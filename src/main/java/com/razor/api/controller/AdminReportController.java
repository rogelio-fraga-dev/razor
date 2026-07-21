package com.razor.api.controller;

import com.razor.api.dto.CommissionReportResponse;
import com.razor.api.service.CommissionService;
import com.razor.api.util.AppointmentDates;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

/**
 * Relatorios do painel web (tenant vem do JWT). Comeca pelas comissoes (dados estruturados);
 * financeiro e agenda estruturados entram nos proximos increments.
 */
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final CommissionService commissionService;

    /** Comissoes por profissional num dia (padrao: hoje). Ex.: {@code /commissions?date=2026-07-21}. */
    @GetMapping("/commissions")
    public CommissionReportResponse commissions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : AppointmentDates.today();
        return CommissionReportResponse.of(target, commissionService.commissionsForDay(target));
    }
}
