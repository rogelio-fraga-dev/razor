package com.razor.api.platform;

import com.razor.api.config.RazorProperties;
import com.razor.api.security.JwtService;
import com.razor.api.util.AppointmentDates;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
 * Acesso ao console da plataforma (V51 + V56).
 *
 * <p><b>Login:</b> e-mail + senha. Com o 2FA ativo, pede o codigo do app (ou um codigo de
 * recuperacao) — a nao ser que o aparelho tenha sido lembrado. Sem 2FA, entra so com a senha
 * durante a <b>carencia</b> ({@code mfa-grace-days}, contada do primeiro acesso); vencida, o login
 * so termina depois de configurar o 2FA.
 *
 * <p><b>Configuracao do 2FA:</b> a chave em configuracao ({@code totp_pending_enc}) e estavel entre
 * tentativas — so muda quando a pessoa pede "gerar novo QR" — e so vira a ativa quando confirmada com
 * um codigo. Ao confirmar saem 10 codigos de recuperacao (mostrados uma vez).
 *
 * <p><b>Sessao:</b> cada login vira uma linha em {@code platform_sessions} (id no token): expira por
 * inatividade ({@code idle-minutes}) e no maximo em {@code session-minutes}; "sair de todos" revoga
 * tudo, inclusive os aparelhos lembrados.
 */
@Slf4j
@Service
public class PlatformAuthService {

    static final Duration CHALLENGE_TTL = Duration.ofMinutes(10);
    static final String ISSUER = "Razor Plataforma";
    /** Atualiza o "ultimo uso" da sessao no maximo uma vez por este intervalo. */
    static final Duration TOUCH_EVERY = Duration.ofSeconds(60);

    private final PlatformUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecretCipher cipher;
    private final JwtService jwtService;
    private final RazorProperties properties;
    private final PlatformSecurityStore store;
    private final Clock clock;

    @Autowired
    public PlatformAuthService(PlatformUserRepository repository, PasswordEncoder passwordEncoder, SecretCipher cipher,
                               JwtService jwtService, RazorProperties properties, PlatformSecurityStore store) {
        this(repository, passwordEncoder, cipher, jwtService, properties, store, Clock.systemUTC());
    }

    PlatformAuthService(PlatformUserRepository repository, PasswordEncoder passwordEncoder, SecretCipher cipher,
                        JwtService jwtService, RazorProperties properties, PlatformSecurityStore store, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.cipher = cipher;
        this.jwtService = jwtService;
        this.properties = properties;
        this.store = store;
        this.clock = clock;
    }

    /** DONE = sessao emitida; MFA = falta o codigo; SETUP_REQUIRED = carencia vencida, configure o 2FA. */
    public enum Step { DONE, MFA, SETUP_REQUIRED }

    /** De onde vem o acesso (para a sessao e o aparelho lembrado). */
    public record Client(String ip, String userAgent, String deviceToken) {
    }

    public record Session(String token, UUID sessionId, UUID userId, String name, String email, String method,
                          LocalDateTime expiresAt, String deviceToken) {
    }

    public record LoginResult(Step step, String challenge, Session session) {
    }

    public record SetupInfo(String secret, String otpauthUri, String qrSvg) {
    }

    /** Usuario da sessao. */
    public record Authenticated(PlatformUser user, UUID sessionId) {
    }

    /** Situacao do 2FA e da sessao para o console. */
    public record Status(boolean mfaEnabled, LocalDateTime mfaDeadline, long mfaDaysLeft, boolean mfaRequiredNow,
                         int recoveryCodesLeft, long idleMinutes) {
    }

    // ------------------------------------------------------------------ primeiro admin

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
        log.info("[PLATAFORMA] Primeiro administrador criado ({}). 2FA com {} dias de carencia.", email, p.getMfaGraceDays());
    }

    // ------------------------------------------------------------------ login

    public LoginResult login(String email, String password, Client client) {
        if (!cipher.enabled()) throw new SecretCipher.ConsoleDisabledException();
        PlatformUser u = repository.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .filter(PlatformUser::isActive)
                .orElse(null);
        // Compara a senha mesmo sem usuario: o tempo de resposta nao revela quem existe.
        String hash = u != null ? u.getPasswordHash() : dummyHash();
        if (!passwordEncoder.matches(password == null ? "" : password, hash) || u == null) {
            throw new BadCredentialsException("credenciais");
        }
        LocalDateTime now = now();
        if (!u.isTotpEnabled() && u.getMfaDeadlineAt() == null) {
            // Primeiro acesso: comeca a contar a carencia.
            u.setMfaDeadlineAt(now.plusDays(properties.getPlatform().getMfaGraceDays()));
            repository.save(u);
        }
        if (u.isTotpEnabled()) {
            if (store.useTrustedDevice(u.getId(), client.deviceToken(), now)) {
                return new LoginResult(Step.DONE, null, issueSession(u, "TRUSTED_DEVICE", client, false));
            }
            return new LoginResult(Step.MFA, challenge(u), null);
        }
        if (!now.isBefore(u.getMfaDeadlineAt())) {
            return new LoginResult(Step.SETUP_REQUIRED, challenge(u), null);
        }
        return new LoginResult(Step.DONE, null, issueSession(u, "PASSWORD", client, false));
    }

    /** Segundo passo: codigo do app ou codigo de recuperacao. */
    public Session verify(String challenge, String code, String recoveryCode, boolean rememberDevice, Client client) {
        if (!cipher.enabled()) throw new SecretCipher.ConsoleDisabledException();
        PlatformUser u = userFromChallenge(challenge);
        if (!u.isTotpEnabled()) throw new BadCredentialsException("sem 2FA");
        LocalDateTime now = now();
        String method;
        if (recoveryCode != null && !recoveryCode.isBlank()) {
            if (!store.useRecoveryCode(u.getId(), recoveryCode, now)) throw new BadCredentialsException("recuperacao");
            log.warn("[PLATAFORMA] {} entrou com um codigo de recuperacao ({} restantes)", u.getEmail(),
                    store.recoveryCodesLeft(u.getId()));
            method = "RECOVERY";
        } else {
            checkTotp(u, u.getTotpSecretEnc(), code);
            method = "TOTP";
        }
        return issueSession(u, method, client, rememberDevice);
    }

    // ------------------------------------------------------------------ configuracao do 2FA

    /** Usuario de um desafio de configuracao obrigatoria (carencia vencida, ainda sem sessao). */
    public PlatformUser userForSetup(String challenge) {
        PlatformUser u = userFromChallenge(challenge);
        if (u.isTotpEnabled()) throw new BadCredentialsException("ja configurado");
        return u;
    }

    /** Chave e QR para o app. A mesma a cada chamada, ate {@code regenerate} ou a confirmacao. */
    public SetupInfo setupStart(PlatformUser u, boolean regenerate) {
        if (u.isTotpEnabled()) throw new IllegalArgumentException("O 2FA já está ativo. Para trocar de celular, desative e configure de novo.");
        String secret;
        if (u.getTotpPendingEnc() == null || regenerate) {
            secret = Totp.newSecret();
            u.setTotpPendingEnc(cipher.encrypt(secret));
            repository.save(u);
        } else {
            secret = cipher.decrypt(u.getTotpPendingEnc());
        }
        String uri = Totp.otpauthUri(ISSUER, u.getEmail(), secret);
        return new SetupInfo(secret, uri, QrSvg.of(uri));
    }

    /** Confirma com um codigo do app: a chave vira a ativa. Devolve os codigos de recuperacao. */
    public List<String> setupConfirm(PlatformUser u, String code) {
        if (u.isTotpEnabled()) throw new IllegalArgumentException("O 2FA já está ativo.");
        if (u.getTotpPendingEnc() == null) throw new IllegalArgumentException("Gere o QR primeiro.");
        checkTotp(u, u.getTotpPendingEnc(), code);
        u.setTotpSecretEnc(u.getTotpPendingEnc());
        u.setTotpPendingEnc(null);
        u.setTotpEnabled(true);
        repository.save(u);
        log.info("[PLATAFORMA] 2FA ativado para {}", u.getEmail());
        return store.regenerateRecoveryCodes(u.getId(), now());
    }

    /** Desliga o 2FA (pedindo um codigo). A carencia recomeca; aparelhos lembrados sao esquecidos. */
    public void disable(PlatformUser u, String code) {
        if (!u.isTotpEnabled()) throw new IllegalArgumentException("O 2FA não está ativo.");
        checkTotp(u, u.getTotpSecretEnc(), code);
        LocalDateTime now = now();
        u.setTotpEnabled(false);
        u.setTotpSecretEnc(null);
        u.setTotpPendingEnc(null);
        u.setLastTotpStep(null);
        u.setMfaDeadlineAt(now.plusDays(properties.getPlatform().getMfaGraceDays()));
        repository.save(u);
        store.deleteRecoveryCodes(u.getId());
        store.revokeAllDevices(u.getId(), now);
        log.warn("[PLATAFORMA] 2FA desativado por {}", u.getEmail());
    }

    public List<String> regenerateRecoveryCodes(PlatformUser u, String code) {
        if (!u.isTotpEnabled()) throw new IllegalArgumentException("Ative o 2FA primeiro.");
        checkTotp(u, u.getTotpSecretEnc(), code);
        return store.regenerateRecoveryCodes(u.getId(), now());
    }

    /** Sai de todos os aparelhos: invalida todas as sessoes e os aparelhos lembrados. */
    public void logoutAll(PlatformUser u) {
        LocalDateTime now = now();
        u.setTokenVersion(u.getTokenVersion() + 1);
        repository.save(u);
        store.revokeAllSessions(u.getId(), now);
        store.revokeAllDevices(u.getId(), now);
    }

    public PlatformUser userOf(UUID id) {
        return repository.findById(id).orElseThrow();
    }

    public void logout(UUID sessionId) {
        if (sessionId != null) store.revokeSession(sessionId, now());
    }

    // ------------------------------------------------------------------ sessao

    public Optional<Authenticated> authenticate(String token) {
        if (token == null || token.isBlank() || !cipher.enabled()) return Optional.empty();
        try {
            JwtService.PlatformClaims c = jwtService.parsePlatform(token, JwtService.TYPE_PLATFORM);
            if (c.sessionId() == null) return Optional.empty(); // token anterior a V56
            Optional<PlatformUser> user = repository.findById(c.userId())
                    .filter(PlatformUser::isActive)
                    .filter(u -> u.getTokenVersion() == c.version());
            if (user.isEmpty()) return Optional.empty();
            var s = store.session(c.sessionId()).orElse(null);
            LocalDateTime now = now();
            if (s == null || s.revokedAt() != null || !s.expiresAt().isAfter(now)
                    || s.lastSeenAt().isBefore(now.minusMinutes(properties.getPlatform().getIdleMinutes()))) {
                return Optional.empty();
            }
            if (s.lastSeenAt().isBefore(now.minus(TOUCH_EVERY))) store.touch(s.id(), now);
            return Optional.of(new Authenticated(user.get(), s.id()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Status status(PlatformUser u) {
        LocalDateTime now = now();
        LocalDateTime deadline = u.getMfaDeadlineAt();
        long daysLeft = u.isTotpEnabled() || deadline == null ? 0
                : Math.max(0, (long) Math.ceil(ChronoUnit.HOURS.between(now, deadline) / 24.0));
        boolean required = !u.isTotpEnabled() && deadline != null && !now.isBefore(deadline);
        return new Status(u.isTotpEnabled(), u.isTotpEnabled() ? null : deadline, daysLeft, required,
                u.isTotpEnabled() ? store.recoveryCodesLeft(u.getId()) : 0, properties.getPlatform().getIdleMinutes());
    }

    /** Sem 2FA ativo nao se faz o que expoe dado de cliente ou a chave da IA (mesmo na carencia). */
    public boolean mfaRequiredNow(PlatformUser u) {
        return status(u).mfaRequiredNow();
    }

    /** Emite a sessao (e o aparelho lembrado, se pedido). Tambem usada apos configurar o 2FA obrigatorio. */
    public Session issueSession(PlatformUser u, String method, Client client, boolean rememberDevice) {
        LocalDateTime now = now();
        long minutes = properties.getPlatform().getSessionMinutes();
        LocalDateTime expires = now.plusMinutes(minutes);
        UUID sid = store.createSession(u.getId(), now, expires, client.ip(), client.userAgent(), method);
        String token = jwtService.issuePlatform(u.getId(), u.getEmail(), u.getTokenVersion(), JwtService.TYPE_PLATFORM,
                Duration.ofMinutes(minutes), sid);
        String device = rememberDevice && u.isTotpEnabled()
                ? store.createTrustedDevice(u.getId(), now, now.plusDays(properties.getPlatform().getTrustedDeviceDays()),
                        client.ip(), client.userAgent())
                : null;
        u.setLastLoginAt(now);
        repository.save(u);
        return new Session(token, sid, u.getId(), u.getName(), u.getEmail(), method, expires, device);
    }

    // ------------------------------------------------------------------ util

    private String challenge(PlatformUser u) {
        return jwtService.issuePlatform(u.getId(), u.getEmail(), u.getTokenVersion(), JwtService.TYPE_MFA, CHALLENGE_TTL);
    }

    private PlatformUser userFromChallenge(String challenge) {
        JwtService.PlatformClaims c;
        try {
            c = jwtService.parsePlatform(challenge, JwtService.TYPE_MFA);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BadCredentialsException("desafio");
        }
        return repository.findById(c.userId())
                .filter(PlatformUser::isActive)
                .filter(x -> x.getTokenVersion() == c.version())
                .orElseThrow(() -> new BadCredentialsException("usuario"));
    }

    /** Confere o codigo contra a chave (cifrada) e bloqueia o reuso. */
    private void checkTotp(PlatformUser u, String secretEnc, String code) {
        if (secretEnc == null) throw new BadCredentialsException("sem chave");
        long step = Totp.verify(cipher.decrypt(secretEnc), code, clock.instant(), u.getLastTotpStep());
        if (step < 0) throw new BadCredentialsException("codigo");
        u.setLastTotpStep(step);
        repository.save(u);
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
