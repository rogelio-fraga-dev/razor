package com.razor.api.security;

import com.razor.api.config.RazorProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie de sessao do painel: carrega o JWT com {@code HttpOnly} (o JavaScript da pagina nunca
 * le o token, entao um XSS nao consegue exfiltra-lo), {@code SameSite=Strict} e {@code Path=/api}
 * (so viaja para a API, nunca para os assets). O {@code Max-Age} acompanha o TTL do token: o
 * cookie morre junto com ele, sem sessao "zumbi" no navegador.
 *
 * <p>Cookie enviado automaticamente pelo navegador exige defesa de CSRF — ver
 * {@link SecurityConfig}.
 */
@Component
public class SessionCookies {

    /** Nome do cookie que carrega o JWT. */
    public static final String NAME = "RAZOR_SESSION";
    static final String PATH = "/api";

    private final boolean secure;
    private final Duration ttl;

    public SessionCookies(RazorProperties properties) {
        this.secure = properties.getSecurity().isCookieSecure();
        this.ttl = Duration.ofMinutes(properties.getSecurity().getJwtExpirationMinutes());
    }

    /** Set-Cookie com o JWT recem-emitido. */
    public ResponseCookie issue(String token) {
        return base(token).maxAge(ttl).build();
    }

    /** Set-Cookie que apaga a sessao (mesmo nome/path, Max-Age=0). */
    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    public boolean isSecure() {
        return secure;
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }

    /** Le o JWT do cookie de sessao, ou {@code null} se ausente/vazio. */
    public static String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (NAME.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }
}
