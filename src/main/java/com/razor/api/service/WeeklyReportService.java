package com.razor.api.service;

import com.razor.api.dto.AiImpactResponse;
import com.razor.api.model.Appointment;
import com.razor.api.model.AttendanceStatus;
import com.razor.api.model.AutomationSend;
import com.razor.api.model.AutomationSettings;
import com.razor.api.model.Professional;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.AutomationSendRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Relatorio semanal do dono no WhatsApp (V47): faturamento da semana contra a anterior,
 * atendimentos, faltas, melhor barbeiro, horarios mais vazios da proxima semana e o que a IA fez.
 *
 * <p>"Semana" = os 7 dias que terminam ontem (enviado na segunda, cobre segunda a domingo). Precos
 * e duracoes pela funcao unica do catalogo ({@link PricingService#quoter()}); faturamento pelo
 * mesmo {@link ReportService#financialSummary} do painel, para os numeros baterem.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportService {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final ReportService reportService;
    private final AiImpactService aiImpactService;
    private final AppointmentRepository appointmentRepository;
    private final ProfessionalRepository professionalRepository;
    private final PricingService pricingService;
    private final BarbershopSettingsService barbershopSettingsService;
    private final AutomationSettingsService automationSettingsService;
    private final AutomationSendRepository sendRepository;
    private final TenantAdminService tenantAdminService;
    private final WhatsAppMessenger messenger;

    /** Faixa do dia para o "horarios vazios": rotulo e intervalo [inicio, fim). */
    record Faixa(String label, LocalTime start, LocalTime end) {
    }

    static final List<Faixa> FAIXAS = List.of(
            new Faixa("manhã", LocalTime.of(0, 0), LocalTime.of(12, 0)),
            new Faixa("tarde", LocalTime.of(12, 0), LocalTime.of(18, 0)),
            new Faixa("noite", LocalTime.of(18, 0), LocalTime.MAX));

    /** Um pedaco da proxima semana e quanto dele ja esta vendido. */
    record Ocupacao(LocalDate date, String faixa, int capacityMinutes, int bookedMinutes) {
        double rate() {
            return capacityMinutes <= 0 ? 1 : (double) bookedMinutes / capacityMinutes;
        }
    }

    // ------------------------------------------------------------------ texto

    /** O relatorio do tenant atual, para a semana que terminou ontem. */
    public String build(LocalDate today) {
        LocalDate from = today.minusDays(7);
        LocalDate to = today.minusDays(1);
        UUID shop = TenantContext.getCurrent();
        var bot = barbershopSettingsService.resolve();
        PricingService.Quoter quoter = pricingService.quoter();

        BigDecimal receita = reportService.financialSummary(from, to).totalRevenue();
        BigDecimal anterior = reportService.financialSummary(from.minusDays(7), to.minusDays(7)).totalRevenue();

        List<Appointment> semana = appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(shop, from, to);
        int faltas = 0;
        Map<UUID, BigDecimal> porBarbeiro = new HashMap<>();
        Map<UUID, Integer> atendBarbeiro = new HashMap<>();
        int atendimentos = 0;
        for (Appointment a : semana) {
            if (a.getAttendanceStatus() == AttendanceStatus.NO_SHOW) {
                faltas++;
                continue;
            }
            atendimentos++;
            if (a.getProfessionalId() != null) {
                BigDecimal preco = quoter.price(a.getService());
                porBarbeiro.merge(a.getProfessionalId(), preco == null ? BigDecimal.ZERO : preco, BigDecimal::add);
                atendBarbeiro.merge(a.getProfessionalId(), 1, Integer::sum);
            }
        }

        List<Professional> profs = professionalRepository.findByBarbershopId(shop);
        Map<UUID, String> nomes = new HashMap<>();
        for (Professional p : profs) nomes.put(p.getId(), p.displayName());
        String melhor = porBarbeiro.entrySet().stream()
                .max(Map.Entry.<UUID, BigDecimal>comparingByValue()
                        .thenComparing(e -> atendBarbeiro.getOrDefault(e.getKey(), 0)))
                .map(e -> nomes.getOrDefault(e.getKey(), "—") + " — " + atendBarbeiro.getOrDefault(e.getKey(), 0)
                        + " atendimento(s), " + brl(e.getValue()))
                .orElse(null);

        int ativos = (int) profs.stream().filter(Professional::isActive).count();
        List<Ocupacao> vazios = emptiest(appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(
                shop, today, today.plusDays(6)), today, bot.workingWeekdays(),
                parse(bot.openingTime(), LocalTime.of(9, 0)), parse(bot.closingTime(), LocalTime.of(19, 0)),
                Math.max(1, ativos), quoter);

        AiImpactResponse ia = aiImpactService.impact(from, to);

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Resumo da semana — ").append(bot.displayName()).append("*\n")
          .append("_").append(AppointmentDates.formatDate(from)).append(" a ").append(AppointmentDates.formatDate(to))
          .append("_\n\n");
        sb.append("💰 *Faturamento:* ").append(brl(receita)).append(variacao(receita, anterior)).append("\n")
          .append("   _semana anterior: ").append(brl(anterior)).append("_\n");
        sb.append("✂️ *Atendimentos:* ").append(atendimentos).append("\n");
        sb.append("🚫 *Faltas:* ").append(faltas).append("\n");
        if (melhor != null) sb.append("🏆 *Destaque:* ").append(melhor).append("\n");

        sb.append("\n🤖 *O que a IA fez*\n")
          .append("• Marcou ").append(ia.bot().appointments()).append(" horário(s) (").append(brl(ia.bot().revenue()))
          .append(")\n")
          .append("• Respondeu ").append(ia.botMessagesOutsideHours()).append(" mensagem(ns) fora do expediente\n");
        if (ia.waitlistRefills() > 0) {
            sb.append("• Encaixou ").append(ia.waitlistRefills()).append(" vaga(s) da lista de espera\n");
        }
        if (ia.noShowsAvoided() > 0) {
            sb.append("• Evitou ").append(ia.noShowsAvoided()).append(" falta(s) com a confirmação\n");
        }

        if (!vazios.isEmpty()) {
            sb.append("\n📅 *Horários mais vazios (próximos 7 dias)*\n");
            for (Ocupacao o : vazios) {
                String dia = o.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "");
                sb.append("• ").append(Character.toUpperCase(dia.charAt(0))).append(dia.substring(1)).append(" ")
                  .append(AppointmentDates.formatDate(o.date())).append(" · ").append(o.faixa()).append(" — ")
                  .append(Math.round(o.rate() * 100)).append("% ocupado\n");
            }
            sb.append("_Boa hora para uma promoção ou para lembrar os clientes sumidos._");
        }
        return sb.toString().trim();
    }

    /**
     * As 3 faixas (dia + manha/tarde/noite) menos ocupadas dos proximos 7 dias, so em dia aberto e
     * dentro do expediente. Capacidade = barbeiros ativos x minutos da faixa; ocupado = duracao do
     * servico de cada agendamento (faltas fora). Faixa ja passada de hoje nao conta.
     */
    static List<Ocupacao> emptiest(List<Appointment> proximos, LocalDate today, Set<DayOfWeek> openDays,
                                   LocalTime open, LocalTime close, int chairs, PricingService.Quoter quoter) {
        List<Ocupacao> out = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = today.plusDays(i);
            if (openDays != null && !openDays.contains(d.getDayOfWeek())) continue;
            for (Faixa f : FAIXAS) {
                LocalTime ini = f.start().isBefore(open) ? open : f.start();
                LocalTime fim = f.end().isAfter(close) ? close : f.end();
                if (!ini.isBefore(fim)) continue;
                if (i == 0 && LocalTime.now(AppointmentDates.ZONE).isAfter(fim)) continue;
                int capacidade = (int) java.time.Duration.between(ini, fim).toMinutes() * chairs;
                if (capacidade < 60) continue;
                int ocupado = 0;
                for (Appointment a : proximos) {
                    if (!d.equals(a.getAppointmentDate()) || a.getAppointmentTime() == null) continue;
                    if (a.getAttendanceStatus() == AttendanceStatus.NO_SHOW) continue;
                    if (a.getAppointmentTime().isBefore(ini) || !a.getAppointmentTime().isBefore(fim)) continue;
                    ocupado += Math.max(30, quoter.duration(a.getService()));
                }
                out.add(new Ocupacao(d, f.label(), capacidade, Math.min(ocupado, capacidade)));
            }
        }
        out.sort(Comparator.comparingDouble(Ocupacao::rate).thenComparing(Ocupacao::date));
        return out.size() > 3 ? new ArrayList<>(out.subList(0, 3)) : out;
    }

    static String variacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.signum() == 0) return "";
        BigDecimal pct = atual.subtract(anterior).multiply(BigDecimal.valueOf(100))
                .divide(anterior, 0, RoundingMode.HALF_UP);
        int p = pct.intValue();
        if (p == 0) return " (estável)";
        return p > 0 ? " (▲ " + p + "%)" : " (▼ " + Math.abs(p) + "%)";
    }

    private static String brl(BigDecimal v) {
        return String.format(PT_BR, "R$ %,.2f", v == null ? BigDecimal.ZERO : v);
    }

    private static LocalTime parse(String hhmm, LocalTime fallback) {
        try {
            return hhmm == null ? fallback : LocalTime.parse(hhmm.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ envio (scheduler)

    /** Chegou o dia/hora configurados? (Depois da hora, no mesmo dia: servidor parado as 8h ainda manda.) */
    static boolean isDue(AutomationSettings cfg, LocalDateTime now) {
        return cfg.isWeeklyReportEnabled()
                && now.getDayOfWeek().getValue() == cfg.getWeeklyReportDay()
                && now.getHour() >= cfg.getWeeklyReportHour();
    }

    static String weekKey(LocalDate d) {
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /**
     * Envia o relatorio aos admins do tenant atual, se for a hora — uma vez por semana por numero
     * (chave em {@code automation_sends}, sobrevive a restart).
     *
     * @return quantos admins receberam agora
     */
    public int sendIfDue(LocalDateTime now) {
        UUID shop = TenantContext.getCurrent();
        AutomationSettings cfg = automationSettingsService.getFor(shop);
        if (!isDue(cfg, now)) return 0;
        List<String> admins = new ArrayList<>(new LinkedHashSet<>(tenantAdminService.adminPhones()));
        if (admins.isEmpty()) return 0;
        String semana = weekKey(now.toLocalDate());
        String texto = null;
        int enviados = 0;
        for (String phone : admins) {
            var existing = sendRepository.findByBarbershopIdAndKindAndCustomerPhoneAndReferenceKey(
                    shop, AutomationSend.Kind.WEEKLY_REPORT, phone, semana);
            if (existing.isPresent() && existing.get().isSettled()) continue;
            AutomationSend row = existing.orElseGet(() -> AutomationSend.builder()
                    .barbershopId(shop).kind(AutomationSend.Kind.WEEKLY_REPORT)
                    .customerPhone(phone).referenceKey(semana).build());
            row.setStatus(AutomationSend.Status.PENDING);
            row.setAttempts(row.getAttempts() + 1);
            try {
                row = sendRepository.save(row);
            } catch (org.springframework.dao.DataIntegrityViolationException dup) {
                continue;
            }
            try {
                if (texto == null) texto = build(now.toLocalDate());
                messenger.sendSync(phone, texto, "ADMIN");
                row.setStatus(AutomationSend.Status.SENT);
                row.setSentAt(LocalDateTime.now(AppointmentDates.ZONE));
                enviados++;
            } catch (Exception e) {
                row.setStatus(AutomationSend.Status.FAILED);
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                row.setLastError(msg.length() > 250 ? msg.substring(0, 250) : msg);
                log.warn("[RELATORIO SEMANAL] Falha ao enviar para {}: {}", phone, msg);
            }
            sendRepository.save(row);
        }
        if (enviados > 0) log.info("[RELATORIO SEMANAL] Enviado a {} admin(s) do tenant {}", enviados, shop);
        return enviados;
    }
}
