package com.razor.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Freio de forca bruta: bloqueia apos 5 falhas na mesma chave (email + IP). */
class LoginRateLimiterTest {

    private static final String EMAIL = "dono@loja.com";
    private static final String IP = "203.0.113.10";

    @Test
    @DisplayName("bloqueia após 5 tentativas falhas")
    void blocksAfterFiveFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(EMAIL, IP);
            assertThat(limiter.isBlocked(EMAIL, IP)).isFalse();
        }
        limiter.recordFailure(EMAIL, IP); // 5a

        assertThat(limiter.isBlocked(EMAIL, IP)).isTrue();
    }

    @Test
    @DisplayName("login bem-sucedido zera o contador")
    void resetClearsCounter() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 5; i++) limiter.recordFailure(EMAIL, IP);
        assertThat(limiter.isBlocked(EMAIL, IP)).isTrue();

        limiter.reset(EMAIL, IP);

        assertThat(limiter.isBlocked(EMAIL, IP)).isFalse();
    }

    @Test
    @DisplayName("o bloqueio é por email+IP — não afeta outro usuário nem outro IP")
    void blockIsScopedToKey() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 5; i++) limiter.recordFailure(EMAIL, IP);

        assertThat(limiter.isBlocked(EMAIL, IP)).isTrue();
        assertThat(limiter.isBlocked("outro@loja.com", IP)).isFalse();
        assertThat(limiter.isBlocked(EMAIL, "198.51.100.7")).isFalse();
    }

    @Test
    @DisplayName("email é normalizado (maiúsculas/espacos não driblam o bloqueio)")
    void keyIsNormalized() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 5; i++) limiter.recordFailure(EMAIL, IP);

        assertThat(limiter.isBlocked("  DONO@LOJA.COM ", IP)).isTrue();
    }
}
