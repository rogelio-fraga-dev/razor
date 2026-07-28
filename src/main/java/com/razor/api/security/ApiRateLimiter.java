package com.razor.api.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Contador de requisicoes por chave e janela fixa, usado pelo {@link RateLimitFilter}.
 *
 * <p>Janela fixa e nao deslizante: um cliente pode emitir ate 2x o limite na virada de duas
 * janelas. Aceito de proposito — o alvo aqui e varredura e laco de abuso, que geram ordens de
 * grandeza acima do limite, nao um pico de borda. A alternativa (janela deslizante) custaria
 * guardar o timestamp de cada requisicao por chave, o que e memoria demais para o ganho.
 *
 * <p>Em memoria, pelo mesmo criterio do {@link LoginRateLimiter}: resolve o caso real (uma
 * instancia) sem introduzir dependencia. Com varias instancias cada uma passa a ter seu proprio
 * contador — o limite efetivo vira N x o configurado. Quando isso importar, migrar para o Redis
 * que ja existe atras do {@code StateStore}.
 */
@Slf4j
@Component
public class ApiRateLimiter {

    private record Window(AtomicInteger count, Instant startedAt) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * Registra uma requisicao e diz se ela deve passar.
     *
     * @param key    quem esta chamando (escopo + IP)
     * @param limit  requisicoes permitidas por janela
     * @param window duracao da janela
     * @return {@code true} se dentro do limite, {@code false} se estourou
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        Instant now = Instant.now();
        Window w = windows.compute(key, (unused, cur) ->
                (cur == null || now.isAfter(cur.startedAt().plus(window)))
                        ? new Window(new AtomicInteger(0), now)
                        : cur);

        int used = w.count().incrementAndGet();
        if (used == limit + 1) {
            // Loga uma vez por janela, na transicao: sem isso um laco de abuso encheria o log
            // com uma linha por requisicao — o proprio alerta viraria o incidente.
            log.warn("[RATE-LIMIT] {} passou de {} requisicoes na janela de {}s.",
                    key, limit, window.toSeconds());
        }
        return used <= limit;
    }

    /**
     * Descarta janelas ja expiradas. Chamado pelo filtro de tempos em tempos para que o mapa
     * nao cresca com o IP de quem passou uma vez e nunca mais voltou.
     */
    public void evictExpired(Duration window) {
        Instant cutoff = Instant.now().minus(window);
        windows.entrySet().removeIf(e -> e.getValue().startedAt().isBefore(cutoff));
    }

    /** Quantidade de chaves vivas — usado nos testes e util para depurar consumo de memoria. */
    public int trackedKeys() {
        return windows.size();
    }

    /** Zera todos os contadores (testes). */
    public void reset() {
        windows.clear();
    }
}
