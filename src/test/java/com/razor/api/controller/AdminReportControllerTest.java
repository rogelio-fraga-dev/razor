package com.razor.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.dto.AppointmentView;
import com.razor.api.dto.CommissionReportResponse;
import com.razor.api.dto.FinancialSummaryResponse;
import com.razor.api.dto.FinancialSummaryResponse.Bucket;
import com.razor.api.dto.FinancialSummaryResponse.CategoryBreakdown;
import com.razor.api.dto.FinancialSummaryResponse.PaymentBreakdown;
import com.razor.api.model.PaymentStatus;
import com.razor.api.service.CommissionService;
import com.razor.api.service.CommissionService.ProfessionalCommission;
import com.razor.api.service.ReportService;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminReportControllerTest {

    @Mock private CommissionService commissionService;
    @Mock private ReportService reportService;
    @InjectMocks private AdminReportController controller;

    @Test
    @DisplayName("comissões com data devolve o relatório estruturado e o total")
    void commissions_withDate() {
        LocalDate dia = LocalDate.of(2026, 7, 21);
        when(commissionService.commissionsForDay(dia)).thenReturn(List.of(
                new ProfessionalCommission(UUID.randomUUID(), "Rafael", new BigDecimal("50"),
                        2, new BigDecimal("95.00"), 0, BigDecimal.ZERO, new BigDecimal("47.50"))));

        CommissionReportResponse resp = controller.commissions(dia);

        assertThat(resp.date()).isEqualTo(dia);
        assertThat(resp.items()).hasSize(1);
        assertThat(resp.items().get(0).professionalName()).isEqualTo("Rafael");
        assertThat(resp.total()).isEqualByComparingTo("47.50");
    }

    @Test
    @DisplayName("comissões sem data usa hoje")
    void commissions_defaultsToday() {
        when(commissionService.commissionsForDay(AppointmentDates.today())).thenReturn(List.of());

        CommissionReportResponse resp = controller.commissions(null);

        assertThat(resp.date()).isEqualTo(AppointmentDates.today());
        verify(commissionService).commissionsForDay(AppointmentDates.today());
    }

    @Test
    @DisplayName("financeiro sem datas usa hoje..hoje")
    void financial_defaultsToday() {
        LocalDate hoje = AppointmentDates.today();
        FinancialSummaryResponse summary = new FinancialSummaryResponse(hoje, hoje, 0,
                new CategoryBreakdown(0, 0, 0),
                new PaymentBreakdown(new Bucket(0, BigDecimal.ZERO), new Bucket(0, BigDecimal.ZERO),
                        new Bucket(0, BigDecimal.ZERO)),
                BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(reportService.financialSummary(hoje, hoje)).thenReturn(summary);

        assertThat(controller.financial(null, null)).isSameAs(summary);
        verify(reportService).financialSummary(hoje, hoje);
    }

    @Test
    @DisplayName("agenda sem data usa hoje")
    void agenda_defaultsToday() {
        LocalDate hoje = AppointmentDates.today();
        List<AppointmentView> agenda = List.of(new AppointmentView(
                LocalTime.of(10, 0), "Ana", "5511999", "Corte", 30,
                PaymentStatus.PIX, UUID.randomUUID(), "Rafael", true));
        when(reportService.dailyAgenda(hoje)).thenReturn(agenda);

        assertThat(controller.agenda(null)).isEqualTo(agenda);
        verify(reportService).dailyAgenda(hoje);
    }
}
