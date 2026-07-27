package com.razor.api.dto;

import com.razor.api.model.Product;
import java.math.BigDecimal;
import java.util.UUID;

/** Produto do balcao como o painel consome. */
public record ProductResponse(UUID id, String name, BigDecimal price, boolean active) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getPrice(), p.isActive());
    }
}
