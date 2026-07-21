package com.razor.api.dto;

import com.razor.api.model.Professional;
import java.math.BigDecimal;
import java.util.UUID;

/** Representacao de um profissional (barbeiro) para a API do painel. */
public record ProfessionalResponse(
        UUID id, String name, String phone, boolean active, BigDecimal commissionPercent) {

    public static ProfessionalResponse from(Professional p) {
        return new ProfessionalResponse(p.getId(), p.getName(), p.getPhone(), p.isActive(), p.getCommissionPercent());
    }
}
