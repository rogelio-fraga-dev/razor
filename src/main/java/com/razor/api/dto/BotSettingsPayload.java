package com.razor.api.dto;

import com.razor.api.model.BarbershopSettings;
import com.razor.api.util.WorkingDays;
import java.util.Set;

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
        /**
         * Dias abertos (V32), como <b>numeros ISO</b>: 1=segunda … 7=domingo.
         *
         * <p>Inteiro e nao {@code DayOfWeek} de proposito. O Jackson desserializa numero para enum
         * por <b>ordinal</b> (0=MONDAY), entao um payload com {@code [1,2,3,4,5,6]} — Seg a Sab em
         * ISO — virava silenciosamente Ter a Dom, e {@code 7} estourava a faixa com 400. Erro de
         * um dia que ninguem notaria ate o bot recusar segunda-feira.
         *
         * <p>Nulo ou vazio <b>preserva</b> o que ja estava gravado, em vez de fechar a barbearia
         * todo dia: cliente antigo da API nao pode zerar o expediente por omitir um campo que
         * nao conhece.
         */
        Set<Integer> workingWeekdays,
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
                s.getOpeningTime(), s.getClosingTime(), WorkingDays.toIso(WorkingDays.parse(s.getWorkingWeekdays())),
                s.getWelcomeText(), s.getReminderText(), s.getReviewText(),
                s.getFidelityRewardText(), s.getCancellationPolicy(),
                s.getBotPersona());
    }
}
