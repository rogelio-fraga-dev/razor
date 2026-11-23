package com.razor.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Forca a emissao do cookie {@code XSRF-TOKEN}. No Spring Security 6 o token CSRF e carregado sob
 * demanda: se nada o le durante a requisicao, o {@code CookieCsrfTokenRepository} nunca escreve o
 * cookie e a SPA fica sem o que mandar no header {@code X-XSRF-TOKEN}. Ler o token aqui resolve.
 *
 * <p>So atua nas rotas do painel ({@code /api/admin/**} e {@code /api/auth/**}): webhook e site
 * publico nao precisam de cookie nenhum. Nao e um {@code @Component} de proposito — registrado
 * pelo {@link SecurityConfig} dentro da cadeia, logo apos o {@code CsrfFilter}; como bean, o
 * Spring Boot o registraria de novo como filtro do servlet, fora da cadeia.
 */
class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !(uri.startsWith("/api/admin/") || uri.startsWith("/api/auth/") || uri.startsWith("/api/platform/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken(); // dispara o carregamento -> Set-Cookie quando o cookie ainda nao existe
        }
        chain.doFilter(request, response);
    }
}
