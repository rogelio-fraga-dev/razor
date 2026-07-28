package com.razor.api.controller;

import com.razor.api.dto.AppointmentView;
import com.razor.api.dto.CommissionReportResponse;
import com.razor.api.dto.DreResponse;
import com.razor.api.dto.FinancialSummaryResponse;
import com.razor.api.dto.OccupancyResponse;
import com.razor.api.dto.RetentionResponse;
import com.razor.api.dto.RevenuePoint;
import com.razor.api.service.CommissionService;
import com.razor.api.service.OccupancyService;
import com.razor.api.service.RetentionService;
import com.razor.api.service.ReportService;
import com.razor.api.util.AppointmentDates;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

/**
 * Relatorios estruturados (JSON) do painel web (tenant vem do JWT): comissoes, resumo financeiro
 * por periodo e agenda do dia.
 */
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final CommissionService commissionService;
    private final ReportService reportService;
    private final OccupancyService occupancyService;
    private final RetentionService retentionService;

    /** Comissoes por profissional num dia (padrao: hoje). Ex.: {@code /commissions?date=2026-07-21}. */
    @GetMapping("/commissions")
    public CommissionReportResponse commissions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : AppointmentDates.today();
        return CommissionReportResponse.of(target, commissionService.commissionsForDay(target));
    }

    /**
     * Resumo financeiro do periodo [from, to] (padrao: hoje..hoje). Ex.:
     * {@code /financial?from=2026-07-01&to=2026-07-21}.
     */
    @GetMapping("/financial")
    public FinancialSummaryResponse financial(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from != null ? from : AppointmentDates.today();
        LocalDate end = to != null ? to : AppointmentDates.today();
        return reportService.financialSummary(start, end);
    }

    /**
     * Serie de faturamento do grafico do painel, em uma unica chamada.
     * Ex.: {@code /revenue-series?period=LAST_7_DAYS}.
     */
    @GetMapping("/revenue-series")
    public List<RevenuePoint> revenueSeries(
            @RequestParam(required = false) ReportService.SeriesPeriod period) {
        ReportService.SeriesPeriod target = period != null ? period : ReportService.SeriesPeriod.LAST_7_DAYS;
        return reportService.revenueSeries(target, AppointmentDates.today());
    }

    /**
     * DRE do periodo [from, to] — padrao: o mes corrente inteiro, que e a pergunta real ("como
     * fechou o mes?"). Um dia so de DRE nao diz nada: a receita e diaria, mas o aluguel vence
     * uma vez.
     */
    @GetMapping("/dre")
    public DreResponse dre(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = AppointmentDates.today();
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : start.plusMonths(1).minusDays(1);
        return reportService.dre(start, end, today);
    }

    /**
     * Taxa de ocupacao das cadeiras no periodo (padrao: mes corrente ate hoje).
     *
     * <p>Ate hoje, e nao ate o fim do mes: dias que ainda nao aconteceram entrariam como
     * disponiveis e vazios, e o dono veria a ocupacao do mes despencar todo dia 1.
     */
    @GetMapping("/occupancy")
    public OccupancyResponse occupancy(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = AppointmentDates.today();
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today;
        return occupancyService.occupancy(start, end);
    }

    /**
     * Retencao da base: quem volta, quem esta prestes a sumir e quem ja sumiu.
     *
     * <p>Sem parametro de periodo de proposito — retencao e uma foto do estado atual da base,
     * nao um intervalo. "Quantos clientes sumiram em marco" nao e uma pergunta que se responda:
     * sumir e um estado que se descobre hoje.
     */
    @GetMapping("/retention")
    public RetentionResponse retention() {
        return retentionService.retention(AppointmentDates.today());
    }

    /** Agenda do dia (padrao: hoje). Ex.: {@code /agenda?date=2026-07-21}. */
    @GetMapping("/agenda")
    public List<AppointmentView> agenda(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : AppointmentDates.today();
        return reportService.dailyAgenda(target);
    }
}
