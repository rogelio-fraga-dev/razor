package com.razor.api.dto;

import com.razor.api.model.Barbershop;
import java.util.UUID;

/** Resposta do provisionamento: identifica a barbearia criada. */
public record OnboardingResponse(UUID barbershopId, String slug, String name) {

    public static OnboardingResponse from(Barbershop shop) {
        return new OnboardingResponse(shop.getId(), shop.getSlug(), shop.getName());
    }
}
