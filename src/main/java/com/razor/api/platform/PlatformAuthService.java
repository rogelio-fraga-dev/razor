package com.razor.api.platform;

import com.razor.api.config.RazorProperties;
import com.razor.api.security.JwtService;
import com.razor.api.util.AppointmentDates;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Login do console da plataforma (V51), em dois passos e com 2FA obrigatorio:
 *
 * <ol>
 *   <li>{@link #login}: e-mail + senha. Certo, devolve um desafio curto (JWT {@code typ=mfa},
 *       5 min). Quem ainda nao configurou o app autenticador recebe junto o segredo novo
 *       ({@code SETUP}); quem ja configurou so precisa do codigo ({@code MFA}).</li>
 *   <li>{@link #verify}: desafio + codigo de 6 digitos. Confere o TOTP (sem reuso do mesmo
 *       codigo), liga o 2FA no primeiro uso e emite a sessao ({@code typ=platform}).</li>
 * </ol>
 *
 * <p>Sem {@code RAZOR_SECRETS_KEY} nada disso funciona ({@link SecretCipher.ConsoleDisabledException}).
 */
@Slf4j
@Service
public class PlatformAuthService {

    static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    static final String ISSUER = "Razor Plataforma";

    private final PlatformUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecretCipher cipher;
    private final JwtService jwtService;
    private final RazorProperties properties;
    private final Clock clock;

    @Autowired
    public PlatformAuthService(PlatformUserRepository repository, PasswordEncoder passwordEncoder, SecretCipher cipher,
                               JwtService jwtService, RazorProperties properties) {
        this(repository, passwordEncoder, cipher, jwtService, properties, Clock.systemUTC());
    }

    PlatformAuthService(PlatformUserRepository repository, PasswordEncoder passwordEncoder, SecretCipher cipher,
                        JwtService jwtService, RazorProperties properties, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.cipher = cipher;
        this.jwtService = jwtService;
        this.properties = properties;
        this.clock = clock;
    }

    public enum Step { MFA, SETUP }

    /** Resposta do primeiro passo. {@code secret}/{@code otpauthUri} so no SETUP. */
    public record LoginStep(Step step, String challenge, String secret, String otpauthUri) {
    }

    /** Sessao emitida no segundo passo. */
    public record Session(String token, UUID userId, String name, String email) {
    }

    // ------------------------------------------------------------------ primeiro admin

    /** Cria o primeiro administrador a partir do ambiente, se ainda nao houver nenhum. */
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        var p = properties.getPlatform();
        if (p.getAdminEmail() == null || p.getAdminEmail().isBlank()) return;
        if (repository.count() > 0) return;
        if (p.getAdminPassword() == null || p.getAdminPassword().length() < 12) {
            log.error("[PLATAFORMA] RAZOR_PLATFORM_ADMIN_PASSWORD precisa ter 12+ caracteres — admin nao criado");
            return;
        }
        String email = p.getAdminEmail().trim().toLowerCase(Locale.ROOT);
        repository.save(PlatformUser.builder()
                .id(UUID.randomUUID())
                .email(email)
                .name("Administrador")
                .passwordHash(passwordEncoder.encode(p.getAdminPassword()))
                .createdAt(now())
                .build());
        log.info("[PLATAFORMA] Primeiro administrador criado ({}). O 2FA e configurado no primeiro login.", email);
    }

    // ------------------------------------------------------------------ login

    public LoginStep login(String email, String password) {
        if (!cipher.enabled()) throw new SecretCipher.ConsoleDisabledException();
        PlatformUser u = repository.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .filter(PlatformUser::isActive)
                .orElse(null);
        // Compara a senha mesmo sem usuario (hash fixo): o tempo de resposta nao revela quem existe.
        String hash = u != null ? u.getPasswordHash() : dummyHash();
        if (!passwordEncoder.matches(password == null ? "" : password, hash) || u == null) {
            throw new BadCredentialsException("credenciais");
        }
        String challenge = jwtService.issuePlatform(u.getId(), u.getEmail(), u.getTokenVersion(), JwtService.TYPE_MFA,
                CHALLENGE_TTL);
        if (u.isTotpEnabled()) {
            return new LoginStep(Step.MFA, challenge, null, null);
        }
        // Primeiro acesso: segredo novo a cada tentativa ate confirmar (um segredo vazado de uma
        // tentativa abandonada nao serve depois).
        String secret = Totp.newSecret();
        u.setTotpSecretEnc(cipher.encrypt(secret));
        repository.save(u);
        return new LoginStep(Step.SETUP, challenge, secret, Totp.otpauthUri(ISSUER, u.getEmail(), secret));
    }

    public Session verify(String challenge, String code) {
        if (!cipher.enabled()) throw new SecretCipher.ConsoleDisabledException();
        JwtService.PlatformClaims c;
        try {
            c = jwtService.parsePlatform(challenge, JwtService.TYPE_MFA);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BadCredentialsException("desafio");
        }
        PlatformUser u = repository.findById(c.userId())
                .filter(PlatformUser::isActive)
                .filter(x -> x.getTokenVersion() == c.version())
                .orElseThrow(() -> new BadCredentialsException("usuario"));
        if (u.getTotpSecretEnc() == null) throw new BadCredentialsException("sem segredo");
        long step = Totp.verify(cipher.decrypt(u.getTotpSecretEnc()), code, clock.instant(), u.getLastTotpStep());
        if (step < 0) throw new BadCredentialsException("codigo");

        if (!u.isTotpEnabled()) log.info("[PLATAFORMA] 2FA ativado para {}", u.getEmail());
        u.setTotpEnabled(true);
        u.setLastTotpStep(step);
        u.setLastLoginAt(now());
        repository.save(u);
        String token = jwtService.issuePlatform(u.getId(), u.getEmail(), u.getTokenVersion(), JwtService.TYPE_PLATFORM,
                Duration.ofMinutes(properties.getPlatform().getSessionMinutes()));
        return new Session(token, u.getId(), u.getName(), u.getEmail());
    }

    // ------------------------------------------------------------------ sessao

    /** Usuario da sessao, se o token for de plataforma, valido e nao revogado. */
    public Optional<PlatformUser> authenticate(String token) {
        if (token == null || token.isBlank() || !cipher.enabled()) return Optional.empty();
        try {
            JwtService.PlatformClaims c = jwtService.parsePlatform(token, JwtService.TYPE_PLATFORM);
            return repository.findById(c.userId())
                    .filter(PlatformUser::isActive)
                    .filter(PlatformUser::isTotpEnabled)
                    .filter(u -> u.getTokenVersion() == c.version());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private volatile String dummyHash;

    private String dummyHash() {
        if (dummyHash == null) dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
        return dummyHash;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(AppointmentDates.ZONE));
    }
}
