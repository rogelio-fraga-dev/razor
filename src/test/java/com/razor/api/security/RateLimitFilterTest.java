package com.razor.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.razor.api.config.RazorProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("Freio geral da API (por IP, em /api/admin e no webhook)")
class RateLimitFilterTest {

    private RazorProperties properties;
    private ApiRateLimiter limiter;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RazorProperties();
        properties.getSecurity().getRateLimit().setAdminPerMinute(3);
        properties.getSecurity().getRateLimit().setWebhookPerMinute(2);
        limiter = new ApiRateLimiter();
        filter = new RateLimitFilter(properties, limiter);
    }

    private MockHttpServletRequest request(String uri, String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    @DisplayName("dentro do limite a requisição segue para a cadeia")
    void withinLimit_passesThrough() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            filter.doFilter(request("/api/admin/reports/financial", "10.0.0.1"),
                    new MockHttpServletResponse(), chain);
        }

        verify(chain, times(3)).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("passando do limite responde 429 com Retry-After e não chama a cadeia")
    void overLimit_returns429() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 3; i++) {
            filter.doFilter(request("/api/admin/products", "10.0.0.1"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        FilterChain afterLimit = mock(FilterChain.class);
        filter.doFilter(request("/api/admin/products", "10.0.0.1"), blocked, afterLimit);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentAsString()).contains("Muitas requisições");
        verify(afterLimit, never()).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("o limite é por IP: um cliente abusando não derruba os outros")
    void limitIsPerIp() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 5; i++) {
            filter.doFilter(request("/api/admin/products", "10.0.0.1"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse other = new MockHttpServletResponse();
        filter.doFilter(request("/api/admin/products", "10.0.0.2"), other, chain);

        assertThat(other.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("webhook e painel têm cotas separadas — o bot não consome o limite do dono")
    void adminAndWebhookHaveSeparateBudgets() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        // esgota a cota do webhook (2)
        for (int i = 0; i < 2; i++) {
            filter.doFilter(request("/api/webhook", "10.0.0.1"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse webhookBlocked = new MockHttpServletResponse();
        filter.doFilter(request("/api/webhook", "10.0.0.1"), webhookBlocked, chain);
        assertThat(webhookBlocked.getStatus()).isEqualTo(429);

        // o painel, do mesmo IP, continua respondendo
        MockHttpServletResponse admin = new MockHttpServletResponse();
        filter.doFilter(request("/api/admin/products", "10.0.0.1"), admin, chain);
        assertThat(admin.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("atrás de proxy, o IP vem do primeiro X-Forwarded-For")
    void behindProxy_usesForwardedFor() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        // Mesmo remoteAddr (o proxy), clientes diferentes: sem ler o cabeçalho os dois cairiam
        // na mesma chave e um cliente derrubaria o outro.
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = request("/api/admin/products", "172.17.0.1");
            req.addHeader("X-Forwarded-For", "203.0.113.7, 172.17.0.1");
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest outro = request("/api/admin/products", "172.17.0.1");
        outro.addHeader("X-Forwarded-For", "203.0.113.9");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(outro, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("rota fora do escopo (login, health) não entra no freio")
    void outOfScopePaths_notLimited() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 50; i++) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilter(request("/api/auth/login", "10.0.0.1"), resp, chain);
            assertThat(resp.getStatus()).isEqualTo(200);
        }
        assertThat(limiter.trackedKeys()).isZero();
    }

    @Test
    @DisplayName("desligado por configuração, deixa tudo passar")
    void disabled_passesEverything() throws Exception {
        properties.getSecurity().getRateLimit().setEnabled(false);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilter(request("/api/admin/products", "10.0.0.1"), resp, chain);
            assertThat(resp.getStatus()).isEqualTo(200);
        }
    }
}
