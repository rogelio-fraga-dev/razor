package com.razor.api.security;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Emite e valida os JWT do painel web. O token carrega a identidade do usuario (subject = email)
 * e as claims {@code uid}, {@code shop} (tenant) e {@code role}, base do {@link TenantUserPrincipal}.
 *
 * <p>Segredo: usa {@code razor.security.jwt-secret} quando definido (&gt;= 32 chars); caso
 * contrario gera uma chave aleatoria no boot (tokens invalidam ao reiniciar) — adequado para dev,
 * mas em producao deve-se definir {@code RAZOR_JWT_SECRET}.
 */
@Slf4j
@Service
public class JwtService {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_SHOP = "shop";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_VERSION = "ver";
    /** Tokens do console da plataforma (V51): "platform" (sessao) ou "mfa" (desafio do 2FA). */
    private static final String CLAIM_TYPE = "typ";
    public static final String TYPE_PLATFORM = "platform";
    public static final String TYPE_MFA = "mfa";
    private static final String CLAIM_SESSION = "sid";
    private static final int MIN_SECRET_LENGTH = 32;

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(RazorProperties properties) {
        String secret = properties.getSecurity().getJwtSecret();
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            byte[] random = new byte[MIN_SECRET_LENGTH];
            new SecureRandom().nextBytes(random);
            this.key = Keys.hmacShaKeyFor(random);
            log.warn("[SECURITY] razor.security.jwt-secret ausente ou curto; usando chave aleatoria "
                    + "(tokens invalidam ao reiniciar). Defina RAZOR_JWT_SECRET em producao.");
        } else {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        this.expirationMinutes = properties.getSecurity().getJwtExpirationMinutes();
    }

    /** Emite um token para o usuario autenticado. */
    public String issue(TenantUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_USER_ID, user.getId().toString())
                .claim(CLAIM_SHOP, user.getBarbershopId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_VERSION, user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationMinutes * 60)))
                .signWith(key)
                .compact();
    }

    /**
     * Valida a assinatura/expiracao e devolve o principal. Lanca {@link JwtException} se o token
     * for invalido, adulterado ou expirado.
     */
    public TenantUserPrincipal parse(String token) {
        return parseToken(token).principal();
    }

    /** Principal + versao do token (claim {@code ver}; ausente = 0, tokens anteriores a ela). */
    public record ParsedToken(TenantUserPrincipal principal, int version) {
    }

    /** Como {@link #parse}, devolvendo tambem a versao para a revalidacao da sessao. */
    public ParsedToken parseToken(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        // Token do console da plataforma (ou desafio de 2FA) nunca vale no painel das barbearias.
        if (claims.get(CLAIM_TYPE) != null) {
            throw new JwtException("token de outro tipo");
        }
        TenantUserPrincipal principal = new TenantUserPrincipal(
                UUID.fromString(claims.get(CLAIM_USER_ID, String.class)),
                claims.getSubject(),
                UUID.fromString(claims.get(CLAIM_SHOP, String.class)),
                UserRole.valueOf(claims.get(CLAIM_ROLE, String.class)));
        Integer version = claims.get(CLAIM_VERSION, Integer.class);
        return new ParsedToken(principal, version == null ? 0 : version);
    }

    // --- Console da plataforma (V51) ---------------------------------------------------------

    /** Claims de um token da plataforma. */
    public record PlatformClaims(UUID userId, String email, int version, String type, UUID sessionId) {
    }

    /** Sessao do console ({@code typ=platform}) ou desafio do 2FA ({@code typ=mfa}). */
    public String issuePlatform(UUID userId, String email, int version, String type, java.time.Duration ttl) {
        return issuePlatform(userId, email, version, type, ttl, null);
    }

    /** Com o id da sessao ({@code sid}, V56): permite expirar por inatividade e encerrar uma sessao. */
    public String issuePlatform(UUID userId, String email, int version, String type, java.time.Duration ttl,
                                UUID sessionId) {
        Instant now = Instant.now();
        var b = Jwts.builder();
        if (sessionId != null) b.claim(CLAIM_SESSION, sessionId.toString());
        return b
                .subject(email)
                .claim(CLAIM_USER_ID, userId.toString())
                .claim(CLAIM_VERSION, version)
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** Valida e exige o tipo pedido. Lanca {@link JwtException} se nao bater. */
    public PlatformClaims parsePlatform(String token, String expectedType) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("tipo de token inesperado");
        }
        Integer version = claims.get(CLAIM_VERSION, Integer.class);
        String sid = claims.get(CLAIM_SESSION, String.class);
        return new PlatformClaims(UUID.fromString(claims.get(CLAIM_USER_ID, String.class)), claims.getSubject(),
                version == null ? 0 : version, expectedType, sid == null ? null : UUID.fromString(sid));
    }
}
