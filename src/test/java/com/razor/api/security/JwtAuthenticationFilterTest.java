package com.razor.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.UserRole;
import com.razor.api.tenant.TenantContext;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Testes do {@link JwtAuthenticationFilter}: token valido popula SecurityContext + TenantContext
 * (limpo ao final); token ausente/invalido segue sem autenticar.
 */
class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        filter = new JwtAuthenticationFilter(jwtService);
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("token válido autentica e escopa o tenant durante a requisição")
    void validToken_authenticatesAndScopesTenant() throws Exception {
        UUID shop = UUID.randomUUID();
        TenantUserPrincipal principal = new TenantUserPrincipal(UUID.randomUUID(), "dono@loja.com", shop, UserRole.OWNER);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok-123");
        when(jwtService.parse("tok-123")).thenReturn(principal);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        AtomicReference<UUID> tenantDuring = new AtomicReference<>();
        doAnswer(inv -> {
            tenantDuring.set(TenantContext.getCurrent());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertThat(tenantDuring.get()).isEqualTo(shop); // tenant ativo durante o processamento
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(principal);
        assertThat(TenantContext.isSet()).isFalse(); // limpo ao final
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("token inválido segue sem autenticação")
    void invalidToken_noAuthentication() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer ruim");
        when(jwtService.parse("ruim")).thenThrow(new JwtException("invalido"));
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("sem header, o cookie de sessão HttpOnly autentica (fluxo do painel)")
    void sessionCookie_authenticates() throws Exception {
        TenantUserPrincipal principal = new TenantUserPrincipal(
                UUID.randomUUID(), "dono@loja.com", UUID.randomUUID(), UserRole.OWNER);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/me");
        request.setCookies(new Cookie(SessionCookies.NAME, "tok-cookie"));
        when(jwtService.parse("tok-cookie")).thenReturn(principal);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(principal);
    }

    @Test
    @DisplayName("header Authorization presente manda: o cookie não é consultado nem como fallback")
    void headerPresent_cookieIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/services");
        request.addHeader("Authorization", "Basic abc");
        request.setCookies(new Cookie(SessionCookies.NAME, "tok-cookie"));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).parse(any());
    }

    @Test
    @DisplayName("sem header Authorization segue sem autenticação")
    void noHeader_noAuthentication() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
