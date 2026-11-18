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
import com.razor.api.model.Comanda;
import com.razor.api.model.ComandaItem;
import com.razor.api.repository.ComandaItemRepository;
import com.razor.api.repository.ComandaRepository;
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
    @Mock private ComandaRepository comandaRepository;
    @Mock private ComandaItemRepository comandaItemRepository;
    @Mock private PricingService pricingService;
    @Mock private CustomerService customerService;
    @Mock private ExpenseService expenseService;
    @Mock private com.razor.api.repository.CashMovementRepository cashMovementRepository;

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
        when(appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(any(), any(), any())).thenReturn(List.of(
                appt("Combo", PaymentStatus.PIX, hoje, LocalTime.of(9, 0), null),         // 60
                appt("Barba", PaymentStatus.PRESENCIAL, hoje, LocalTime.of(10, 0), null), // 30
                appt("Corte", PaymentStatus.PENDENTE, hoje, LocalTime.of(11, 0), null)));  // 35
        // O recorte de período agora é feito pelo banco (consulta ),
        // não descartado em Java depois de ler a tabela inteira do tenant.
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
        when(appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(any(), any(), any())).thenReturn(List.of(
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
        when(appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(any(), any(), any())).thenReturn(List.of());
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
        when(appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(any(), any(), any())).thenReturn(List.of());
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
        when(appointmentRepository.findByBarbershopIdAndAppointmentDate(any(), any())).thenReturn(List.of(
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
        when(appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(any(), any(), any())).thenReturn(List.of(
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
        when(appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(any(), any(), any())).thenReturn(List.of(
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
        when(appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(any(), any(), any())).thenReturn(List.of());

        List<com.razor.api.dto.RevenuePoint> serie =
                service.revenueSeries(ReportService.SeriesPeriod.LAST_6_MONTHS, hoje);

        assertThat(serie).hasSize(6);
        assertThat(serie.get(5).from()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(serie.get(5).to()).isEqualTo(hoje);
        assertThat(serie.get(0).from()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    // --- Servico lancado em comanda (V28) ---

    private Comanda comanda(Long appointmentId, LocalDate dia) {
        return Comanda.builder()
                .id(java.util.UUID.randomUUID()).status(Comanda.Status.FECHADA)
                .appointmentId(appointmentId).comandaDate(dia).build();
    }

    private ComandaItem comandaItem(ComandaItem.Kind kind, String total) {
        return ComandaItem.builder()
                .id(java.util.UUID.randomUUID()).kind(kind).total(new BigDecimal(total)).build();
    }

    @Test
    @DisplayName("serviço de comanda SEM agendamento entra no faturamento")
    void comandaWithoutAppointment_addsServiceRevenue() {
        LocalDate hoje = AppointmentDates.today();
        when(appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(any(), any(), any())).thenReturn(List.of());
        Comanda c = comanda(null, hoje);
        when(comandaRepository.findByBarbershopIdAndComandaDateBetween(any(), any(), any()))
                .thenReturn(List.of(c));
        when(comandaItemRepository.findByComandaIdIn(any()))
                .thenReturn(List.of(comandaItem(ComandaItem.Kind.SERVICO, "40.00")));
        givenPrices();

        FinancialSummaryResponse s = service.financialSummary(hoje, hoje);

        assertThat(s.servicesRevenue()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("serviço de comanda COM agendamento não conta duas vezes")
    void comandaWithAppointment_isNotDoubleCounted() {
        LocalDate hoje = AppointmentDates.today();
        // O agendamento ja responde por 35,00
        when(appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(any(), any(), any()))
                .thenReturn(List.of(appt("Corte", PaymentStatus.PIX, hoje, LocalTime.of(9, 0), null)));
        Comanda c = comanda(99L, hoje); // vinculada
        when(comandaRepository.findByBarbershopIdAndComandaDateBetween(any(), any(), any()))
                .thenReturn(List.of(c));
        when(comandaItemRepository.findByComandaIdIn(any()))
                .thenReturn(List.of(comandaItem(ComandaItem.Kind.SERVICO, "35.00")));
        givenPrices();

        FinancialSummaryResponse s = service.financialSummary(hoje, hoje);

        assertThat(s.servicesRevenue()).isEqualByComparingTo("35.00"); // e nao 70,00
    }

    @Test
    @DisplayName("produto da comanda nao e somado como servico — ja conta em product_sales")
    void comandaProductItem_isNotCountedAsService() {
        LocalDate hoje = AppointmentDates.today();
        when(appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(any(), any(), any())).thenReturn(List.of());
        Comanda c = comanda(null, hoje);
        when(comandaRepository.findByBarbershopIdAndComandaDateBetween(any(), any(), any()))
                .thenReturn(List.of(c));
        when(comandaItemRepository.findByComandaIdIn(any()))
                .thenReturn(List.of(comandaItem(ComandaItem.Kind.PRODUTO, "29.90")));
        givenPrices();

        assertThat(service.financialSummary(hoje, hoje).servicesRevenue()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("comanda ainda aberta nao entra no faturamento")
    void openComanda_isNotCounted() {
        LocalDate hoje = AppointmentDates.today();
        when(appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(any(), any(), any())).thenReturn(List.of());
        Comanda aberta = Comanda.builder()
                .id(java.util.UUID.randomUUID()).status(Comanda.Status.ABERTA)
                .comandaDate(hoje).build();
        when(comandaRepository.findByBarbershopIdAndComandaDateBetween(any(), any(), any()))
                .thenReturn(List.of(aberta));
        givenPrices();

        assertThat(service.financialSummary(hoje, hoje).servicesRevenue()).isEqualByComparingTo("0.00");
    }

    // =========================================================================================
    // DRE (V31): receita do resumo financeiro menos as despesas por competência.
    // =========================================================================================

    private static final LocalDate JUL_1 = LocalDate.of(2026, 7, 1);
    private static final LocalDate JUL_31 = LocalDate.of(2026, 7, 31);

    /** Receita fixa de 3 combos = R$ 180,00, para o foco dos testes ficar nas despesas. */
    private void givenRevenue180() {
        givenPrices();
        when(appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(any(), any(), any()))
                .thenReturn(List.of(
                        appt("Combo", PaymentStatus.PIX, JUL_1, LocalTime.of(9, 0), null),
                        appt("Combo", PaymentStatus.PIX, JUL_1, LocalTime.of(10, 0), null),
                        appt("Combo", PaymentStatus.PIX, JUL_1, LocalTime.of(11, 0), null)));
        when(productSaleRepository.findByBarbershopIdAndSaleDateBetween(any(), any(), any()))
                .thenReturn(List.of());
        when(comandaRepository.findByBarbershopIdAndComandaDateBetween(any(), any(), any()))
                .thenReturn(List.of());
    }

    private com.razor.api.model.Expense despesa(String valor, com.razor.api.model.ExpenseCategory cat,
                                                LocalDate vencimento, LocalDate pagoEm) {
        return com.razor.api.model.Expense.builder()
                .id(UUID.randomUUID()).description(cat.label()).category(cat)
                .amount(new BigDecimal(valor)).dueDate(vencimento).paidAt(pagoEm).build();
    }

    @Test
    @DisplayName("DRE: resultado é receita menos despesa, com margem em pontos percentuais")
    void dre_resultAndMargin() {
        givenRevenue180();
        var aluguel = despesa("45.00", com.razor.api.model.ExpenseCategory.ALUGUEL, LocalDate.of(2026, 7, 5), null);
        when(expenseService.totalsByCategory(JUL_1, JUL_31)).thenReturn(
                Map.of(com.razor.api.model.ExpenseCategory.ALUGUEL, new BigDecimal("45.00")));
        when(expenseService.betweenDueDates(JUL_1, JUL_31)).thenReturn(List.of(aluguel));

        var dre = service.dre(JUL_1, JUL_31, LocalDate.of(2026, 7, 20));

        assertThat(dre.totalRevenue()).isEqualByComparingTo("180.00");
        assertThat(dre.totalExpenses()).isEqualByComparingTo("45.00");
        assertThat(dre.result()).isEqualByComparingTo("135.00");
        assertThat(dre.marginPercent()).isEqualByComparingTo("75.0");
        assertThat(dre.expenses()).singleElement()
                .satisfies(l -> assertThat(l.shareOfRevenue()).isEqualByComparingTo("25.0"));
    }

    private com.razor.api.model.CashMovement vendaCaixa(com.razor.api.model.CashPaymentMethod m, String valor,
                                                        String taxa, UUID comanda) {
        return com.razor.api.model.CashMovement.builder()
                .type(com.razor.api.model.CashMovementType.VENDA).paymentMethod(m)
                .amount(new BigDecimal(valor)).feeAmount(new BigDecimal(taxa)).comandaId(comanda).build();
    }

    @Test
    @DisplayName("recebimentos por forma de pagamento refletem o split: cada parte na sua linha, com taxa e líquido")
    void financialSummary_receiptsReflectSplitPayments() {
        givenRevenue180();
        UUID comandaSplit = UUID.randomUUID();
        when(cashMovementRepository.findByBarbershopIdAndTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(), any(), any(), any())).thenReturn(List.of(
                // comanda de R$ 80 paga em split: 20 dinheiro + 60 crédito (3,5% = 2,10)
                vendaCaixa(com.razor.api.model.CashPaymentMethod.DINHEIRO, "20.00", "0", comandaSplit),
                vendaCaixa(com.razor.api.model.CashPaymentMethod.CREDITO, "60.00", "2.10", comandaSplit),
                // outra comanda, inteira no crédito
                vendaCaixa(com.razor.api.model.CashPaymentMethod.CREDITO, "40.00", "1.40", UUID.randomUUID()),
                vendaCaixa(com.razor.api.model.CashPaymentMethod.PIX, "35.00", "0", null)));

        FinancialSummaryResponse s = service.financialSummary(JUL_1, JUL_31);

        var r = s.receipts();
        assertThat(r.methods()).extracting(FinancialSummaryResponse.MethodLine::method).containsExactly(
                com.razor.api.model.CashPaymentMethod.DINHEIRO, com.razor.api.model.CashPaymentMethod.PIX,
                com.razor.api.model.CashPaymentMethod.CREDITO);
        var credito = r.methods().get(2);
        assertThat(credito.count()).isEqualTo(2);
        assertThat(credito.gross()).isEqualByComparingTo("100.00");
        assertThat(credito.fees()).isEqualByComparingTo("3.50");
        assertThat(credito.net()).isEqualByComparingTo("96.50");
        assertThat(r.methods().get(0).gross()).isEqualByComparingTo("20.00");
        assertThat(r.gross()).isEqualByComparingTo("155.00");
        assertThat(r.fees()).isEqualByComparingTo("3.50");
        assertThat(r.net()).isEqualByComparingTo("151.50");
    }

    @Test
    @DisplayName("DRE: taxa dos meios de pagamento é deduzida da receita antes do resultado")
    void dre_deductsPaymentFees() {
        givenRevenue180();
        when(cashMovementRepository.findByBarbershopIdAndTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(), any(), any(), any())).thenReturn(List.of(
                vendaCaixa(com.razor.api.model.CashPaymentMethod.CREDITO, "100.00", "3.50", null)));
        when(expenseService.totalsByCategory(any(), any())).thenReturn(Map.of());
        when(expenseService.betweenDueDates(any(), any())).thenReturn(List.of());

        var dre = service.dre(JUL_1, JUL_31, LocalDate.of(2026, 7, 20));

        assertThat(dre.totalRevenue()).isEqualByComparingTo("180.00");
        assertThat(dre.paymentFees()).isEqualByComparingTo("3.50");
        assertThat(dre.netRevenue()).isEqualByComparingTo("176.50");
        assertThat(dre.result()).isEqualByComparingTo("176.50");
    }

    @Test
    @DisplayName("DRE: prejuízo aparece como resultado negativo, não zerado")
    void dre_lossIsNegative() {
        givenRevenue180();
        var aluguel = despesa("500.00", com.razor.api.model.ExpenseCategory.ALUGUEL, LocalDate.of(2026, 7, 5), null);
        when(expenseService.totalsByCategory(any(), any())).thenReturn(
                Map.of(com.razor.api.model.ExpenseCategory.ALUGUEL, new BigDecimal("500.00")));
        when(expenseService.betweenDueDates(any(), any())).thenReturn(List.of(aluguel));

        var dre = service.dre(JUL_1, JUL_31, LocalDate.of(2026, 7, 20));

        // Mês no vermelho tem que aparecer no vermelho — é a informação mais importante do relatório.
        assertThat(dre.result()).isEqualByComparingTo("-320.00");
        assertThat(dre.marginPercent().signum()).isNegative();
    }

    @Test
    @DisplayName("DRE: sem receita, a margem vem nula em vez de 0% ou divisão por zero")
    void dre_noRevenue_nullMargin() {
        givenPrices();
        when(appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(any(), any(), any()))
                .thenReturn(List.of());
        when(productSaleRepository.findByBarbershopIdAndSaleDateBetween(any(), any(), any())).thenReturn(List.of());
        when(comandaRepository.findByBarbershopIdAndComandaDateBetween(any(), any(), any())).thenReturn(List.of());
        var aluguel = despesa("2500.00", com.razor.api.model.ExpenseCategory.ALUGUEL, LocalDate.of(2026, 7, 5), null);
        when(expenseService.totalsByCategory(any(), any())).thenReturn(
                Map.of(com.razor.api.model.ExpenseCategory.ALUGUEL, new BigDecimal("2500.00")));
        when(expenseService.betweenDueDates(any(), any())).thenReturn(List.of(aluguel));

        var dre = service.dre(JUL_1, JUL_31, LocalDate.of(2026, 7, 20));

        assertThat(dre.marginPercent()).isNull();
        assertThat(dre.expenses()).singleElement()
                .satisfies(l -> assertThat(l.shareOfRevenue()).isNull());
        assertThat(dre.result()).isEqualByComparingTo("-2500.00");
    }

    @Test
    @DisplayName("DRE: pago, em aberto e vencido somam o total, e vencido só conta o que passou do prazo")
    void dre_splitsPaidUnpaidOverdue() {
        givenRevenue180();
        LocalDate hoje = LocalDate.of(2026, 7, 20);
        var paga = despesa("100.00", com.razor.api.model.ExpenseCategory.ENERGIA, LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 4));
        var vencida = despesa("60.00", com.razor.api.model.ExpenseCategory.AGUA, LocalDate.of(2026, 7, 10), null);
        var aVencer = despesa("40.00", com.razor.api.model.ExpenseCategory.INTERNET, LocalDate.of(2026, 7, 28), null);
        when(expenseService.totalsByCategory(any(), any())).thenReturn(Map.of());
        when(expenseService.betweenDueDates(any(), any())).thenReturn(List.of(paga, vencida, aVencer));

        var dre = service.dre(JUL_1, JUL_31, hoje);

        assertThat(dre.totalExpenses()).isEqualByComparingTo("200.00");
        assertThat(dre.expensesPaid()).isEqualByComparingTo("100.00");
        assertThat(dre.expensesUnpaid()).isEqualByComparingTo("100.00");
        // A que ainda vai vencer está em aberto, mas não está vencida.
        assertThat(dre.overdueAmount()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("DRE: despesa em aberto entra no resultado — competência, não desembolso")
    void dre_unpaidStillCounts() {
        givenRevenue180();
        var emAberto = despesa("80.00", com.razor.api.model.ExpenseCategory.ALUGUEL, LocalDate.of(2026, 7, 5), null);
        when(expenseService.totalsByCategory(any(), any())).thenReturn(
                Map.of(com.razor.api.model.ExpenseCategory.ALUGUEL, new BigDecimal("80.00")));
        when(expenseService.betweenDueDates(any(), any())).thenReturn(List.of(emAberto));

        var dre = service.dre(JUL_1, JUL_31, LocalDate.of(2026, 7, 20));

        // Atrasar o boleto não pode fazer o mês parecer mais lucrativo.
        assertThat(dre.result()).isEqualByComparingTo("100.00");
        assertThat(dre.expensesPaid()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("DRE: as linhas vêm da maior despesa para a menor")
    void dre_linesSortedByAmountDesc() {
        givenRevenue180();
        when(expenseService.totalsByCategory(any(), any())).thenReturn(Map.of(
                com.razor.api.model.ExpenseCategory.MARKETING, new BigDecimal("20.00"),
                com.razor.api.model.ExpenseCategory.ALUGUEL, new BigDecimal("90.00"),
                com.razor.api.model.ExpenseCategory.ENERGIA, new BigDecimal("45.00")));
        when(expenseService.betweenDueDates(any(), any())).thenReturn(List.of());

        var dre = service.dre(JUL_1, JUL_31, LocalDate.of(2026, 7, 20));

        assertThat(dre.expenses()).extracting(l -> l.category().name())
                .containsExactly("ALUGUEL", "ENERGIA", "MARKETING");
    }

    @Test
    @DisplayName("DRE e resumo financeiro mostram o mesmo faturamento para o mesmo período")
    void dre_revenueMatchesFinancialSummary() {
        givenRevenue180();
        when(expenseService.totalsByCategory(any(), any())).thenReturn(Map.of());
        when(expenseService.betweenDueDates(any(), any())).thenReturn(List.of());

        var dre = service.dre(JUL_1, JUL_31, LocalDate.of(2026, 7, 20));
        var resumo = service.financialSummary(JUL_1, JUL_31);

        // Duas telas mostrando faturamentos diferentes para o mesmo mês destruiria a confiança
        // no painel inteiro — por isso o DRE reusa o resumo em vez de recalcular.
        assertThat(dre.totalRevenue()).isEqualByComparingTo(resumo.totalRevenue());
        assertThat(dre.servicesRevenue()).isEqualByComparingTo(resumo.servicesRevenue());
        assertThat(dre.productsRevenue()).isEqualByComparingTo(resumo.productsRevenue());
    }
}