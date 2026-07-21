package com.razor.api.dto;

import com.razor.api.model.BarbershopAdmin;
import java.util.UUID;

/** Telefone administrativo (admin do WhatsApp) para o painel web. */
public record AdminContactResponse(UUID id, String phone, String name) {

    public static AdminContactResponse from(BarbershopAdmin admin) {
        return new AdminContactResponse(admin.getId(), admin.getPhone(), admin.getName());
    }
}
