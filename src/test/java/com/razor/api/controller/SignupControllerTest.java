package com.razor.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.config.RazorProperties;
import com.razor.api.dto.SignupRequest;
import com.razor.api.model.Barbershop;
import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import com.razor.api.security.JwtService;
import com.razor.api.security.SessionCookies;
import com.razor.api.service.BarbershopProvisioningService.Provisioned;
import com.razor.api.service.SignupService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("POST /api/signup — cadastro com login automático")
class SignupControllerTest {

    @Mock private SignupService signupService;
    @Mock private JwtService jwtService;

    private RazorProperties properties;
    private SignupController controller;

    @BeforeEach
    void setUp() {
        properties = new RazorProperties();
        controller = new SignupController(signupService, jwtService, new SessionCookies(properties),
                CookieCsrfTokenRepository.withHttpOnlyFalse(), properties);
    }

    private SignupRequest req() {
        return new SignupRequest("Barbearia do Zé", "José", "ze@loja.com", "senha1234", "21970001111",
                "Niterói", "solo", null);
    }

    @Test
    @DisplayName("201 com a sessão já aberta (cookie HttpOnly) e um XSRF-TOKEN novo")
    void signup_logsIn() {
        UUID shopId = UUID.randomUUID();
        Barbershop shop = Barbershop.builder().id(shopId).slug("barbearia-do-ze").status(Barbershop.STATUS_TRIAL)
                .trialEndsAt(LocalDateTime.of(2026, 12, 2, 10, 0)).build();
        TenantUser owner = TenantUser.builder().id(UUID.randomUUID()).barbershopId(shopId).email("ze@loja.com")
                .name("José").role(UserRole.OWNER).build();
        when(signupService.signup(any())).thenReturn(new Provisioned(shop, owner));
        when(jwtService.issue(owner)).thenReturn("jwt-token");

        MockHttpServletResponse http = new MockHttpServletResponse();
        ResponseEntity<Object> resp = controller.signup(req(), new MockHttpServletRequest(), http);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String cookie = resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).startsWith(SessionCookies.NAME + "=jwt-token").contains("HttpOnly");
        assertThat(http.getCookie("XSRF-TOKEN")).isNotNull();
        SignupController.SignupResponse body = (SignupController.SignupResponse) resp.getBody();
        assertThat(body.role()).isEqualTo("OWNER");
        assertThat(body.slug()).isEqualTo("barbearia-do-ze");
        assertThat(body.status()).isEqualTo("TRIAL");
        // o token nunca vai no corpo
        assertThat(body.toString()).doesNotContain("jwt-token");
    }

    @Test
    @DisplayName("desligado por config: 404 e nada é criado")
    void signup_disabled() {
        properties.getSignup().setEnabled(false);

        ResponseEntity<Object> resp = controller.signup(req(), new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(signupService, never()).signup(any());
    }

    @Test
    @DisplayName("honeypot: 400 genérico, sem sessão")
    void signup_honeypot() {
        when(signupService.signup(any())).thenThrow(new SignupService.SuspectedBotException());

        ResponseEntity<Object> resp = controller.signup(req(), new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
    }
}
