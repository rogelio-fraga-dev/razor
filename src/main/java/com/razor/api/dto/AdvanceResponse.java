package com.razor.api.dto;

import com.razor.api.model.ProfessionalAdvance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Um vale como o painel consome. */
public record AdvanceResponse(
        UUID id,
        UUID professionalId,
        String professionalName,
        BigDecimal amount,
        LocalDate advanceDate,
        /** True quando o vale saiu da gaveta (gerou sangria). */
        boolean fromCashRegister,
        String notes,
        String grantedByName) {

    public static AdvanceResponse from(ProfessionalAdvance a, String professionalName) {
        return new AdvanceResponse(a.getId(), a.getProfessionalId(), professionalName,
                a.getAmount(), a.getAdvanceDate(), a.getCashMovementId() != null,
                a.getNotes(), a.getGrantedByName());
    }
}
