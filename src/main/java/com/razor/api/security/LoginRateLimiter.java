package com.razor.api.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Freio de forca bruta no login do painel: apos {@value #MAX_ATTEMPTS} tentativas falhas
 * para a mesma chave (email + IP), bloqueia por {@value #LOCK_MINUTES} minutos.
 *
 * <p>Em memoria de proposito — resolve o caso real (uma instancia) sem introduzir dependencia.
 * Ao escalar para varias instancias, trocar por Redis junto com o estado da conversa (Fase 6).
 */
@Slf4j
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;
    private static final Duration WINDOW = Duration.ofMinutes(LOCK_MINUTES);

    private record Attempts(AtomicInteger count, Instant firstAt) {
    }

    private final Map<String, Attempts> failures = new ConcurrentHashMap<>();

    private String key(String email, String ip) {
        return (email == null ? "" : email.trim().toLowerCase()) + "|" + (ip == null ? "?" : ip);
    }

    /** True quando a chave esta bloqueada agora. */
    public boolean isBlocked(String email, String ip) {
        Attempts a = failures.get(key(email, ip));
        if (a == null) return false;
        if (Instant.now().isAfter(a.firstAt().plus(WINDOW))) {
            failures.remove(key(email, ip)); // janela expirou
            return false;
        }
        return a.count().get() >= MAX_ATTEMPTS;
    }

    /** Registra uma falha de autenticacao. */
    public void recordFailure(String email, String ip) {
        String k = key(email, ip);
        Attempts a = failures.compute(k, (unused, cur) ->
                (cur == null || Instant.now().isAfter(cur.firstAt().plus(WINDOW)))
                        ? new Attempts(new AtomicInteger(0), Instant.now())
                        : cur);
        int n = a.count().incrementAndGet();
        if (n >= MAX_ATTEMPTS) {
            log.warn("[AUTH] Login bloqueado por {} min apos {} tentativas falhas ({}).", LOCK_MINUTES, n, k);
        }
    }

    /** Limpa o contador apos um login bem-sucedido. */
    public void reset(String email, String ip) {
        failures.remove(key(email, ip));
    }

    public int lockMinutes() {
        return LOCK_MINUTES;
    }
}
