package com.razor.api.dto;

import com.razor.api.model.BarbershopSettings;

/**
 * Tudo que o dono configura sobre o que o bot diz e usa: identidade, pagamento (PIX),
 * funcionamento, textos das mensagens automaticas e a persona da IA. Todos os campos sao
 * opcionais — vazio faz cair no padrao do sistema.
 */
public record BotSettingsPayload(
        // Identidade
        String displayName,
        // Localizacao e vitrine
        String addressText,
        String addressMapsUrl,
        String servicesText,
        String scheduleUrl,
        String instagramUrl,
        // Pagamento
        String pixKey,
        String pixCopyPaste,
        String paymentInfo,
        // Funcionamento
        String openingTime,
        String closingTime,
        String workingDays,
        // Mensagens automaticas
        String welcomeText,
        String reminderText,
        String reviewText,
        String fidelityRewardText,
        String cancellationPolicy,
        // IA
        String botPersona) {

    /** Payload só com os textos históricos (demais campos nulos) — util em testes/clientes simples. */
    public static BotSettingsPayload ofTexts(String addressText, String addressMapsUrl, String servicesText,
                                             String scheduleUrl, String instagramUrl) {
        return new BotSettingsPayload(null, addressText, addressMapsUrl, servicesText, scheduleUrl, instagramUrl,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public static BotSettingsPayload from(BarbershopSettings s) {
        return new BotSettingsPayload(
                s.getDisplayName(),
                s.getAddressText(), s.getAddressMapsUrl(), s.getServicesText(),
                s.getScheduleUrl(), s.getInstagramUrl(),
                s.getPixKey(), s.getPixCopyPaste(), s.getPaymentInfo(),
                s.getOpeningTime(), s.getClosingTime(), s.getWorkingDays(),
                s.getWelcomeText(), s.getReminderText(), s.getReviewText(),
                s.getFidelityRewardText(), s.getCancellationPolicy(),
                s.getBotPersona());
    }
}
