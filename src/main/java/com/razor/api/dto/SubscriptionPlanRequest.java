package com.razor.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Criacao/atualizacao de um plano de assinatura. No POST, {@code active} nasce verdadeiro. */
public record SubscriptionPlanRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @PositiveOrZero BigDecimal price,
        String description,
        Boolean active) {
}
