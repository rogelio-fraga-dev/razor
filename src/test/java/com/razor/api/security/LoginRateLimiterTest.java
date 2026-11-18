package com.razor.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Freio de forca bruta: 5 falhas por email+IP, 20 por email de qualquer IP, janelas varridas. */
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

    @Test
    @DisplayName("trocar de IP não dribla: 20 falhas no mesmo email, de IPs variados, travam a conta")
    void perEmailLimit_acrossIps() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS_PER_EMAIL - 1; i++) {
            limiter.recordFailure(EMAIL, "203.0.113." + i);
        }
        assertThat(limiter.isBlocked(EMAIL, "198.51.100.1")).isFalse();

        limiter.recordFailure(EMAIL, "198.51.100.200");

        // IP nunca visto, mesmo email: bloqueado. Outro email segue livre.
        assertThat(limiter.isBlocked(EMAIL, "198.51.100.1")).isTrue();
        assertThat(limiter.isBlocked("outro@loja.com", "198.51.100.1")).isFalse();
    }

    @Test
    @DisplayName("janelas vencidas são varridas — o mapa não cresce para sempre")
    void evictExpired_removesStaleEntries() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-17T12:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        for (int i = 0; i < 50; i++) limiter.recordFailure("bot" + i + "@x.com", IP);
        assertThat(limiter.trackedKeys()).isEqualTo(100); // par + email de cada

        clock.advance(Duration.ofMinutes(16));
        limiter.recordFailure(EMAIL, IP); // entrada nova, ainda viva
        limiter.evictExpired();

        assertThat(limiter.trackedKeys()).isEqualTo(2);
        assertThat(limiter.isBlocked("bot1@x.com", IP)).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
