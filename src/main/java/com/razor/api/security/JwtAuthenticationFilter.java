package com.razor.api.security;

import com.razor.api.tenant.TenantContext;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica a requisicao pelo JWT no header {@code Authorization: Bearer <token>}. Em caso de
 * token valido, popula o {@code SecurityContext} com o {@link TenantUserPrincipal} e define o
 * {@link TenantContext} com a barbearia do usuario (limpo ao final da requisicao). Token ausente
 * ou invalido segue sem autenticacao — endpoints protegidos respondem 401.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean tenantSet = false;
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                try {
                    TenantUserPrincipal principal = jwtService.parse(header.substring(BEARER_PREFIX.length()));
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
                    var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    TenantContext.set(principal.barbershopId());
                    tenantSet = true;
                } catch (JwtException | IllegalArgumentException e) {
                    log.debug("[SECURITY] JWT invalido: {}", e.getMessage());
                }
            }
            chain.doFilter(request, response);
        } finally {
            if (tenantSet) {
                TenantContext.clear();
            }
        }
    }
}
