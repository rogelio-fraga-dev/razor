package com.razor.api.platform;

import com.razor.api.config.RazorProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sessao do console da plataforma (V51): cookie proprio ({@link #COOKIE}, HttpOnly, SameSite=Strict,
 * so em {@code /api/platform}) e o filtro que o le. Separado do painel das barbearias de proposito:
 * o token de um nunca vale no outro (claim {@code typ}), e nenhum tenant e definido aqui.
 */
public final class PlatformSecurity {

    public static final String COOKIE = "RAZOR_PLATFORM_SESSION";
    static final String PATH = "/api/platform";
    public static final String ROLE = "PLATFORM_ADMIN";

    private PlatformSecurity() {
    }

    /** Quem esta logado no console. */
    public record PlatformPrincipal(UUID userId, String email, String name) {
    }

    @Component
    public static class Cookies {
        private final boolean secure;
        private final Duration ttl;

        public Cookies(RazorProperties properties) {
            this.secure = properties.getSecurity().isCookieSecure();
            this.ttl = Duration.ofMinutes(properties.getPlatform().getSessionMinutes());
        }

        public ResponseCookie issue(String token) {
            return base(token).maxAge(ttl).build();
        }

        public ResponseCookie clear() {
            return base("").maxAge(0).build();
        }

        private ResponseCookie.ResponseCookieBuilder base(String value) {
            return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(secure).sameSite("Strict").path(PATH);
        }

        public static String read(HttpServletRequest request) {
            Cookie[] cookies = request.getCookies();
            if (cookies == null) return null;
            for (Cookie c : cookies) {
                if (COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) return c.getValue();
            }
            return null;
        }
    }

    /**
     * Autentica {@code /api/platform/**} pelo cookie do console. Qualquer autenticacao de painel de
     * barbearia que tenha vindo antes e descartada: aqui so vale a sessao da plataforma.
     */
    @Component
    @RequiredArgsConstructor
    public static class Filter extends OncePerRequestFilter {
        private final PlatformAuthService authService;

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String uri = request.getRequestURI();
            return uri == null || !uri.startsWith(PATH + "/");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            SecurityContextHolder.clearContext();
            authService.authenticate(Cookies.read(request)).ifPresent(u -> {
                var principal = new PlatformPrincipal(u.getId(), u.getEmail(), u.getName());
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
            });
            chain.doFilter(request, response);
        }
    }
}
