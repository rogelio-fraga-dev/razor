package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import com.razor.api.repository.TenantUserRepository;
import com.razor.api.security.JwtService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Testes do {@link AuthService}: login valido emite token; credenciais ruins sao rejeitadas. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock private TenantUserRepository tenantUserRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks private AuthService authService;

    private TenantUser user(boolean active) {
        return TenantUser.builder()
                .id(UUID.randomUUID())
                .email("dono@loja.com")
                .passwordHash("hash")
                .barbershopId(UUID.randomUUID())
                .role(UserRole.OWNER)
                .active(active)
                .build();
    }

    @Test
    @DisplayName("login válido emite o token e devolve tenant e papel")
    void login_valid_issuesToken() {
        TenantUser u = user(true);
        when(tenantUserRepository.findByEmail("dono@loja.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("senha1234", "hash")).thenReturn(true);
        when(jwtService.issue(u)).thenReturn("jwt-token");

        AuthService.LoginResult result = authService.login("Dono@Loja.com", "senha1234");

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.barbershopId()).isEqualTo(u.getBarbershopId());
        assertThat(result.role()).isEqualTo(UserRole.OWNER);
    }

    @Test
    @DisplayName("senha errada é rejeitada")
    void login_wrongPassword_throws() {
        when(tenantUserRepository.findByEmail(any())).thenReturn(Optional.of(user(true)));
        when(passwordEncoder.matches(eq("errada"), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login("dono@loja.com", "errada"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("email inexistente é rejeitado (mensagem genérica)")
    void login_unknownEmail_throws() {
        when(tenantUserRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nao@existe.com", "x"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("usuário inativo é rejeitado")
    void login_inactiveUser_throws() {
        when(tenantUserRepository.findByEmail(any())).thenReturn(Optional.of(user(false)));

        assertThatThrownBy(() -> authService.login("dono@loja.com", "senha1234"))
                .isInstanceOf(BadCredentialsException.class);
    }
}
