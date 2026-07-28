package com.razor.api.dto;

import com.razor.api.model.PlanBenefit;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Beneficio de plano vindo do painel.
 *
 * <p>{@code targetId} nulo = vale para TODOS do {@code targetType} ("Cosmeticos: 10% OFF").
 * {@code monthlyQuota} nula = ilimitado. {@code weekdays} nulo = todos os dias; senao CSV ISO
 * (1=segunda ... 7=domingo), ex.: {@code "1,2,3"}.
 */
public record PlanBenefitRequest(
        @NotNull(message = "Informe se o beneficio e de servico ou produto.")
                PlanBenefit.TargetType targetType,
        UUID targetId,
        @NotNull(message = "Informe o desconto.")
                @DecimalMin(value = "0", message = "O desconto deve estar entre 0 e 100.")
                @DecimalMax(value = "100", message = "O desconto deve estar entre 0 e 100.")
                BigDecimal discountPercent,
        @Positive(message = "A cota mensal deve ser maior que zero.") Integer monthlyQuota,
        String weekdays) {
}
