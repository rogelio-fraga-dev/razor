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
 * Sessao do console da plataforma (V51/V56): cookies proprios (HttpOnly, SameSite=Strict, so em
 * {@code /api/platform}) e o filtro que os le. Separado do painel das barbearias de proposito: o
 * token de um nunca vale no outro (claim {@code typ}), e nenhum tenant e definido aqui.
 */
public final class PlatformSecurity {

    public static final String COOKIE = "RAZOR_PLATFORM_SESSION";
    /** "Lembrar este aparelho" (V56): so e lido no login. */
    public static final String DEVICE_COOKIE = "RAZOR_PLATFORM_DEVICE";
    static final String PATH = "/api/platform";
    static final String AUTH_PATH = "/api/platform/auth";
    public static final String ROLE = "PLATFORM_ADMIN";

    private PlatformSecurity() {
    }

    /** Quem esta logado no console e em que situacao do 2FA. */
    public record PlatformPrincipal(UUID userId, String email, String name, UUID sessionId, boolean mfaEnabled) {
    }

    @Component
    public static class Cookies {
        private final boolean secure;
        private final Duration ttl;
        private final Duration deviceTtl;

        public Cookies(RazorProperties properties) {
            this.secure = properties.getSecurity().isCookieSecure();
            this.ttl = Duration.ofMinutes(properties.getPlatform().getSessionMinutes());
            this.deviceTtl = Duration.ofDays(properties.getPlatform().getTrustedDeviceDays());
        }

        public ResponseCookie issue(String token) {
            return base(COOKIE, token, PATH).maxAge(ttl).build();
        }

        public ResponseCookie clear() {
            return base(COOKIE, "", PATH).maxAge(0).build();
        }

        public ResponseCookie device(String token) {
            return base(DEVICE_COOKIE, token, AUTH_PATH).maxAge(deviceTtl).build();
        }

        public ResponseCookie clearDevice() {
            return base(DEVICE_COOKIE, "", AUTH_PATH).maxAge(0).build();
        }

        private ResponseCookie.ResponseCookieBuilder base(String name, String value, String path) {
            return ResponseCookie.from(name, value).httpOnly(true).secure(secure).sameSite("Strict").path(path);
        }

        public static String read(HttpServletRequest request) {
            return read(request, COOKIE);
        }

        public static String read(HttpServletRequest request, String name) {
            Cookie[] cookies = request.getCookies();
            if (cookies == null) return null;
            for (Cookie c : cookies) {
                if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) return c.getValue();
            }
            return null;
        }
    }

    /**
     * Autentica {@code /api/platform/**} pelo cookie do console. Qualquer autenticacao de painel de
     * barbearia que tenha vindo antes e descartada: aqui so vale a sessao da plataforma.
     *
     * <p>Carencia do 2FA vencida sem configurar: a sessao so alcanca o login, o {@code /me} e a
     * configuracao do 2FA — o resto responde 403 {@code MFA_REQUIRED}.
     */
    @Component
    @RequiredArgsConstructor
    public static class Filter extends OncePerRequestFilter {
        // Provider (preguicoso): o SecurityConfig depende deste filtro, e o PlatformAuthService
        // depende do PasswordEncoder definido no SecurityConfig — injecao direta fecharia um ciclo.
        private final org.springframework.beans.factory.ObjectProvider<PlatformAuthService> authService;

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String uri = request.getRequestURI();
            return uri == null || !uri.startsWith(PATH + "/");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            SecurityContextHolder.clearContext();
            PlatformAuthService auth = authService.getObject();
            var a = auth.authenticate(Cookies.read(request)).orElse(null);
            if (a != null) {
                PlatformUser u = a.user();
                if (auth.mfaRequiredNow(u) && !allowedWithoutMfa(request.getRequestURI())) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"error\":\"O prazo para ativar a verificação em duas etapas acabou. "
                            + "Configure o app autenticador para continuar.\",\"code\":\"MFA_REQUIRED\"}");
                    return;
                }
                var principal = new PlatformPrincipal(u.getId(), u.getEmail(), u.getName(), a.sessionId(), u.isTotpEnabled());
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
            }
            chain.doFilter(request, response);
        }

        static boolean allowedWithoutMfa(String uri) {
            return uri.startsWith(AUTH_PATH + "/") || uri.equals(PATH + "/me")
                    || uri.startsWith(PATH + "/security/2fa/setup");
        }
    }
}
