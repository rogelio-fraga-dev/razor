package com.razor.api.service;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.BarbershopSettings;
import com.razor.api.repository.BarbershopSettingsRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.WorkingDays;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Configuracoes do bot por tenant (textos/links do WhatsApp). {@link #resolve()} devolve os
 * valores efetivos — linha do tenant com <b>fallback por campo</b> no config global
 * ({@code razor.menu.*}) —, garantindo que o tenant padrao (sem linha) siga como antes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BarbershopSettingsService {

    private final BarbershopSettingsRepository repository;
    private final RazorProperties properties;

    /** Nome usado quando o tenant nao definiu um (mantem o comportamento historico). */
    public static final String DEFAULT_DISPLAY_NAME = "Barbearia Ficticia";
    private static final String DEFAULT_OPENING = "09:00";
    private static final String DEFAULT_CLOSING = "19:00";

    /** Textos efetivos usados no fluxo do cliente (linha do tenant com fallback por campo). */
    public record BotSettings(
            String addressText, String addressMapsUrl, String servicesText, String scheduleUrl, String instagramUrl,
            String displayName, String pixKey, String pixCopyPaste, String paymentInfo,
            String openingTime, String closingTime, Set<DayOfWeek> workingWeekdays,
            String welcomeText, String reminderText, String reviewText,
            String fidelityRewardText, String cancellationPolicy, String botPersona) {

        /**
         * Atalho para os 5 textos historicos, com o restante nos padroes do sistema —
         * evita que cada novo campo quebre quem so precisa dos textos basicos.
         */
        public BotSettings(String addressText, String addressMapsUrl, String servicesText,
                           String scheduleUrl, String instagramUrl) {
            this(addressText, addressMapsUrl, servicesText, scheduleUrl, instagramUrl,
                    DEFAULT_DISPLAY_NAME, null, null, null,
                    DEFAULT_OPENING, DEFAULT_CLOSING, WorkingDays.DEFAULT,
                    null, null, null, null, null, null);
        }

        /** True quando o tenant configurou a propria chave PIX (senao nao devemos enviar PIX). */
        public boolean hasPix() {
            return pixKey != null && !pixKey.isBlank();
        }

        /** A barbearia abre neste dia? Usado para nao oferecer horario em dia fechado (V32). */
        public boolean isOpenOn(DayOfWeek day) {
            return workingWeekdays.contains(day);
        }

        /**
         * Frase dos dias de funcionamento ("Seg a Sáb"), <b>derivada</b> dos dias estruturados.
         * Antes era um texto livre a parte, que podia dizer uma coisa enquanto a agenda fazia
         * outra — foi exatamente o que aconteceu ate a V32.
         */
        public String workingDaysText() {
            return WorkingDays.describe(workingWeekdays);
        }
    }

    public BotSettings resolve() {
        return resolveFor(TenantContext.getCurrent());
    }

    /**
     * As mesmas configuracoes, para uma barbearia informada explicitamente.
     *
     * <p>Existe para os caminhos <b>sem tenant no contexto</b> — hoje o site white-label, que e
     * publico e resolve a barbearia pelo slug da URL. Deixar esses caminhos chamarem
     * {@code resolve()} os faria depender de um contexto que nao existe ali: com o
     * {@code TenantContext} fail-closed (V30) isso estoura, o que ja e melhor que devolver o
     * tenant errado — mas o certo e passar o id.
     */
    public BotSettings resolveFor(java.util.UUID barbershopId) {
        RazorProperties.Menu menu = properties.getMenu();
        BarbershopSettings s = repository.findById(barbershopId).orElse(null);
        return new BotSettings(
                pick(s == null ? null : s.getAddressText(), menu.getAddressText()),
                pick(s == null ? null : s.getAddressMapsUrl(), menu.getAddressMapsUrl()),
                pick(s == null ? null : s.getServicesText(), menu.getServicesText()),
                pick(s == null ? null : s.getScheduleUrl(), menu.getScheduleUrl()),
                pick(s == null ? null : s.getInstagramUrl(), menu.getInstagramUrl()),
                pick(s == null ? null : s.getDisplayName(), DEFAULT_DISPLAY_NAME),
                s == null ? null : s.getPixKey(),
                s == null ? null : s.getPixCopyPaste(),
                s == null ? null : s.getPaymentInfo(),
                pick(s == null ? null : s.getOpeningTime(), DEFAULT_OPENING),
                pick(s == null ? null : s.getClosingTime(), DEFAULT_CLOSING),
                WorkingDays.parse(s == null ? null : s.getWorkingWeekdays()),
                s == null ? null : s.getWelcomeText(),
                s == null ? null : s.getReminderText(),
                s == null ? null : s.getReviewText(),
                s == null ? null : s.getFidelityRewardText(),
                s == null ? null : s.getCancellationPolicy(),
                s == null ? null : s.getBotPersona());
    }

    /** Linha do tenant para edicao (ou uma vazia, sem persistir, quando ainda nao existe). */
    public BarbershopSettings getOrEmpty() {
        return repository.findById(TenantContext.getCurrent())
                .orElseGet(() -> BarbershopSettings.builder().barbershopId(TenantContext.getCurrent()).build());
    }

    /** Salva o pacote completo de configuracoes do bot (payload do painel). */
    public BarbershopSettings update(com.razor.api.dto.BotSettingsPayload p) {
        BarbershopSettings s = repository.findById(TenantContext.getCurrent())
                .orElseGet(() -> BarbershopSettings.builder().barbershopId(TenantContext.getCurrent()).build());
        s.setDisplayName(p.displayName());
        s.setAddressText(p.addressText());
        s.setAddressMapsUrl(p.addressMapsUrl());
        s.setServicesText(p.servicesText());
        s.setScheduleUrl(p.scheduleUrl());
        s.setInstagramUrl(p.instagramUrl());
        s.setPixKey(p.pixKey());
        s.setPixCopyPaste(p.pixCopyPaste());
        s.setPaymentInfo(p.paymentInfo());
        s.setOpeningTime(p.openingTime());
        s.setClosingTime(p.closingTime());
        // Payload sem os dias (cliente antigo da API) nao pode zerar o expediente: manter o que
        // ja estava e mais seguro do que gravar "fechado todo dia" por omissao.
        Set<DayOfWeek> dias = WorkingDays.fromIso(p.workingWeekdays());
        if (!dias.isEmpty()) {
            s.setWorkingWeekdays(WorkingDays.format(dias));
        }
        s.setWelcomeText(p.welcomeText());
        s.setReminderText(p.reminderText());
        s.setReviewText(p.reviewText());
        s.setFidelityRewardText(p.fidelityRewardText());
        s.setCancellationPolicy(p.cancellationPolicy());
        s.setBotPersona(p.botPersona());
        BarbershopSettings saved = repository.save(s);
        log.info("[BOT] Configuracoes do bot atualizadas para o tenant {}", TenantContext.getCurrent());
        return saved;
    }

    /** Meta de faturamento mensal definida pelo dono (V19). {@code null} = sem meta. */
    public BigDecimal getMonthlyRevenueGoal() {
        return repository.findById(TenantContext.getCurrent())
                .map(BarbershopSettings::getMonthlyRevenueGoal)
                .orElse(null);
    }

    /**
     * Define (ou limpa, com {@code null}) a meta de faturamento do mes. Meta zero ou negativa
     * nao tem significado no calculo de progresso e e tratada como "sem meta".
     */
    public BigDecimal setMonthlyRevenueGoal(BigDecimal goal) {
        BigDecimal valid = (goal == null || goal.signum() <= 0) ? null : goal.setScale(2, RoundingMode.HALF_UP);
        BarbershopSettings s = repository.findById(TenantContext.getCurrent())
                .orElseGet(() -> BarbershopSettings.builder().barbershopId(TenantContext.getCurrent()).build());
        s.setMonthlyRevenueGoal(valid);
        repository.save(s);
        log.info("[METAS] Meta mensal do tenant {} definida como {}", TenantContext.getCurrent(), valid);
        return valid;
    }

    /**
     * Limite de horarios futuros por cliente (V36) da barbearia informada. {@code null} = sem
     * limite. Sem linha de configuracao vale o default da coluna (2), o mesmo de quem tem linha
     * e nunca mexeu. Recebe o id explicito porque o agendamento online (site publico) nao tem
     * tenant no contexto.
     */
    public Integer getMaxFutureBookingsPerCustomer(java.util.UUID barbershopId) {
        return repository.findById(barbershopId)
                .map(BarbershopSettings::getMaxFutureBookingsPerCustomer)
                .orElse(BarbershopSettings.DEFAULT_MAX_FUTURE_BOOKINGS);
    }

    /** Mesmo limite, para o tenant atual. */
    public Integer getMaxFutureBookingsPerCustomer() {
        return getMaxFutureBookingsPerCustomer(TenantContext.getCurrent());
    }

    /**
     * Define o limite de horarios futuros por cliente. {@code null} (ou zero/negativo, que nao
     * significam nada) = sem limite.
     */
    public Integer setMaxFutureBookingsPerCustomer(Integer limit) {
        Integer valid = (limit == null || limit <= 0) ? null : limit;
        BarbershopSettings s = repository.findById(TenantContext.getCurrent())
                .orElseGet(() -> BarbershopSettings.builder().barbershopId(TenantContext.getCurrent()).build());
        s.setMaxFutureBookingsPerCustomer(valid);
        repository.save(s);
        log.info("[AGENDA] Limite de agendamentos por cliente do tenant {} definido como {}",
                TenantContext.getCurrent(), valid == null ? "sem limite" : valid);
        return valid;
    }

    /**
     * Regras do anti-falta (V42) ja com os defaults aplicados.
     *
     * @param depositAfter  faltas (180 dias) a partir das quais o bot exige sinal; null = desligado
     * @param depositAmount valor do sinal; null = preco do servico
     */
    public record NoShowSettings(boolean confirmationEnabled, int confirmationHour, int deadlineHours,
                                 boolean autoRelease, Integer depositAfter, BigDecimal depositAmount) {
        public static final NoShowSettings DEFAULTS = new NoShowSettings(true,
                BarbershopSettings.DEFAULT_CONFIRMATION_HOUR, BarbershopSettings.DEFAULT_CONFIRMATION_DEADLINE_HOURS,
                false, null, null);
    }

    public NoShowSettings getNoShowSettings() {
        return repository.findById(TenantContext.getCurrent())
                .map(s -> new NoShowSettings(
                        !Boolean.FALSE.equals(s.getConfirmationEnabled()),
                        s.getConfirmationHour() != null ? s.getConfirmationHour() : BarbershopSettings.DEFAULT_CONFIRMATION_HOUR,
                        s.getConfirmationDeadlineHours() != null ? s.getConfirmationDeadlineHours()
                                : BarbershopSettings.DEFAULT_CONFIRMATION_DEADLINE_HOURS,
                        Boolean.TRUE.equals(s.getConfirmationAutoRelease()),
                        s.getNoShowDepositAfter(),
                        s.getNoShowDepositAmount()))
                .orElse(NoShowSettings.DEFAULTS);
    }

    public NoShowSettings updateNoShowSettings(com.razor.api.dto.NoShowSettingsPayload p) {
        BarbershopSettings s = repository.findById(TenantContext.getCurrent())
                .orElseGet(() -> BarbershopSettings.builder().barbershopId(TenantContext.getCurrent()).build());
        s.setConfirmationEnabled(p.confirmationEnabled());
        s.setConfirmationHour(p.confirmationHour());
        s.setConfirmationDeadlineHours(p.confirmationDeadlineHours());
        s.setConfirmationAutoRelease(p.autoReleaseUnconfirmed());
        s.setNoShowDepositAfter(p.noShowDepositAfter() == null || p.noShowDepositAfter() <= 0 ? null : p.noShowDepositAfter());
        s.setNoShowDepositAmount(p.noShowDepositAmount() == null || p.noShowDepositAmount().signum() <= 0
                ? null : p.noShowDepositAmount().setScale(2, RoundingMode.HALF_UP));
        repository.save(s);
        log.info("[ANTI-FALTA] Configuracoes atualizadas para o tenant {}", TenantContext.getCurrent());
        return getNoShowSettings();
    }

    /** O bot atende clientes neste tenant? Sem linha (tenant novo) = ligado. */
    public boolean isBotEnabled() {
        return repository.findById(TenantContext.getCurrent())
                .map(s -> !Boolean.FALSE.equals(s.getBotEnabled()))
                .orElse(true);
    }

    /** Liga/desliga o atendimento automatico de clientes (controle do dono no painel). */
    public boolean setBotEnabled(boolean enabled) {
        BarbershopSettings s = repository.findById(TenantContext.getCurrent())
                .orElseGet(() -> BarbershopSettings.builder().barbershopId(TenantContext.getCurrent()).build());
        s.setBotEnabled(enabled);
        repository.save(s);
        log.info("[BOT] Bot {} para o tenant {}", enabled ? "ATIVADO" : "DESATIVADO", TenantContext.getCurrent());
        return enabled;
    }

    private String pick(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
