package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.razor.api.model.Appointment;
import com.razor.api.model.PaymentStatus;
import com.razor.api.model.Professional;
import com.razor.api.model.ProductSale;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.ProductSaleRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.service.CommissionService.ProfessionalCommission;
import com.razor.api.service.PricingService.ServiceCategory;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Testes do {@link CommissionService}: agrega por profissional o faturamento e a comissao dos
 * agendamentos com barbeiro atribuido, usando o preco do catalogo — tudo com mocks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommissionServiceTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private ProfessionalRepository professionalRepository;
    @Mock private ProductSaleRepository productSaleRepository;
    @Mock private ServiceOfferingRepository serviceOfferingRepository;
    @Mock private PricingService pricingService;
    @Mock private com.razor.api.repository.PackageUsageRepository packageUsageRepository;

    @InjectMocks private CommissionService service;

    private Professional prof(UUID id, String name, String commissionPercent) {
        return Professional.builder().id(id).name(name)
                .commissionPercent(new BigDecimal(commissionPercent)).active(true).build();
    }

    private Appointment appt(UUID professionalId, String svc, LocalDate date) {
        return Appointment.builder()
                .professionalId(professionalId).service(svc)
                .appointmentDate(date).appointmentTime(LocalTime.of(10, 0))
                .paymentStatus(PaymentStatus.PENDENTE).build();
    }

    private void givenPrices() {
        when(pricingService.quoter()).thenReturn(PricingService.Quoter.legacy(Map.of(
                ServiceCategory.SIMPLES, new BigDecimal("35.00"),
                ServiceCategory.BARBA, new BigDecimal("30.00"),
                ServiceCategory.COMBO, new BigDecimal("60.00")), null));
        when(pricingService.categorize("Corte")).thenReturn(ServiceCategory.SIMPLES);
        when(pricingService.categorize("Combo")).thenReturn(ServiceCategory.COMBO);
        when(pricingService.categorize("Barba")).thenReturn(ServiceCategory.BARBA);
    }

    @Test
    @DisplayName("comissão por profissional: faturamento x percentual, agregado por barbeiro")
    void commissionsForDay_computesPerProfessional() {
        LocalDate hoje = AppointmentDates.today();
        UUID rafael = UUID.randomUUID();
        UUID bruno = UUID.randomUUID();
        when(professionalRepository.findByBarbershopId(any()))
                .thenReturn(List.of(prof(rafael, "Rafael", "50"), prof(bruno, "Bruno", "40")));
        givenAppointments(List.of(
                appt(rafael, "Corte", hoje),   // 35
                appt(rafael, "Combo", hoje),   // 60
                appt(bruno, "Barba", hoje)));  // 30
        givenPrices();

        List<ProfessionalCommission> comissoes = service.commissionsForDay(hoje);

        ProfessionalCommission r = comissoes.stream().filter(c -> c.professionalName().equals("Rafael")).findFirst().orElseThrow();
        assertThat(r.atendimentos()).isEqualTo(2);
        assertThat(r.faturamento()).isEqualByComparingTo("95.00");
        assertThat(r.comissao()).isEqualByComparingTo("47.50"); // 95 * 50%

        ProfessionalCommission b = comissoes.stream().filter(c -> c.professionalName().equals("Bruno")).findFirst().orElseThrow();
        assertThat(b.atendimentos()).isEqualTo(1);
        assertThat(b.comissao()).isEqualByComparingTo("12.00"); // 30 * 40%
    }

    @Test
    @DisplayName("pacote (V34): corte pago pelo pacote comissiona sobre o valor unitário do pacote")
    void commissions_packageUsageUsesUnitValue() {
        LocalDate hoje = AppointmentDates.today();
        UUID rafael = UUID.randomUUID();
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of(prof(rafael, "Rafael", "50")));
        Appointment ag = appt(rafael, "Corte", hoje);
        ag.setId(7L);
        givenAppointments(List.of(ag));
        when(packageUsageRepository.findByBarbershopIdAndAppointmentIdIn(any(), any())).thenReturn(List.of(
                com.razor.api.model.PackageUsage.builder().appointmentId(7L).uses(1)
                        .unitValue(new BigDecimal("30.00")).originalPrice(new BigDecimal("35.00")).build()));
        givenPrices();

        ProfessionalCommission r = service.commissionsForDay(hoje).get(0);

        assertThat(r.faturamento()).isEqualByComparingTo("30.00");  // 35 de tabela -> 30 do pacote
        assertThat(r.comissao()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("falta (NO_SHOW) não gera faturamento nem comissão para o barbeiro")
    void commissions_ignoresNoShows() {
        LocalDate hoje = AppointmentDates.today();
        UUID rafael = UUID.randomUUID();
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of(prof(rafael, "Rafael", "50")));
        Appointment faltou = appt(rafael, "Combo", hoje);
        faltou.setAttendanceStatus(com.razor.api.model.AttendanceStatus.NO_SHOW);
        givenAppointments(List.of(faltou, appt(rafael, "Corte", hoje)));
        givenPrices();

        ProfessionalCommission r = service.commissionsForDay(hoje).get(0);

        assertThat(r.atendimentos()).isEqualTo(1);
        assertThat(r.faturamento()).isEqualByComparingTo("35.00");
        assertThat(r.comissao()).isEqualByComparingTo("17.50");
    }

    @Test
    @DisplayName("agendamentos sem barbeiro atribuído são ignorados")
    void commissions_ignoresUnassigned() {
        LocalDate hoje = AppointmentDates.today();
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of());
        givenAppointments(List.of(
                appt(null, "Corte", hoje))); // sem professionalId
        givenPrices();

        assertThat(service.commissionsForDay(hoje)).isEmpty();
    }

    @Test
    @DisplayName("agendamentos fora do período não entram no cálculo")
    void commissions_filtersByPeriod() {
        LocalDate hoje = AppointmentDates.today();
        UUID rafael = UUID.randomUUID();
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of(prof(rafael, "Rafael", "50")));
        givenAppointments(List.of(
                appt(rafael, "Corte", hoje.minusDays(10)))); // fora do dia
        givenPrices();

        assertThat(service.commissionsForDay(hoje)).isEmpty();
    }

    @Test
    @DisplayName("relatório textual: sem atendimentos informa que não há barbeiro atribuído")
    void report_empty() {
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of());
        givenAppointments(List.of());
        givenPrices();

        assertThat(service.getComissoesDoDiaText()).contains("Nenhum atendimento");
    }

    @Test
    @DisplayName("relatório textual lista o barbeiro, sua comissão e o total")
    void report_listsProfessionalAndTotal() {
        LocalDate hoje = AppointmentDates.today();
        UUID rafael = UUID.randomUUID();
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of(prof(rafael, "Rafael", "50")));
        givenAppointments(List.of(appt(rafael, "Corte", hoje)));
        givenPrices();

        String out = service.getComissoesDoDiaText();

        // Valor formatado no locale default (pt-BR usa vírgula), igual ao restante do financeiro.
        assertThat(out).contains("Rafael").contains("50%")
                .contains(String.format("%.2f", 17.50)) // 35 * 50%
                .contains("Total de comissões");
    }

    // --- Comissao por servico (V27) ---

    @Test
    @DisplayName("serviço com percentual próprio usa o dele; os demais usam o do barbeiro")
    void serviceCommission_ownPercent_overridesProfessional() {
        LocalDate hoje = AppointmentDates.today();
        UUID rafael = UUID.randomUUID();
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of(prof(rafael, "Rafael", "50")));
        givenAppointments(List.of(
                appt(rafael, "Corte", hoje),    // 35, com percentual próprio de 20%
                appt(rafael, "Barba", hoje)));  // 30, sem percentual próprio -> 50% do barbeiro
        when(serviceOfferingRepository.findByBarbershopId(any())).thenReturn(List.of(
                com.razor.api.model.ServiceOffering.builder()
                        .id(UUID.randomUUID()).name("Corte")
                        .commissionPercent(new BigDecimal("20")).build()));
        givenPrices();

        ProfessionalCommission c = service.commissionsForDay(hoje).get(0);

        // Corte 35 × 20% = 7,00  +  Barba 30 × 50% = 15,00
        assertThat(c.comissao()).isEqualByComparingTo("22.00");
        assertThat(c.faturamento()).isEqualByComparingTo("65.00");
    }

    @Test
    @DisplayName("sem percentual por serviço, tudo continua no percentual do barbeiro")
    void serviceCommission_noneConfigured_keepsProfessionalPercent() {
        LocalDate hoje = AppointmentDates.today();
        UUID rafael = UUID.randomUUID();
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of(prof(rafael, "Rafael", "50")));
        givenAppointments(List.of(appt(rafael, "Corte", hoje)));
        when(serviceOfferingRepository.findByBarbershopId(any())).thenReturn(List.of());
        givenPrices();

        assertThat(service.commissionsForDay(hoje).get(0).comissao()).isEqualByComparingTo("17.50");
    }

    @Test
    @DisplayName("serviço com 0% não paga comissão — diferente de não ter percentual próprio")
    void serviceCommission_zeroPercent_paysNothing() {
        LocalDate hoje = AppointmentDates.today();
        UUID rafael = UUID.randomUUID();
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of(prof(rafael, "Rafael", "50")));
        givenAppointments(List.of(appt(rafael, "Corte", hoje)));
        when(serviceOfferingRepository.findByBarbershopId(any())).thenReturn(List.of(
                com.razor.api.model.ServiceOffering.builder()
                        .id(UUID.randomUUID()).name("Corte")
                        .commissionPercent(BigDecimal.ZERO).build()));
        givenPrices();

        assertThat(service.commissionsForDay(hoje).get(0).comissao()).isEqualByComparingTo("0.00");
    }

    // --- Comissao de produto vendido no balcao (V21) ---

    private ProductSale venda(UUID profId, String total, int qtd, LocalDate dia) {
        return ProductSale.builder()
                .professionalId(profId).total(new BigDecimal(total)).quantity(qtd).saleDate(dia).build();
    }

    @Test
    @DisplayName("venda de balcão entra na comissão, separada do faturamento de serviços")
    void productSale_addsToCommission_separateFromServices() {
        LocalDate hoje = AppointmentDates.today();
        UUID rafael = UUID.randomUUID();
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of(prof(rafael, "Rafael", "50")));
        givenAppointments(List.of(appt(rafael, "Corte", hoje)));
        when(productSaleRepository.findByBarbershopIdAndSaleDateBetween(any(), any(), any()))
                .thenReturn(List.of(venda(rafael, "60.00", 2, hoje)));
        givenPrices();

        ProfessionalCommission c = service.commissionsForDay(hoje).get(0);

        assertThat(c.faturamento()).isEqualByComparingTo("35.00");          // só serviços
        assertThat(c.faturamentoProdutos()).isEqualByComparingTo("60.00");  // só produtos
        assertThat(c.produtos()).isEqualTo(2);                              // unidades vendidas
        assertThat(c.comissao()).isEqualByComparingTo("47.50");             // (35 + 60) * 50%
    }

    @Test
    @DisplayName("venda sem barbeiro não gera comissão para ninguém")
    void productSale_withoutProfessional_noCommission() {
        LocalDate hoje = AppointmentDates.today();
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of());
        givenAppointments(List.of());
        when(productSaleRepository.findByBarbershopIdAndSaleDateBetween(any(), any(), any()))
                .thenReturn(List.of(venda(null, "60.00", 1, hoje)));
        givenPrices();

        assertThat(service.commissionsForDay(hoje)).isEmpty();
    }

    @Test
    @DisplayName("percentual próprio de produto é usado no lugar do de serviço (V23)")
    void productCommission_ownPercent_isUsed() {
        LocalDate hoje = AppointmentDates.today();
        UUID rafael = UUID.randomUUID();
        Professional p = prof(rafael, "Rafael", "40");
        p.setProductCommissionPercent(new BigDecimal("10")); // produto paga menos
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of(p));
        givenAppointments(List.of(appt(rafael, "Corte", hoje)));
        when(productSaleRepository.findByBarbershopIdAndSaleDateBetween(any(), any(), any()))
                .thenReturn(List.of(venda(rafael, "100.00", 1, hoje)));
        givenPrices();

        ProfessionalCommission c = service.commissionsForDay(hoje).get(0);

        assertThat(c.commissionPercent()).isEqualByComparingTo("40");
        assertThat(c.productCommissionPercent()).isEqualByComparingTo("10");
        // serviço 35 × 40% = 14,00  +  produto 100 × 10% = 10,00
        assertThat(c.comissao()).isEqualByComparingTo("24.00");
    }

    @Test
    @DisplayName("sem percentual próprio, produto continua usando o de serviço — migração não muda comissão de ninguém")
    void productCommission_nullPercent_fallsBackToServicePercent() {
        LocalDate hoje = AppointmentDates.today();
        UUID rafael = UUID.randomUUID();
        Professional p = prof(rafael, "Rafael", "40");
        p.setProductCommissionPercent(null); // nunca configurado
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of(p));
        givenAppointments(List.of(appt(rafael, "Corte", hoje)));
        when(productSaleRepository.findByBarbershopIdAndSaleDateBetween(any(), any(), any()))
                .thenReturn(List.of(venda(rafael, "100.00", 1, hoje)));
        givenPrices();

        ProfessionalCommission c = service.commissionsForDay(hoje).get(0);

        assertThat(c.productCommissionPercent()).isEqualByComparingTo("40"); // herdou o de serviço
        // (35 + 100) × 40% = 54,00 — exatamente o que era antes da V23
        assertThat(c.comissao()).isEqualByComparingTo("54.00");
    }

    @Test
    @DisplayName("percentual de produto zero significa não pagar comissão de produto")
    void productCommission_zeroPercent_paysNothingOnProducts() {
        LocalDate hoje = AppointmentDates.today();
        UUID rafael = UUID.randomUUID();
        Professional p = prof(rafael, "Rafael", "40");
        p.setProductCommissionPercent(BigDecimal.ZERO);
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of(p));
        givenAppointments(List.of(appt(rafael, "Corte", hoje)));
        when(productSaleRepository.findByBarbershopIdAndSaleDateBetween(any(), any(), any()))
                .thenReturn(List.of(venda(rafael, "100.00", 1, hoje)));
        givenPrices();

        ProfessionalCommission c = service.commissionsForDay(hoje).get(0);

        assertThat(c.comissao()).isEqualByComparingTo("14.00"); // só os 35 × 40%
    }

    @Test
    @DisplayName("barbeiro que só vendeu produto aparece no relatório, com zero atendimentos")
    void productSale_onlyProductSeller_appearsWithZeroAppointments() {
        LocalDate hoje = AppointmentDates.today();
        UUID rafael = UUID.randomUUID();
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of(prof(rafael, "Rafael", "10")));
        givenAppointments(List.of());
        when(productSaleRepository.findByBarbershopIdAndSaleDateBetween(any(), any(), any()))
                .thenReturn(List.of(venda(rafael, "100.00", 1, hoje)));
        givenPrices();

        ProfessionalCommission c = service.commissionsForDay(hoje).get(0);

        assertThat(c.atendimentos()).isZero();
        assertThat(c.faturamento()).isEqualByComparingTo("0.00");
        assertThat(c.comissao()).isEqualByComparingTo("10.00"); // 100 * 10%
    }

    /** Imita a consulta do periodo no banco: com barbeiro e dentro de [inicio, fim]. */
    private void givenAppointments(List<Appointment> all) {
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(all);
        when(appointmentRepository.findWithProfessionalBetween(any(), any(), any())).thenAnswer(inv -> {
            java.time.LocalDate ini = inv.getArgument(1);
            java.time.LocalDate fim = inv.getArgument(2);
            return all.stream()
                    .filter(a -> a.getProfessionalId() != null && a.getAppointmentDate() != null)
                    .filter(a -> !a.getAppointmentDate().isBefore(ini) && !a.getAppointmentDate().isAfter(fim))
                    .toList();
        });
    }
}
