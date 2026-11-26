package com.razor.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Payload de criacao/atualizacao de um servico do catalogo. No POST (criar), {@code active} e
 * ignorado (nasce ativo); no PUT (atualizar), {@code active} controla ativar/desativar.
 * {@code commissionPercent}: comissao propria do servico (V27); vazio = vale o % do barbeiro.
 */
public record ServiceRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @PositiveOrZero BigDecimal price,
        @Positive int durationMinutes,
        Boolean active,
        @DecimalMin("0") @DecimalMax("100") BigDecimal commissionPercent) {

    public ServiceRequest(String name, BigDecimal price, int durationMinutes, Boolean active) {
        this(name, price, durationMinutes, active, null);
    }
}
