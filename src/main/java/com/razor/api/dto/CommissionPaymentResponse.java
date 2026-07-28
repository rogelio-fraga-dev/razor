package com.razor.api.dto;

import com.razor.api.model.CommissionPayment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Um acerto de comissao como o painel consome. */
public record CommissionPaymentResponse(
        UUID id,
        UUID professionalId,
        String professionalName,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal amount,
        /** True quando o pagamento saiu da gaveta (gerou sangria). */
        boolean fromCashRegister,
        String notes,
        String paidByName,
        LocalDateTime paidAt) {

    public static CommissionPaymentResponse from(CommissionPayment p, String professionalName) {
        return new CommissionPaymentResponse(
                p.getId(), p.getProfessionalId(), professionalName,
                p.getPeriodStart(), p.getPeriodEnd(), p.getAmount(),
                p.getCashMovementId() != null, p.getNotes(), p.getPaidByName(), p.getPaidAt());
    }
}
