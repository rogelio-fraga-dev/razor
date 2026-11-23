package com.razor.api.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.razor.api.config.RazorProperties;
import com.razor.api.security.JwtService;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Console da plataforma (V51): 2FA, cifragem, tokens separados e o fluxo de login. */
class PlatformSecurityTest {

    /** RFC 6238, apendice B: segredo ASCII "12345678901234567890". */
    private static final String RFC_SECRET = Totp.base32("12345678901234567890".getBytes());
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    @DisplayName("TOTP bate com os vetores da RFC 6238 (6 digitos)")
    void totp_rfcVectors() {
        assertThat(RFC_SECRET).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1234567890)))).isEqualTo("005924");
    }

    @Test
    @DisplayName("TOTP: aceita o passo vizinho, recusa codigo errado, formato invalido e reuso")
    void totp_verify() {
        Instant now = Instant.ofEpochSecond(1234567890);
        long step = Totp.step(now);
        String atual = Totp.code(RFC_SECRET, step);
        assertThat(Totp.verify(RFC_SECRET, atual, now, null)).isEqualTo(step);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, null)).isEqualTo(step - 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 3), now, null)).isEqualTo(-1);
        assertThat(Totp.verify(RFC_SECRET, "12345", now, null)).isEqualTo(-1);
        assertThat(Totp.verify(RFC_SECRET, "abc123", now, null)).isEqualTo(-1);
        assertThat(Totp.verify(RFC_SECRET, atual, now, step)).as("mesmo codigo duas vezes").isEqualTo(-1);
        assertThat(Totp.unbase32(Totp.base32(new byte[] {1, 2, 3, 4, 5}))).containsExactly(1, 2, 3, 4, 5);
        assertThat(Totp.otpauthUri("Razor Plataforma", "eu@x.com", "ABC")).startsWith("otpauth://totp/Razor%20Plataforma%3Aeu%40x.com?secret=ABC");
    }

    @Test
    @DisplayName("cifra: ida e volta, IV diferente a cada vez, adulteracao e chave errada falham; sem chave = desligado")
    void cipher() {
        SecretCipher c = new SecretCipher(KEY);
        assertThat(c.enabled()).isTrue();
        String a = c.encrypt("sk-segredo");
        assertThat(a).isNotEqualTo(c.encrypt("sk-segredo")).doesNotContain("segredo");
        assertThat(c.decrypt(a)).isEqualTo("sk-segredo");

        byte[] raw = Base64.getDecoder().decode(a);
        raw[raw.length - 1] ^= 1;
        assertThatThrownBy(() -> c.decrypt(Base64.getEncoder().encodeToString(raw))).isInstanceOf(IllegalStateException.class);
        byte[] outra = new byte[32];
        outra[0] = 9;
        assertThatThrownBy(() -> new SecretCipher(Base64.getEncoder().encodeToString(outra)).decrypt(a))
                .isInstanceOf(IllegalStateException.class);

        assertThat(new SecretCipher("").enabled()).isFalse();
        assertThat(new SecretCipher("curta").enabled()).isFalse();
        assertThatThrownBy(() -> new SecretCipher("").encrypt("x")).isInstanceOf(SecretCipher.ConsoleDisabledException.class);
    }

    @Test
    @DisplayName("token do console nao vale no painel das barbearias, e o desafio do 2FA nao vale como sessao")
    void tokensAreSeparate() {
        JwtService jwt = jwt();
        UUID id = UUID.randomUUID();
        String sessao = jwt.issuePlatform(id, "op@razor", 0, JwtService.TYPE_PLATFORM, java.time.Duration.ofMinutes(5));
        String desafio = jwt.issuePlatform(id, "op@razor", 0, JwtService.TYPE_MFA, java.time.Duration.ofMinutes(5));
        assertThatThrownBy(() -> jwt.parseToken(sessao)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwt.parseToken(desafio)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwt.parsePlatform(desafio, JwtService.TYPE_PLATFORM)).isInstanceOf(JwtException.class);
        assertThat(jwt.parsePlatform(sessao, JwtService.TYPE_PLATFORM).userId()).isEqualTo(id);
    }

    @Test
    @DisplayName("CSRF: escrita do console exige token, menos os dois passos do login")
    void csrfOnPlatform() {
        assertThat(com.razor.api.security.SecurityConfigAccess.csrfRequired(new MockHttpServletRequest("POST", "/api/platform/revenue"))).isTrue();
        assertThat(com.razor.api.security.SecurityConfigAccess.csrfRequired(new MockHttpServletRequest("POST", "/api/platform/auth/login"))).isFalse();
        assertThat(com.razor.api.security.SecurityConfigAccess.csrfRequired(new MockHttpServletRequest("POST", "/api/platform/auth/verify"))).isFalse();
        assertThat(com.razor.api.security.SecurityConfigAccess.csrfRequired(new MockHttpServletRequest("POST", "/api/platform/auth/logout"))).isTrue();
        assertThat(com.razor.api.security.SecurityConfigAccess.csrfRequired(new MockHttpServletRequest("GET", "/api/platform/overview"))).isFalse();
    }

    private static JwtService jwt() {
        RazorProperties props = new RazorProperties();
        props.getSecurity().setJwtSecret("um-segredo-de-teste-bem-longo-0123456789");
        return new JwtService(props);
    }

    // ------------------------------------------------------------------ fluxo de login

    @Nested
    class LoginFlow {
        private final Map<UUID, PlatformUser> db = new HashMap<>();
        private final PlatformUserRepository repo = mock(PlatformUserRepository.class);
        private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        private final RazorProperties props = new RazorProperties();
        private final JwtService jwt = jwt();
        private final Instant now = Instant.parse("2026-11-23T15:00:00Z");
        private PlatformAuthService auth;
        private PlatformUser user;

        @BeforeEach
        void setUp() {
            user = PlatformUser.builder().id(UUID.randomUUID()).email("op@razor.dev").name("Op")
                    .passwordHash(encoder.encode("senha-forte-123")).createdAt(java.time.LocalDateTime.now()).build();
            db.put(user.getId(), user);
            when(repo.findByEmailIgnoreCase(any())).thenAnswer(i -> db.values().stream()
                    .filter(u -> u.getEmail().equalsIgnoreCase(i.getArgument(0))).findFirst());
            when(repo.findById(any())).thenAnswer(i -> Optional.ofNullable(db.get(i.<UUID>getArgument(0))));
            when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
            auth = new PlatformAuthService(repo, encoder, new SecretCipher(KEY), jwt, props, Clock.fixed(now, ZoneOffset.UTC));
        }

        @Test
        @DisplayName("primeiro acesso: senha -> SETUP com segredo; codigo certo liga o 2FA e emite a sessao")
        void firstLogin_setupThenSession() {
            var step = auth.login("OP@razor.dev", "senha-forte-123");
            assertThat(step.step()).isEqualTo(PlatformAuthService.Step.SETUP);
            assertThat(step.secret()).isNotBlank();
            assertThat(user.getTotpSecretEnc()).doesNotContain(step.secret());
            assertThat(auth.authenticate("qualquer")).isEmpty();

            var s = auth.verify(step.challenge(), Totp.code(step.secret(), Totp.step(now)));
            assertThat(user.isTotpEnabled()).isTrue();
            assertThat(auth.authenticate(s.token())).contains(user);

            // Depois de ligado: o login pede so o codigo, sem devolver segredo.
            var again = auth.login("op@razor.dev", "senha-forte-123");
            assertThat(again.step()).isEqualTo(PlatformAuthService.Step.MFA);
            assertThat(again.secret()).isNull();
        }

        @Test
        @DisplayName("senha errada, usuario inexistente, codigo errado, reuso e desafio usado como sessao: recusados")
        void rejections() {
            assertThatThrownBy(() -> auth.login("op@razor.dev", "errada")).isInstanceOf(BadCredentialsException.class);
            assertThatThrownBy(() -> auth.login("ninguem@x", "senha-forte-123")).isInstanceOf(BadCredentialsException.class);

            var step = auth.login("op@razor.dev", "senha-forte-123");
            assertThatThrownBy(() -> auth.verify(step.challenge(), "000000")).isInstanceOf(BadCredentialsException.class);
            assertThat(auth.authenticate(step.challenge())).as("desafio nao e sessao").isEmpty();

            String code = Totp.code(step.secret(), Totp.step(now));
            auth.verify(step.challenge(), code);
            var step2 = auth.login("op@razor.dev", "senha-forte-123");
            assertThatThrownBy(() -> auth.verify(step2.challenge(), code)).as("reuso").isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("usuario desativado ou com versao trocada perde a sessao")
        void revoked() {
            var step = auth.login("op@razor.dev", "senha-forte-123");
            var s = auth.verify(step.challenge(), Totp.code(step.secret(), Totp.step(now)));
            user.setTokenVersion(1);
            assertThat(auth.authenticate(s.token())).isEmpty();
            user.setTokenVersion(0);
            user.setActive(false);
            assertThat(auth.authenticate(s.token())).isEmpty();
        }

        @Test
        @DisplayName("sem RAZOR_SECRETS_KEY o console fica desligado")
        void disabledWithoutKey() {
            var off = new PlatformAuthService(repo, encoder, new SecretCipher(""), jwt, props, Clock.fixed(now, ZoneOffset.UTC));
            assertThatThrownBy(() -> off.login("op@razor.dev", "senha-forte-123"))
                    .isInstanceOf(SecretCipher.ConsoleDisabledException.class);
        }

        @Test
        @DisplayName("primeiro admin vem do ambiente so se nao houver nenhum e com senha de 12+")
        void bootstrap() {
            db.clear();
            when(repo.count()).thenAnswer(i -> (long) db.size());
            when(repo.save(any())).thenAnswer(i -> {
                PlatformUser u = i.getArgument(0);
                db.put(u.getId(), u);
                return u;
            });
            props.getPlatform().setAdminEmail("Admin@Razor.dev");
            props.getPlatform().setAdminPassword("curta");
            auth.bootstrap();
            assertThat(db).isEmpty();
            props.getPlatform().setAdminPassword("uma-senha-bem-longa");
            auth.bootstrap();
            assertThat(db.values()).singleElement().satisfies(u -> {
                assertThat(u.getEmail()).isEqualTo("admin@razor.dev");
                assertThat(u.isTotpEnabled()).isFalse();
            });
            auth.bootstrap();
            assertThat(db).hasSize(1);
        }
    }
}
