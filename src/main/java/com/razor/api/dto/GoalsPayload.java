package com.razor.api.dto;

import com.razor.api.model.BarbershopSettings;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Metas do dono (V19). Hoje so a meta de faturamento mensal; o record existe separado do
 * {@code BotSettingsPayload} porque aquele PUT troca o registro inteiro e zeraria a meta.
 *
 * <p>{@code null} = sem meta definida (o painel omite o card de progresso). Zero ou negativo
 * e recusado: o progresso nao teria significado.
 */
public record GoalsPayload(@Positive BigDecimal monthlyRevenueGoal) {

    public static GoalsPayload from(BarbershopSettings s) {
        return new GoalsPayload(s == null ? null : s.getMonthlyRevenueGoal());
    }
}
