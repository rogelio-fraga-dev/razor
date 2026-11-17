package com.razor.api.service;

import com.razor.api.dto.FinancialSummaryResponse;
import com.razor.api.model.Appointment;
import com.razor.api.model.PaymentStatus;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Relatorios de agenda em texto para o WhatsApp (dono/admin e scheduler): agenda de hoje e da
 * semana, painel de agendamentos e extratos financeiros do dia/semana.
 *
 * <p>Somente leitura. Os numeros financeiros vem do {@link ReportService} — a mesma fonte do
 * painel web — para o WhatsApp nunca divergir do painel. Extraido do antigo {@link AgendaService}.
 */
@Service
@RequiredArgsConstructor
public class AgendaReportService {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final AppointmentRepository appointmentRepository;
    private final ReportService reportService;

    public String getAgendaSalva() {
        List<Appointment> agendaAtiva = appointmentRepository.findByBarbershopId(TenantContext.getCurrent());
        if (agendaAtiva.isEmpty()) return "Nenhum agendamento registrado no sistema.";

        List<String> display = new ArrayList<>();
        for (Appointment ag : agendaAtiva) {
            display.add("📅 " + AppointmentDates.formatDate(ag.getAppointmentDate()) + " às ⏰ " + AppointmentDates.formatTime(ag.getAppointmentTime()) + " - " + ag.getCustomerName() + " | " + ag.getService());
        }
        return String.join("\n", display);
    }

    public int getAgendamentosHojeCount() {
        LocalDate hoje = AppointmentDates.today();
        List<Appointment> agendaAtiva = appointmentRepository.findByBarbershopId(TenantContext.getCurrent());
        return (int) agendaAtiva.stream().filter(a -> hoje.equals(a.getAppointmentDate())).count();
    }

    public String getAgendaHojeText() {
        LocalDate hoje = AppointmentDates.today();
        List<Appointment> apps = appointmentRepository.findByBarbershopId(TenantContext.getCurrent()).stream()
            .filter(a -> hoje.equals(a.getAppointmentDate()))
            .collect(Collectors.toList());

        if (apps.isEmpty()) return "Nenhum agendamento para hoje.";

        StringBuilder sb = new StringBuilder();
        for (Appointment ag : apps) {
            sb.append("⏰ ").append(AppointmentDates.formatTime(ag.getAppointmentTime())).append(" - ").append(ag.getCustomerName()).append(" | ").append(ag.getService()).append("\n");
        }
        return sb.toString().trim();
    }

    public String getPainelAgendamentosDetalhado(long totalBase, long waitlistCount, long pausedCount) {
        LocalDate hoje = AppointmentDates.today();
        String dataHoje = AppointmentDates.formatDate(hoje);

        List<Appointment> agendaAtiva = appointmentRepository.findByBarbershopId(TenantContext.getCurrent());
        List<Appointment> agendaHoje = new ArrayList<>();
        for (Appointment ag : agendaAtiva) {
            if (hoje.equals(ag.getAppointmentDate())) {
                agendaHoje.add(ag);
            }
        }

        // Ordenação Cronológica (Horário)
        agendaHoje.sort(AgendaReportService::compareByTime);

        List<String> list = new ArrayList<>();
        for (Appointment ag : agendaHoje) {
            String badge = "";
            if (ag.getPaymentStatus() == PaymentStatus.PIX) badge = " 🟢 [PIX]";
            else if (ag.getPaymentStatus() == PaymentStatus.PRESENCIAL) badge = " 🔵 [Local]";
            else badge = " 🟡 [Pend]";

            list.add(String.format("⏰ *%s* | %s (%s)%s", AppointmentDates.formatTime(ag.getAppointmentTime()), ag.getCustomerName(), ag.getService(), badge));
        }

        String agendaStr = list.isEmpty() ? "_Nenhum agendamento para hoje até o momento._" : String.join("\n", list);

        return String.format("""
                📅 *PAINEL DE AGENDAMENTOS*
                🗓️ Data: *%s*
                ━━━━━━━━━━━━━━━━━━━━━━
                👥 *Métricas da Base*
                ├ Clientes Cadastrados: *%d*
                ├ Fila de Espera: *%d*
                └ Em Atendimento Humano: *%d*

                📋 *Agendamentos de Hoje (%d)*
                ━━━━━━━━━━━━━━━━━━━━━━
                %s
                ━━━━━━━━━━━━━━━━━━━━━━
                """,
                dataHoje, totalBase, waitlistCount, pausedCount, agendaHoje.size(), agendaStr);
    }

    public String getAgendaSemanaText() {
        LocalDate hoje = AppointmentDates.today();
        LocalDate inicioSemana = hoje.with(DayOfWeek.MONDAY);
        LocalDate fimSemana = hoje.with(DayOfWeek.SUNDAY);

        Map<LocalDate, List<Appointment>> porDia = new TreeMap<>();

        List<Appointment> agendaAtiva = appointmentRepository.findByBarbershopId(TenantContext.getCurrent());
        for (Appointment ag : agendaAtiva) {
            LocalDate agDate = ag.getAppointmentDate();
            if (agDate == null) continue;
            if (!agDate.isBefore(inicioSemana) && !agDate.isAfter(fimSemana)) {
                porDia.computeIfAbsent(agDate, k -> new ArrayList<>()).add(ag);
            }
        }

        if (porDia.isEmpty()) {
            return "_Nenhum agendamento para esta semana._";
        }

        StringBuilder sb = new StringBuilder("📆 *AGENDA DA SEMANA*\n\n");
        for (Map.Entry<LocalDate, List<Appointment>> entry : porDia.entrySet()) {
            LocalDate dia = entry.getKey();
            String diaSemana = dia.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("pt", "BR"));
            String title = AppointmentDates.formatDate(dia) + " - " + diaSemana.substring(0, 1).toUpperCase() + diaSemana.substring(1);
            sb.append("📅 *").append(title).append("*\n");

            List<Appointment> appsDia = entry.getValue();
            appsDia.sort(AgendaReportService::compareByTime);

            for (Appointment ag : appsDia) {
                String badge = "";
                if (ag.getPaymentStatus() == PaymentStatus.PIX) badge = " [PIX]";
                else if (ag.getPaymentStatus() == PaymentStatus.PRESENCIAL) badge = " [Local]";
                else badge = " [Pend]";
                sb.append("  ⏰ ").append(AppointmentDates.formatTime(ag.getAppointmentTime())).append(" | ").append(ag.getCustomerName()).append(" (").append(ag.getService()).append(")").append(badge).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    public String getResumoDoDia() {
        LocalDate hoje = AppointmentDates.today();
        String dataHoje = AppointmentDates.formatDate(hoje);
        // Mesma apuração do painel, em BigDecimal — ver o extrato financeiro abaixo.
        FinancialSummaryResponse resumo = reportService.financialSummary(hoje, hoje);
        return String.format(PT_BR,
                "📈 *Fechamento de %s*\n\n✂️ Cortes realizados hoje: %d\n💰 Faturamento estimado: R$ %.2f",
                dataHoje, resumo.totalAppointments(), resumo.totalRevenue());
    }

    public String getResumoFinanceiroDetalhado() {
        LocalDate hoje = AppointmentDates.today();
        String dataHoje = AppointmentDates.formatDate(hoje);

        // Uma fonte de verdade só: o mesmo cálculo que o painel usa. Esta conta era refeita aqui
        // em `double`, então o resumo do WhatsApp podia divergir do painel por arredondamento de
        // ponto flutuante — dois números diferentes para o mesmo dia, e o dono sem saber qual crer.
        FinancialSummaryResponse resumo = reportService.financialSummary(hoje, hoje);

        int totalCortes = resumo.totalAppointments();
        int totalCombo = resumo.categories().combo();
        int totalBarba = resumo.categories().barba();
        int totalSimples = resumo.categories().simples();

        BigDecimal faturamentoTotal = resumo.totalRevenue();
        BigDecimal pixVal = resumo.payments().pix().value();
        BigDecimal presencialVal = resumo.payments().presencial().value();
        BigDecimal pendenteVal = resumo.payments().pendente().value();

        int pixCount = resumo.payments().pix().count();
        int presencialCount = resumo.payments().presencial().count();
        int pendenteCount = resumo.payments().pendente().count();

        BigDecimal ticketMedio = resumo.averageTicket();

        return String.format(PT_BR, """
                📊 *EXTRATO FINANCEIRO DO DIA*
                🗓️ Data: *%s*
                ━━━━━━━━━━━━━━━━━━━━━━

                ✂️ *VOLUME DE SERVIÇOS*
                Total Atendidos: *%d*
                ├ Combos (Cabelo+Barba): %d
                ├ Apenas Barba: %d
                └ Simples / Outros: %d

                💳 *FLUXO DE CAIXA*
                🟢 *PIX:* R$ %.2f (%d)
                🔵 *Presencial:* R$ %.2f (%d)
                🟡 *Pendente:* R$ %.2f (%d)

                ━━━━━━━━━━━━━━━━━━━━━━
                💰 *Faturamento Total:* R$ %.2f
                📈 *Ticket Médio:* R$ %.2f
                ━━━━━━━━━━━━━━━━━━━━━━
                """,
                dataHoje, totalCortes, totalCombo, totalBarba, totalSimples,
                pixVal, pixCount, presencialVal, presencialCount, pendenteVal, pendenteCount,
                faturamentoTotal, ticketMedio);
    }

    public String getResumoFinanceiroDaSemana() {
        LocalDate hoje = AppointmentDates.today();
        LocalDate inicioSemana = hoje.with(DayOfWeek.MONDAY);

        // Mesma fonte de verdade do painel — ver o extrato diário acima.
        FinancialSummaryResponse resumo = reportService.financialSummary(inicioSemana, hoje);

        int totalCortes = resumo.totalAppointments();
        int totalCombo = resumo.categories().combo();
        int totalBarba = resumo.categories().barba();
        int totalSimples = resumo.categories().simples();

        BigDecimal faturamentoTotal = resumo.totalRevenue();
        BigDecimal pixVal = resumo.payments().pix().value();
        BigDecimal presencialVal = resumo.payments().presencial().value();
        BigDecimal pendenteVal = resumo.payments().pendente().value();

        int pixCount = resumo.payments().pix().count();
        int presencialCount = resumo.payments().presencial().count();
        int pendenteCount = resumo.payments().pendente().count();
        BigDecimal ticketMedio = resumo.averageTicket();

        String periodo = AppointmentDates.formatDate(inicioSemana) + " até " + AppointmentDates.formatDate(hoje);

        return String.format(PT_BR, """
                📊 *RESUMO FINANCEIRO DA SEMANA*
                _Acumulado de %s (Até hoje)_

                ✂️ *Volume de Serviços Realizados:*
                - Total de Atendimentos: %d
                - Combos: %d
                - Apenas Barba: %d
                - Cortes Simples / Outros: %d

                💳 *Fluxo de Caixa por Meio de Pagamento:*
                - ✅ *PIX (Confirmado):* R$ %.2f (%d rec.)
                - 💵 *Presencial:* R$ %.2f (%d rec.)
                - ⏳ *Pendente:* R$ %.2f (%d agendamentos)

                📈 *Indicadores de Performance:*
                - Faturamento Parcial: *R$ %.2f*
                - Ticket Médio na Semana: *R$ %.2f*
                """,
                periodo, totalCortes, totalCombo, totalBarba, totalSimples,
                pixVal, pixCount, presencialVal, presencialCount, pendenteVal, pendenteCount,
                faturamentoTotal, ticketMedio);
    }

    /** Ordena agendamentos por horário, tolerando horários nulos (vão para o fim). */
    private static int compareByTime(Appointment a1, Appointment a2) {
        LocalTime t1 = a1.getAppointmentTime();
        LocalTime t2 = a2.getAppointmentTime();
        if (t1 == null && t2 == null) return 0;
        if (t1 == null) return 1;
        if (t2 == null) return -1;
        return t1.compareTo(t2);
    }
}
