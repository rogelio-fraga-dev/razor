package com.razor.api.dto;

import com.razor.api.model.ServiceOffering;
import java.math.BigDecimal;
import java.util.UUID;

/** Representacao de um servico do catalogo para a API do painel. */
public record ServiceOfferingResponse(
        UUID id, String name, BigDecimal price, int durationMinutes, boolean active, BigDecimal commissionPercent) {

    public static ServiceOfferingResponse from(ServiceOffering s) {
        return new ServiceOfferingResponse(s.getId(), s.getName(), s.getPrice(), s.getDurationMinutes(), s.isActive(),
                s.getCommissionPercent());
    }
}
