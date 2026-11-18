package com.razor.api.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Freio de forca bruta no login do painel, em duas camadas, ambas com janela de
 * {@value #LOCK_MINUTES} minutos:
 * <ul>
 *   <li><b>email + IP</b>: {@value #MAX_ATTEMPTS} falhas bloqueiam aquele par — o caso comum
 *       (alguem errando a senha, ou um script de uma maquina so);</li>
 *   <li><b>so o email</b>: {@value #MAX_ATTEMPTS_PER_EMAIL} falhas, de qualquer IP, bloqueiam a
 *       conta. Sem isso, um ataque distribuido (ou um unico atacante trocando de IP) testava
 *       senhas sem limite, 4 por endereco. O teto e folgado de proposito: ele tambem e o custo de
 *       alguem travar o login de um usuario de proposito, entao so deve disparar sob ataque.</li>
 * </ul>
 *
 * <p>Em memoria de proposito — resolve o caso real (uma instancia) sem introduzir dependencia.
 * As entradas vencidas sao varridas periodicamente ({@link #evictExpired()}); antes, cada email
 * inventado por um atacante ficava no mapa para sempre. Ao escalar para varias instancias,
 * trocar por Redis junto com o estado da conversa.
 */
@Slf4j
@Component
public class LoginRateLimiter {

    static final int MAX_ATTEMPTS = 5;
    static final int MAX_ATTEMPTS_PER_EMAIL = 20;
    private static final int LOCK_MINUTES = 15;
    private static final Duration WINDOW = Duration.ofMinutes(LOCK_MINUTES);

    private record Attempts(AtomicInteger count, Instant firstAt) {
        boolean expired(Instant now) {
            return now.isAfter(firstAt.plus(WINDOW));
        }
    }

    private final Map<String, Attempts> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    /** Relogio injetavel para os testes avancarem a janela sem dormir. */
    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    private static String email(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static String pairKey(String email, String ip) {
        return "pair|" + email(email) + "|" + (ip == null ? "?" : ip);
    }

    private static String emailKey(String email) {
        return "email|" + email(email);
    }

    /** True quando o par email+IP, ou o email como um todo, esta bloqueado agora. */
    public boolean isBlocked(String email, String ip) {
        return reached(pairKey(email, ip), MAX_ATTEMPTS) || reached(emailKey(email), MAX_ATTEMPTS_PER_EMAIL);
    }

    private boolean reached(String key, int max) {
        Attempts a = failures.get(key);
        if (a == null) return false;
        if (a.expired(clock.instant())) {
            failures.remove(key, a); // janela expirou
            return false;
        }
        return a.count().get() >= max;
    }

    /** Registra uma falha de autenticacao (conta para o par e para o email). */
    public void recordFailure(String email, String ip) {
        int pair = increment(pairKey(email, ip));
        int total = increment(emailKey(email));
        if (pair == MAX_ATTEMPTS) {
            log.warn("[AUTH] Login bloqueado por {} min apos {} falhas de {} para {}.",
                    LOCK_MINUTES, pair, ip, email(email));
        }
        if (total == MAX_ATTEMPTS_PER_EMAIL) {
            log.warn("[AUTH] Conta {} bloqueada por {} min apos {} falhas de IPs variados.",
                    email(email), LOCK_MINUTES, total);
        }
    }

    private int increment(String key) {
        Instant now = clock.instant();
        Attempts a = failures.compute(key, (unused, cur) ->
                (cur == null || cur.expired(now)) ? new Attempts(new AtomicInteger(0), now) : cur);
        return a.count().incrementAndGet();
    }

    /** Limpa os contadores apos um login bem-sucedido. */
    public void reset(String email, String ip) {
        failures.remove(pairKey(email, ip));
        failures.remove(emailKey(email));
    }

    /** Remove janelas vencidas. Agendado; publico para os testes. */
    @Scheduled(fixedDelay = 5, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void evictExpired() {
        Instant now = clock.instant();
        failures.entrySet().removeIf(e -> e.getValue().expired(now));
    }

    /** Quantidade de chaves vivas (testes / depuracao de memoria). */
    int trackedKeys() {
        return failures.size();
    }

    public int lockMinutes() {
        return LOCK_MINUTES;
    }
}
