package com.razor.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aba Automacoes (V47): lista de espera automatica, campanhas e relatorio semanal. Endpoint e
 * tabela proprios, como o anti-falta.
 *
 * <p>Os modelos vem sempre preenchidos (o salvo ou o padrao). Salvar um modelo igual ao padrao —
 * ou vazio — volta a usar o padrao do sistema.
 *
 * @param services          servicos do catalogo com o intervalo de retorno (nulo = sem lembrete)
 * @param defaultTemplates  so leitura: os modelos padrao (para o botao "restaurar")
 * @param stats             so leitura: envios dos ultimos 30 dias por tipo e quem espera na lista
 */
public record AutomationSettingsPayload(
        @NotNull Boolean waitlistEnabled,
        @NotNull @Min(5) @Max(240) Integer waitlistWindowMinutes,
        @NotNull Boolean lapsedEnabled,
        @NotNull @Min(7) @Max(365) Integer lapsedDays,
        @Size(max = 1000) String lapsedTemplate,
        @NotNull Boolean birthdayEnabled,
        @Size(max = 1000) String birthdayTemplate,
        @NotNull @DecimalMin("1") @DecimalMax("100") BigDecimal birthdayDiscountPercent,
        @NotNull @Min(1) @Max(90) Integer birthdayCouponValidDays,
        @NotNull Boolean returnEnabled,
        @Size(max = 1000) String returnTemplate,
        @NotNull @Min(1) @Max(60) Integer frequencyCapDays,
        @NotNull Boolean weeklyReportEnabled,
        @NotNull @Min(1) @Max(7) Integer weeklyReportDay,
        @NotNull @Min(0) @Max(23) Integer weeklyReportHour,
        @Valid List<ServiceReturn> services,
        Map<String, String> defaultTemplates,
        Stats stats) {

    /** Intervalo de retorno de um servico; {@code returnReminderDays} nulo = sem lembrete. */
    public record ServiceReturn(@NotNull UUID id, String name, @Min(7) @Max(180) Integer returnReminderDays) {
    }

    public record Stats(long lapsedSent30d, long birthdaySent30d, long returnSent30d, long waitlistWaiting) {
    }

    /** Previa de uma campanha: tipo (LAPSED/BIRTHDAY/RETURN) e o texto ainda nao salvo. */
    public record PreviewRequest(@NotNull String kind, @Size(max = 1000) String template) {
    }

    public record PreviewResponse(String text) {
    }
}
