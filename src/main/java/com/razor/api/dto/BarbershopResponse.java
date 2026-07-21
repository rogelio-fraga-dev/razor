package com.razor.api.dto;

import com.razor.api.model.Barbershop;
import java.util.UUID;

/** Perfil da barbearia para o painel web. */
public record BarbershopResponse(UUID id, String name, String slug, String whatsappInstance, boolean active) {

    public static BarbershopResponse from(Barbershop shop) {
        return new BarbershopResponse(shop.getId(), shop.getName(), shop.getSlug(),
                shop.getWhatsappInstance(), shop.isActive());
    }
}
