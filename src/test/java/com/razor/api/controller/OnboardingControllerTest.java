package com.razor.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.config.RazorProperties;
import com.razor.api.dto.OnboardingRequest;
import com.razor.api.dto.OnboardingResponse;
import com.razor.api.model.Barbershop;
import com.razor.api.service.BarbershopProvisioningService;
import com.razor.api.service.OnboardingConflictException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Testes do {@link OnboardingController}: protecao por chave (fail-closed) e delegacao ao
 * provisionamento. Testa a logica de autorizacao/roteamento diretamente (sem MVC).
 */
@ExtendWith(MockitoExtension.class)
class OnboardingControllerTest {

    private static final String KEY = "chave-secreta";

    @Mock private BarbershopProvisioningService provisioningService;

    private RazorProperties properties;
    private OnboardingController controller;

    @BeforeEach
    void setUp() {
        properties = new RazorProperties();
        controller = new OnboardingController(provisioningService, properties);
    }

    private OnboardingRequest request() {
        return new OnboardingRequest("Barbearia Nova", "loja-2", "loja2-wa", "Rafael", List.of("5521970001111"));
    }

    @Test
    @DisplayName("fail-closed: sem chave configurada, rejeita mesmo com header (403) e não provisiona")
    void unauthorized_whenKeyNotConfigured() {
        // properties.onboarding.apiKey == "" (padrao)
        ResponseEntity<Object> resp = controller.onboard(KEY, request());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(provisioningService, never()).provision(any());
    }

    @Test
    @DisplayName("chave errada é rejeitada (403)")
    void unauthorized_whenWrongKey() {
        properties.getOnboarding().setApiKey(KEY);

        ResponseEntity<Object> resp = controller.onboard("errada", request());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(provisioningService, never()).provision(any());
    }

    @Test
    @DisplayName("header ausente é rejeitado (403)")
    void unauthorized_whenHeaderMissing() {
        properties.getOnboarding().setApiKey(KEY);

        ResponseEntity<Object> resp = controller.onboard(null, request());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(provisioningService, never()).provision(any());
    }

    @Test
    @DisplayName("chave correta provisiona e retorna 201 com o id da barbearia")
    void authorized_provisions() {
        properties.getOnboarding().setApiKey(KEY);
        UUID id = UUID.randomUUID();
        when(provisioningService.provision(any()))
                .thenReturn(Barbershop.builder().id(id).slug("loja-2").name("Barbearia Nova").build());

        ResponseEntity<Object> resp = controller.onboard(KEY, request());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isInstanceOf(OnboardingResponse.class);
        assertThat(((OnboardingResponse) resp.getBody()).barbershopId()).isEqualTo(id);
    }

    @Test
    @DisplayName("conflito de unicidade vira 409")
    void conflict_returns409() {
        properties.getOnboarding().setApiKey(KEY);
        when(provisioningService.provision(any()))
                .thenThrow(new OnboardingConflictException("slug ja existe"));

        ResponseEntity<Object> resp = controller.onboard(KEY, request());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
