package com.razor.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Vale/adiantamento ao barbeiro.
 *
 * <p>{@code date} nula = hoje. {@code fromCashRegister} decide se o dinheiro sai da gaveta:
 * {@code true} exige caixa aberto e gera sangria; {@code false} (padrao) e o vale por fora.
 */
public record AdvanceRequest(
        @NotNull(message = "Informe o profissional.") UUID professionalId,
        @NotNull(message = "Informe o valor do vale.")
                @Positive(message = "O valor do vale deve ser maior que zero.") BigDecimal amount,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        Boolean fromCashRegister,
        String notes) {
}
