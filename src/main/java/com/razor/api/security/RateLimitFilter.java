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
 * Aplica o freio de requisicoes em {@code /api/admin/**}, no webhook, nas rotas publicas do site
 * ({@code /api/site/**}, com um teto extra e mais curto no POST de agendamento online) e no
 * cadastro de barbearia pela landing ({@code /api/signup}, V49 — teto por hora, nao por minuto).
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
    /** Site white-label e agendamento online: publicos, entao o IP e o unico freio. */
    private static final String SITE_PREFIX = "/api/site/";
    private static final String BOOKING_SCOPE = "site-booking";
    /** Cadastro publico de barbearia (V49): ninguem abre cinco barbearias numa hora. */
    private static final String SIGNUP_PATH = "/api/signup";

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
        Duration bookingWindow = Duration.ofSeconds(cfg.getBookingWindowSeconds());
        Duration signupWindow = Duration.ofSeconds(cfg.getSignupWindowSeconds());
        boolean signup = SIGNUP_PATH.equals(scope);
        int limit = switch (scope) {
            case WEBHOOK_PREFIX -> cfg.getWebhookPerMinute();
            case SITE_PREFIX -> cfg.getSitePerMinute();
            case SIGNUP_PATH -> cfg.getSignupPerWindow();
            default -> cfg.getAdminPerMinute();
        };

        if (seen.incrementAndGet() % EVICT_EVERY == 0) {
            // A maior janela manda: despejar com a de 1 minuto zeraria o contador de quem ja
            // gastou os agendamentos da hora — o freio do POST viraria 1 minuto.
            Duration longest = window;
            if (bookingWindow.compareTo(longest) > 0) longest = bookingWindow;
            if (signupWindow.compareTo(longest) > 0) longest = signupWindow;
            limiter.evictExpired(longest);
        }

        String ip = ClientIp.of(request);
        if (!limiter.tryAcquire(scope + "|" + ip, limit, signup ? signupWindow : window)) {
            reject(response, signup ? cfg.getSignupWindowSeconds() : cfg.getWindowSeconds());
            return;
        }

        // Criar agendamento sem login e o unico POST publico: alem do volume geral do site, um
        // teto curto por IP na hora. Quem marca um corte faz isso uma ou duas vezes, nao dez.
        if (isPublicBooking(request)
                && !limiter.tryAcquire(BOOKING_SCOPE + "|" + ip, cfg.getBookingPerWindow(), bookingWindow)) {
            reject(response, cfg.getBookingWindowSeconds());
            return;
        }

        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Retry-After em segundos: cliente bem-comportado (e a propria Evolution) sabe
        // esperar em vez de insistir imediatamente.
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.getWriter().write(
                "{\"error\":\"Muitas requisições. Tente novamente em instantes.\"}");
    }

    /** POST /api/site/{slug}/booking — a criacao de agendamento pela pagina publica. */
    private boolean isPublicBooking(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "POST".equalsIgnoreCase(request.getMethod())
                && path != null && path.startsWith(SITE_PREFIX) && path.endsWith("/booking");
    }

    /** Escopo do limite, ou {@code null} quando a rota nao entra no freio. */
    private String scopeOf(String path) {
        if (path == null) return null;
        if (path.startsWith(ADMIN_PREFIX)) return ADMIN_PREFIX;
        if (path.startsWith(WEBHOOK_PREFIX)) return WEBHOOK_PREFIX;
        if (path.startsWith(SITE_PREFIX)) return SITE_PREFIX;
        if (path.equals(SIGNUP_PATH) || path.startsWith(SIGNUP_PATH + "/")) return SIGNUP_PATH;
        return null;
    }
}
