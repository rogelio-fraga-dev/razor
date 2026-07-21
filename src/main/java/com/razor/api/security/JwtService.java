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
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new TenantUserPrincipal(
                UUID.fromString(claims.get(CLAIM_USER_ID, String.class)),
                claims.getSubject(),
                UUID.fromString(claims.get(CLAIM_SHOP, String.class)),
                UserRole.valueOf(claims.get(CLAIM_ROLE, String.class)));
    }
}
