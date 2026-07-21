package com.razor.api.controller;

import com.razor.api.config.RazorProperties;
import com.razor.api.dto.OnboardingRequest;
import com.razor.api.dto.OnboardingResponse;
import com.razor.api.model.Barbershop;
import com.razor.api.service.BarbershopProvisioningService;
import com.razor.api.service.OnboardingConflictException;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Onboarding self-service: provisiona novas barbearias (tenants).
 *
 * <p><b>Protecao:</b> exige a chave em {@code X-Onboarding-Key} conferida contra
 * {@code razor.onboarding.api-key}. <b>Fail-closed:</b> se a chave nao esta configurada no
 * ambiente, o endpoint rejeita qualquer requisicao (nao existe estado "aberto por engano").
 */
@Slf4j
@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private static final String KEY_HEADER = "X-Onboarding-Key";

    private final BarbershopProvisioningService provisioningService;
    private final RazorProperties properties;

    @PostMapping
    public ResponseEntity<Object> onboard(
            @RequestHeader(value = KEY_HEADER, required = false) String providedKey,
            @Valid @RequestBody OnboardingRequest request) {

        if (!isAuthorized(providedKey)) {
            log.warn("[ONBOARDING] Provisionamento nao autorizado (slug solicitado: {}).", request.slug());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Nao autorizado."));
        }

        try {
            Barbershop shop = provisioningService.provision(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(OnboardingResponse.from(shop));
        } catch (OnboardingConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Fail-closed: sem chave configurada (ou header ausente), nada e autorizado. A comparacao usa
     * {@link MessageDigest#isEqual} para evitar vazamento por timing.
     */
    private boolean isAuthorized(String providedKey) {
        String configured = properties.getOnboarding().getApiKey();
        if (configured == null || configured.isBlank() || providedKey == null) {
            return false;
        }
        return MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8));
    }
}
