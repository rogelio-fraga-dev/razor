package com.razor.api.dto;

import com.razor.api.model.SubscriptionPlan;
import java.math.BigDecimal;
import java.util.UUID;

/** Plano de assinatura para a API do painel. */
public record SubscriptionPlanResponse(
        UUID id, String name, BigDecimal price, String description, boolean active,
        /** Antecedencia de agendamento do assinante (V27); nulo = sem regra especial. */
        Integer bookingWindowDays) {

    public static SubscriptionPlanResponse from(SubscriptionPlan p) {
        return new SubscriptionPlanResponse(p.getId(), p.getName(), p.getPrice(), p.getDescription(),
                p.isActive(), p.getBookingWindowDays());
    }
}
