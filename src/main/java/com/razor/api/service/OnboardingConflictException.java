package com.razor.api.service;

/**
 * Provisionamento rejeitado por conflito de unicidade (slug ou instancia de WhatsApp ja em uso).
 * Mapeada para HTTP 409 no controller.
 */
public class OnboardingConflictException extends RuntimeException {

    public OnboardingConflictException(String message) {
        super(message);
    }
}
