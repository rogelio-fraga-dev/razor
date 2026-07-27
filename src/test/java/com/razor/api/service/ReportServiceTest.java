package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.razor.api.dto.AppointmentView;
import com.razor.api.dto.FinancialSummaryResponse;
import com.razor.api.model.Appointment;
import com.razor.api.model.PaymentStatus;
import com.razor.api.model.ProductSale;
import com.razor.api.model.Professional;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.ProductSaleRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.service.PricingService.ServiceCategory;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Testes do {@link ReportService}: resumo financeiro estruturado e agenda do dia. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private ProfessionalRepository professionalRepository;
    @Mock private ProductSaleRepository productSaleRepository;
    @Mock private PricingService pricingService;
    @Mock private CustomerService customerService;

    @InjectMocks private ReportService service;

    private Appointment appt(String svc, PaymentStatus status, LocalDate date, LocalTime time, UUID profId) {
        return appt(svc, status, date, time, profId, "5511999");
    }

    private Appointment appt(String svc, PaymentStatus status, LocalDate date, LocalTime time, UUID profId, String phone) {
        return Appointment.builder()
                .customerName("Cliente").service(svc).paymentStatus(status).customerPhone(phone)
                .appointmentDate(date).appointmentTime(time).professionalId(profId).build();
    }

    private ProductSale sale(String total, int qty, LocalDate dia) {
        return ProductSale.builder()
                .total(new BigDecimal(total)).quantity(qty).saleDate(dia).build();
    }

    private void givenPrices() {
        when(pricingService.resolvePrices()).thenReturn(Map.of(
                ServiceCategory.SIMPLES, new BigDecimal("35.00"),
                ServiceCategory.BARBA, new BigDecimal("30.00"),
                ServiceCategory.COMBO, new BigDecimal("60.00")));
        when(pricingService.categorize("Corte")).thenReturn(ServiceCategory.SIMPLES);
        when(pricingService.categorize("Combo")).thenReturn(ServiceCategory.COMBO);
        when(pricingService.categorize("Barba")).thenReturn(ServiceCategory.BARBA);
    }

    @Test
    @DisplayName("resumo financeiro agrega categoria, meio de pagamento, faturamento e ticket médio")
    void financialSummary_aggregates() {
        LocalDate hoje = AppointmentDates.today();
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(List.of(
                appt("Combo", PaymentStatus.PIX, hoje, LocalTime.of(9, 0), null),         // 60
                appt("Barba", PaymentStatus.PRESENCIAL, hoje, LocalTime.of(10, 0), null), // 30
                appt("Corte", PaymentStatus.PENDENTE, hoje, LocalTime.of(11, 0), null),   // 35
                appt("Corte", PaymentStatus.PIX, hoje.minusDays(30), LocalTime.of(9, 0), null))); // fora do período
        givenPrices();

        FinancialSummaryResponse s = service.financialSummary(hoje, hoje);

        assertThat(s.totalAppointments()).isEqualTo(3);
        assertThat(s.categories().combo()).isEqualTo(1);
        assertThat(s.categories().barba()).isEqualTo(1);
        assertThat(s.categories().simples()).isEqualTo(1);
        assertThat(s.payments().pix().count()).isEqualTo(1);
        assertThat(s.payments().pix().value()).isEqualByComparingTo("60.00");
        assertThat(s.payments().presencial().value()).isEqualByComparingTo("30.00");
        assertThat(s.payments().pendente().value()).isEqualByComparingTo("35.00");
        assertThat(s.servicesRevenue()).isEqualByComparingTo("125.00");
        assertThat(s.totalRevenue()).isEqualByComparingTo("125.00"); // sem produtos, total = serviços
        assertThat(s.averageTicket()).isEqualByComparingTo("41.67"); // 125 / 3
    }

    @Test
    @DisplayName("produtos entram no total sem inflar o ticket médio do atendimento")
    void financialSummary_addsProductsToTotalOnly() {
        LocalDate hoje = AppointmentDates.today();
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(List.of(
                appt("Corte", PaymentStatus.PIX, hoje, LocalTime.of(9, 0), null),   // 35
                appt("Corte", PaymentStatus.PIX, hoje, LocalTime.of(10, 0), null))); // 35
        when(productSaleRepository.findByBarbershopIdAndSaleDateBetween(any(), any(), any()))
                .thenReturn(List.of(
                        sale("59.80", 2, hoje),
                        sale("10.00", 1, hoje)));
        givenPrices();

        FinancialSummaryResponse s = service.financialSummary(hoje, hoje);

        assertThat(s.servicesRevenue()).isEqualByComparingTo("70.00");
        assertThat(s.productsRevenue()).isEqualByComparingTo("69.80");
        assertThat(s.productsSold()).isEqualTo(3);
        assertThat(s.totalRevenue()).isEqualByComparingTo("139.80");   // 70 + 69,80
        assertThat(s.averageTicket()).isEqualByComparingTo("35.00");   // 70 / 2 — produto fora
    }

    @Test
    @DisplayName("venda de produto sem atendimento nenhum aparece no total, com ticket médio zero")
    void financialSummary_productsWithoutAppointments() {
        LocalDate hoje = AppointmentDates.today();
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(List.of());
        when(productSaleRepository.findByBarbershopIdAndSaleDateBetween(any(), any(), any()))
                .thenReturn(List.of(sale("29.90", 1, hoje)));
        givenPrices();

        FinancialSummaryResponse s = service.financialSummary(hoje, hoje);

        assertThat(s.totalAppointments()).isZero();
        assertThat(s.servicesRevenue()).isEqualByComparingTo("0.00");
        assertThat(s.totalRevenue()).isEqualByComparingTo("29.90");
        assertThat(s.averageTicket()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("período sem atendimentos: tudo zerado, ticket médio 0")
    void financialSummary_empty() {
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(List.of());
        givenPrices();

        FinancialSummaryResponse s = service.financialSummary(AppointmentDates.today(), AppointmentDates.today());

        assertThat(s.totalAppointments()).isZero();
        assertThat(s.totalRevenue()).isEqualByComparingTo("0.00");
        assertThat(s.averageTicket()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("agenda do dia vem ordenada por horário e com o nome do barbeiro resolvido")
    void dailyAgenda_sortedWithProfessionalName() {
        LocalDate hoje = AppointmentDates.today();
        UUID rafael = UUID.randomUUID();
        when(professionalRepository.findByBarbershopId(any()))
                .thenReturn(List.of(Professional.builder().id(rafael).name("Rafael").build()));
        when(customerService.subscriberPhones()).thenReturn(Set.of("5511888"));
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(List.of(
                appt("Corte", PaymentStatus.PIX, hoje, LocalTime.of(10, 0), rafael, "5511888"),  // assinante
                appt("Barba", PaymentStatus.PENDENTE, hoje, LocalTime.of(9, 0), null, "5511777"))); // avulso

        List<AppointmentView> agenda = service.dailyAgenda(hoje);

        assertThat(agenda).hasSize(2);
        assertThat(agenda.get(0).time()).isEqualTo(LocalTime.of(9, 0)); // ordenado
        assertThat(agenda.get(0).professionalName()).isNull();           // sem barbeiro
        assertThat(agenda.get(0).subscriber()).isFalse();                // avulso
        assertThat(agenda.get(1).time()).isEqualTo(LocalTime.of(10, 0));
        assertThat(agenda.get(1).professionalName()).isEqualTo("Rafael");
        assertThat(agenda.get(1).subscriber()).isTrue();                 // assinante
    }

    // --- Serie de faturamento do grafico (uma varredura, faixas definidas no backend) ---

    @Test
    @DisplayName("série de 7 dias tem um ponto por dia e soma no dia certo")
    void revenueSeries_last7Days_bucketsByDay() {
        LocalDate hoje = LocalDate.of(2026, 7, 27);
        givenPrices();
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(List.of(
                appt("Combo", PaymentStatus.PIX, hoje, LocalTime.of(10, 0), null),            // 60 hoje
                appt("Corte", PaymentStatus.PIX, hoje, LocalTime.of(11, 0), null),            // 35 hoje
                appt("Corte", PaymentStatus.PIX, hoje.minusDays(3), LocalTime.of(9, 0), null) // 35 ha 3 dias
        ));

        List<com.razor.api.dto.RevenuePoint> serie =
                service.revenueSeries(ReportService.SeriesPeriod.LAST_7_DAYS, hoje);

        assertThat(serie).hasSize(7);
        assertThat(serie.get(6).from()).isEqualTo(hoje);              // ultimo ponto = hoje
        assertThat(serie.get(6).revenue()).isEqualByComparingTo("95.00");
        assertThat(serie.get(3).revenue()).isEqualByComparingTo("35.00");
        assertThat(serie.get(0).from()).isEqualTo(hoje.minusDays(6)); // primeiro ponto = 6 dias atras
        assertThat(serie.get(0).revenue()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("agendamento fora da janela não entra na série")
    void revenueSeries_outsideWindow_ignored() {
        LocalDate hoje = LocalDate.of(2026, 7, 27);
        givenPrices();
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(List.of(
                appt("Combo", PaymentStatus.PIX, hoje.minusDays(40), LocalTime.of(10, 0), null)));

        List<com.razor.api.dto.RevenuePoint> serie =
                service.revenueSeries(ReportService.SeriesPeriod.LAST_7_DAYS, hoje);

        assertThat(serie).extracting(p -> p.revenue().signum()).containsOnly(0);
    }

    @Test
    @DisplayName("série de 6 meses fecha o último ponto em hoje, não no fim do mês")
    void revenueSeries_last6Months_currentMonthEndsToday() {
        LocalDate hoje = LocalDate.of(2026, 7, 27);
        givenPrices();
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(List.of());

        List<com.razor.api.dto.RevenuePoint> serie =
                service.revenueSeries(ReportService.SeriesPeriod.LAST_6_MONTHS, hoje);

        assertThat(serie).hasSize(6);
        assertThat(serie.get(5).from()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(serie.get(5).to()).isEqualTo(hoje);
        assertThat(serie.get(0).from()).isEqualTo(LocalDate.of(2026, 2, 1));
    }
}
