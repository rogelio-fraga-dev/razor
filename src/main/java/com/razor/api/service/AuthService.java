package com.razor.api.service;

import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import com.razor.api.repository.TenantUserRepository;
import com.razor.api.security.JwtService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Autenticacao do painel web: valida email + senha (bcrypt) e emite o JWT da sessao.
 * Mensagem de erro generica (nao revela se o email existe).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public record LoginResult(String token, UUID barbershopId, UserRole role, String name, String email) {
    }

    public LoginResult login(String email, String rawPassword) {
        TenantUser user = tenantUserRepository.findByEmail(normalizeEmail(email))
                .filter(TenantUser::isActive)
                .orElseThrow(() -> new BadCredentialsException("Credenciais invalidas."));

        if (rawPassword == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Credenciais invalidas.");
        }

        log.info("[AUTH] Login do usuario {} (tenant {})", user.getEmail(), user.getBarbershopId());
        return new LoginResult(jwtService.issue(user), user.getBarbershopId(), user.getRole(),
                user.getName(), user.getEmail());
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
