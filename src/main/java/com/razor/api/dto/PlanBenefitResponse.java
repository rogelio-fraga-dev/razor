package com.razor.api.dto;

import com.razor.api.model.PlanBenefit;
import java.math.BigDecimal;
import java.util.UUID;

/** Um beneficio de plano como o painel consome. */
public record PlanBenefitResponse(
        UUID id,
        PlanBenefit.TargetType targetType,
        UUID targetId,
        BigDecimal discountPercent,
        Integer monthlyQuota,
        String weekdays,
        /** True quando zera o preco — o "GRATIS" da tela do cliente. */
        boolean free) {

    public static PlanBenefitResponse from(PlanBenefit b) {
        return new PlanBenefitResponse(b.getId(), b.getTargetType(), b.getTargetId(),
                b.getDiscountPercent(), b.getMonthlyQuota(), b.getWeekdays(), b.isFree());
    }
}
