package com.razor.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Anti-falta (V42): confirmacao da vespera e sinal para quem falta muito. Record e endpoint
 * proprios pelo mesmo motivo do limite de agendamentos: o PUT das configuracoes do bot troca o
 * registro inteiro.
 *
 * @param pixConfigured so leitura — sem PIX configurado a regra do sinal nao vale (o painel avisa)
 */
public record NoShowSettingsPayload(
        @NotNull Boolean confirmationEnabled,
        @NotNull @Min(0) @Max(23) Integer confirmationHour,
        @NotNull @Min(1) @Max(23) Integer confirmationDeadlineHours,
        @NotNull Boolean autoReleaseUnconfirmed,
        @Min(1) @Max(20) Integer noShowDepositAfter,
        @DecimalMin("0.01") BigDecimal noShowDepositAmount,
        Boolean pixConfigured) {
}
