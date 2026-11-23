package com.razor.api.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.razor.api.config.RazorProperties;
import com.razor.api.security.JwtService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Acesso ao console (V56): carencia do 2FA, chave estavel, recuperacao, aparelho lembrado e sessao. */
class PlatformLoginFlowTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String PASSWORD = "senha-forte-123";

    private final Map<UUID, PlatformUser> db = new HashMap<>();
    private final PlatformUserRepository repo = mock(PlatformUserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final RazorProperties props = new RazorProperties();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-23T15:00:00Z"));
    private final FakeStore store = new FakeStore(clock);
    private final PlatformAuthService.Client client = new PlatformAuthService.Client("1.2.3.4", "Mozilla/5.0 (iPhone) Safari/1", null);
    private PlatformAuthService auth;
    private PlatformUser user;

    @BeforeEach
    void setUp() {
        props.getSecurity().setJwtSecret("um-segredo-de-teste-bem-longo-0123456789");
        user = PlatformUser.builder().id(UUID.randomUUID()).email("op@razor.dev").name("Op")
                .passwordHash(encoder.encode(PASSWORD)).createdAt(LocalDateTime.now()).build();
        db.put(user.getId(), user);
        when(repo.findByEmailIgnoreCase(any())).thenAnswer(i -> db.values().stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(i.getArgument(0))).findFirst());
        when(repo.findById(any())).thenAnswer(i -> Optional.ofNullable(db.get(i.<UUID>getArgument(0))));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        auth = new PlatformAuthService(repo, encoder, new SecretCipher(KEY), new JwtService(props), props, store, clock);
    }

    private String code(String secret) {
        return Totp.code(secret, Totp.step(clock.instant()));
    }

    /** Ativa o 2FA pela tela Seguranca; devolve a chave. */
    private String enableMfa() {
        var info = auth.setupStart(user, false);
        auth.setupConfirm(user, code(info.secret()));
        clock.advance(Duration.ofSeconds(31)); // proximo codigo
        return info.secret();
    }

    @Test
    @DisplayName("primeiro acesso entra so com a senha e comeca a carencia de 7 dias")
    void firstLogin_passwordOnly_startsGrace() {
        var r = auth.login("OP@razor.dev", PASSWORD, client);
        assertThat(r.step()).isEqualTo(PlatformAuthService.Step.DONE);
        assertThat(user.getMfaDeadlineAt()).isEqualTo(LocalDateTime.of(2026, 11, 30, 12, 0));
        var status = auth.status(user);
        assertThat(status.mfaEnabled()).isFalse();
        assertThat(status.mfaDaysLeft()).isEqualTo(7);
        assertThat(status.mfaRequiredNow()).isFalse();
        assertThat(auth.authenticate(r.session().token())).isPresent();
        assertThat(store.sessions.get(r.session().sessionId()).method()).isEqualTo("PASSWORD");
    }

    @Test
    @DisplayName("carencia vencida: login pede configurar; QR estavel entre tentativas; 'gerar novo' troca; confirmar libera")
    void graceExpired_setupRequired() {
        auth.login("op@razor.dev", PASSWORD, client);
        clock.advance(Duration.ofDays(7));
        var r = auth.login("op@razor.dev", PASSWORD, client);
        assertThat(r.step()).isEqualTo(PlatformAuthService.Step.SETUP_REQUIRED);
        assertThat(auth.mfaRequiredNow(user)).isTrue();

        PlatformUser u = auth.userForSetup(r.challenge());
        var a = auth.setupStart(u, false);
        var b = auth.setupStart(u, false);
        assertThat(b.secret()).as("mesma chave em todas as tentativas").isEqualTo(a.secret());
        assertThat(a.qrSvg()).startsWith("<svg").contains("<path");
        assertThat(a.otpauthUri()).contains("secret=" + a.secret());
        var c = auth.setupStart(u, true);
        assertThat(c.secret()).isNotEqualTo(a.secret());

        assertThatThrownBy(() -> auth.setupConfirm(u, code(a.secret()))).as("a chave antiga nao vale mais")
                .isInstanceOf(BadCredentialsException.class);
        List<String> codes = auth.setupConfirm(u, code(c.secret()));
        assertThat(codes).hasSize(10).doesNotHaveDuplicates();
        assertThat(user.isTotpEnabled()).isTrue();
        assertThat(user.getTotpPendingEnc()).isNull();
        assertThat(auth.mfaRequiredNow(user)).isFalse();
    }

    @Test
    @DisplayName("com 2FA: codigo do app, sem reuso; codigo de recuperacao vale uma vez so")
    void mfa_codesAndRecovery() {
        auth.login("op@razor.dev", PASSWORD, client);
        String secret = enableMfa();
        List<String> recovery = auth.regenerateRecoveryCodes(user, code(secret));
        clock.advance(Duration.ofSeconds(31));

        var r = auth.login("op@razor.dev", PASSWORD, client);
        assertThat(r.step()).isEqualTo(PlatformAuthService.Step.MFA);
        String c = code(secret);
        var s = auth.verify(r.challenge(), "  " + c.substring(0, 3) + " " + c.substring(3), null, false, client);
        assertThat(s.method()).isEqualTo("TOTP");
        var r2 = auth.login("op@razor.dev", PASSWORD, client);
        assertThatThrownBy(() -> auth.verify(r2.challenge(), c, null, false, client)).as("reuso")
                .isInstanceOf(BadCredentialsException.class);

        String rc = recovery.get(0).toUpperCase().replace("-", " ");
        assertThat(auth.verify(r2.challenge(), null, rc, false, client).method()).isEqualTo("RECOVERY");
        assertThatThrownBy(() -> auth.verify(r2.challenge(), null, rc, false, client)).isInstanceOf(BadCredentialsException.class);
        assertThat(auth.status(user).recoveryCodesLeft()).isEqualTo(9);
    }

    @Test
    @DisplayName("lembrar o aparelho: o proximo login no mesmo navegador nao pede codigo; sair de todos esquece")
    void trustedDevice_andLogoutAll() {
        auth.login("op@razor.dev", PASSWORD, client);
        String secret = enableMfa();
        var r = auth.login("op@razor.dev", PASSWORD, client);
        var s = auth.verify(r.challenge(), code(secret), null, true, client);
        assertThat(s.deviceToken()).isNotBlank();

        var withDevice = new PlatformAuthService.Client("1.2.3.4", "x", s.deviceToken());
        var again = auth.login("op@razor.dev", PASSWORD, withDevice);
        assertThat(again.step()).isEqualTo(PlatformAuthService.Step.DONE);
        assertThat(again.session().method()).isEqualTo("TRUSTED_DEVICE");
        assertThat(auth.login("op@razor.dev", PASSWORD, client).step()).as("outro navegador pede codigo")
                .isEqualTo(PlatformAuthService.Step.MFA);

        auth.logoutAll(user);
        assertThat(auth.authenticate(s.token())).isEmpty();
        assertThat(auth.authenticate(again.session().token())).isEmpty();
        assertThat(auth.login("op@razor.dev", PASSWORD, withDevice).step()).isEqualTo(PlatformAuthService.Step.MFA);
    }

    @Test
    @DisplayName("sessao expira por inatividade e o uso renova; sair encerra so a sessao atual")
    void idleTimeout() {
        var s1 = auth.login("op@razor.dev", PASSWORD, client).session();
        clock.advance(Duration.ofMinutes(20));
        assertThat(auth.authenticate(s1.token())).as("usado aos 20 min").isPresent();
        clock.advance(Duration.ofMinutes(20));
        assertThat(auth.authenticate(s1.token())).as("20 min depois do ultimo uso").isPresent();
        clock.advance(Duration.ofMinutes(31));
        assertThat(auth.authenticate(s1.token())).as("31 min parado").isEmpty();

        var s2 = auth.login("op@razor.dev", PASSWORD, client).session();
        var s3 = auth.login("op@razor.dev", PASSWORD, client).session();
        auth.logout(s2.sessionId());
        assertThat(auth.authenticate(s2.token())).isEmpty();
        assertThat(auth.authenticate(s3.token())).isPresent();
    }

    @Test
    @DisplayName("desativar pede codigo, apaga a chave e os codigos e recomeca a carencia")
    void disable() {
        auth.login("op@razor.dev", PASSWORD, client);
        String secret = enableMfa();
        assertThatThrownBy(() -> auth.disable(user, "000000")).isInstanceOf(BadCredentialsException.class);
        auth.disable(user, code(secret));
        assertThat(user.isTotpEnabled()).isFalse();
        assertThat(user.getTotpSecretEnc()).isNull();
        assertThat(auth.status(user).mfaDaysLeft()).isEqualTo(7);
        assertThat(store.recovery).isEmpty();
    }

    @Test
    @DisplayName("filtro: sem 2FA depois do prazo, so login, /me e a configuracao passam")
    void allowedWithoutMfa() {
        assertThat(PlatformSecurity.Filter.allowedWithoutMfa("/api/platform/auth/logout")).isTrue();
        assertThat(PlatformSecurity.Filter.allowedWithoutMfa("/api/platform/me")).isTrue();
        assertThat(PlatformSecurity.Filter.allowedWithoutMfa("/api/platform/security/2fa/setup/start")).isTrue();
        assertThat(PlatformSecurity.Filter.allowedWithoutMfa("/api/platform/overview")).isFalse();
        assertThat(PlatformSecurity.Filter.allowedWithoutMfa("/api/platform/security/logout-all")).isFalse();
    }

    @Test
    @DisplayName("nome do aparelho legivel a partir do navegador")
    void deviceName() {
        assertThat(PlatformSecurityController.device("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) AppleWebKit Safari/604.1"))
                .isEqualTo("Safari no iPhone");
        assertThat(PlatformSecurityController.device("Mozilla/5.0 (Windows NT 10.0) Chrome/120 Safari/537"))
                .isEqualTo("Chrome no Windows");
        assertThat(PlatformSecurityController.device(null)).isEqualTo("aparelho desconhecido");
    }

    @Test
    @DisplayName("codigo de recuperacao: maiusculas, espacos e hifens nao importam")
    void normalizeRecovery() {
        assertThat(PlatformSecurityStore.normalizeCode("AB2C-d3ef 45gh")).isEqualTo("ab2cd3ef45gh");
    }

    // ------------------------------------------------------------------ apoio

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            MutableClock self = this;
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return zone;
                }

                @Override
                public Clock withZone(ZoneId z) {
                    return self.withZone(z);
                }

                @Override
                public Instant instant() {
                    return self.now;
                }
            };
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** O mesmo contrato do PlatformSecurityStore, em memoria. */
    static final class FakeStore extends PlatformSecurityStore {
        final Map<UUID, SessionRow> sessions = new HashMap<>();
        final Map<String, UUID> devices = new HashMap<>();
        final Map<String, LocalDateTime> deviceExpiry = new HashMap<>();
        final List<String> recovery = new ArrayList<>();
        final Set<String> usedRecovery = new HashSet<>();

        FakeStore(Clock clock) {
            super(null);
        }

        @Override
        public UUID createSession(UUID userId, LocalDateTime now, LocalDateTime expiresAt, String ip, String ua, String method) {
            UUID id = UUID.randomUUID();
            sessions.put(id, new SessionRow(id, now, now, expiresAt, ip, ua, method, null));
            return id;
        }

        @Override
        public Optional<SessionRow> session(UUID id) {
            return Optional.ofNullable(sessions.get(id));
        }

        @Override
        public void touch(UUID id, LocalDateTime now) {
            var s = sessions.get(id);
            sessions.put(id, new SessionRow(s.id(), s.createdAt(), now, s.expiresAt(), s.ip(), s.userAgent(), s.method(), s.revokedAt()));
        }

        @Override
        public void revokeSession(UUID id, LocalDateTime now) {
            var s = sessions.get(id);
            if (s != null) sessions.put(id, new SessionRow(s.id(), s.createdAt(), s.lastSeenAt(), s.expiresAt(), s.ip(), s.userAgent(), s.method(), now));
        }

        @Override
        public void revokeAllSessions(UUID userId, LocalDateTime now) {
            new ArrayList<>(sessions.keySet()).forEach(id -> revokeSession(id, now));
        }

        @Override
        public String createTrustedDevice(UUID userId, LocalDateTime now, LocalDateTime exp, String ip, String ua) {
            String t = UUID.randomUUID().toString();
            devices.put(t, userId);
            deviceExpiry.put(t, exp);
            return t;
        }

        @Override
        public boolean useTrustedDevice(UUID userId, String token, LocalDateTime now) {
            return token != null && userId.equals(devices.get(token)) && deviceExpiry.get(token).isAfter(now);
        }

        @Override
        public void revokeAllDevices(UUID userId, LocalDateTime now) {
            devices.values().removeIf(userId::equals);
        }

        @Override
        public List<String> regenerateRecoveryCodes(UUID userId, LocalDateTime now) {
            recovery.clear();
            usedRecovery.clear();
            for (int i = 0; i < RECOVERY_CODES; i++) recovery.add("abcd-efgh-" + String.format("%04d", i));
            return new ArrayList<>(recovery);
        }

        @Override
        public boolean useRecoveryCode(UUID userId, String code, LocalDateTime now) {
            String n = normalizeCode(code);
            for (String r : recovery) {
                if (normalizeCode(r).equals(n) && usedRecovery.add(n)) return true;
            }
            return false;
        }

        @Override
        public int recoveryCodesLeft(UUID userId) {
            return recovery.size() - usedRecovery.size();
        }

        @Override
        public void deleteRecoveryCodes(UUID userId) {
            recovery.clear();
            usedRecovery.clear();
        }
    }
}
