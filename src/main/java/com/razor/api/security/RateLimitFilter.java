package com.razor.api.security;

import com.razor.api.config.RazorProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Aplica o freio de requisicoes em {@code /api/admin/**} e no webhook.
 *
 * <p>Roda <b>antes</b> da cadeia do Spring Security ({@code @Order} negativo): um atacante que
 * dispara mil requisicoes com token invalido nao deve custar mil validacoes de JWT. O login tem
 * freio proprio ({@link LoginRateLimiter}), que conta <i>falhas</i> por email+IP e nao volume —
 * sao controles diferentes e ambos continuam valendo.
 *
 * <p>A chave e o IP. Nao e infalivel (NAT compartilha IP, proxy pode forjar cabecalho), mas e o
 * que existe antes de autenticar — e o ponto e justamente barrar quem ainda nao se identificou.
 */
@Component
@Order(-200)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String ADMIN_PREFIX = "/api/admin/";
    private static final String WEBHOOK_PREFIX = "/api/webhook";

    /** Limpa janelas expiradas a cada N requisicoes, para nao varrer o mapa a toda chamada. */
    private static final long EVICT_EVERY = 500;

    private final RazorProperties properties;
    private final ApiRateLimiter limiter;
    private final AtomicLong seen = new AtomicLong();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        RazorProperties.RateLimit cfg = properties.getSecurity().getRateLimit();
        String path = request.getRequestURI();
        String scope = scopeOf(path);

        if (!cfg.isEnabled() || scope == null) {
            chain.doFilter(request, response);
            return;
        }

        Duration window = Duration.ofSeconds(cfg.getWindowSeconds());
        int limit = WEBHOOK_PREFIX.equals(scope) ? cfg.getWebhookPerMinute() : cfg.getAdminPerMinute();

        if (seen.incrementAndGet() % EVICT_EVERY == 0) {
            limiter.evictExpired(window);
        }

        if (!limiter.tryAcquire(scope + "|" + clientIp(request), limit, window)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            // Retry-After em segundos: cliente bem-comportado (e a propria Evolution) sabe
            // esperar em vez de insistir imediatamente.
            response.setHeader("Retry-After", String.valueOf(cfg.getWindowSeconds()));
            response.getWriter().write(
                    "{\"error\":\"Muitas requisições. Tente novamente em instantes.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /** Escopo do limite, ou {@code null} quando a rota nao entra no freio. */
    private String scopeOf(String path) {
        if (path == null) return null;
        if (path.startsWith(ADMIN_PREFIX)) return ADMIN_PREFIX;
        if (path.startsWith(WEBHOOK_PREFIX)) return WEBHOOK_PREFIX;
        return null;
    }

    /**
     * IP do cliente. Atras de proxy (nginx do painel, load balancer) o {@code remoteAddr} e o do
     * proxy e todo mundo cairia na mesma chave, entao o primeiro {@code X-Forwarded-For} vale.
     *
     * <p>Esse cabecalho e forjavel por quem fala direto com a aplicacao — em producao a app fica
     * atras do proxy, que reescreve o valor. Vale a leitura porque o custo de errar aqui e um
     * limite por IP falso, nao um bypass de autenticacao.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (!first.isEmpty()) return first;
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "?";
    }
}
