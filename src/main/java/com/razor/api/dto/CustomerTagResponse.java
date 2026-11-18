package com.razor.api.dto;

import com.razor.api.model.CustomerTag;
import java.util.UUID;

/** Tag de cliente (V36) para o painel. */
public record CustomerTagResponse(UUID id, String name, String color) {

    public static CustomerTagResponse from(CustomerTag t) {
        return new CustomerTagResponse(t.getId(), t.getName(), t.getColor());
    }
}
