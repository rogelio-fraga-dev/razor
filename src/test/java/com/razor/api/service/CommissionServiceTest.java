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
    @Mock private PricingService pricingService;

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
        when(pricingService.resolvePrices()).thenReturn(Map.of(
                ServiceCategory.SIMPLES, new BigDecimal("35.00"),
                ServiceCategory.BARBA, new BigDecimal("30.00"),
                ServiceCategory.COMBO, new BigDecimal("60.00")));
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
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(List.of(
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
    @DisplayName("agendamentos sem barbeiro atribuído são ignorados")
    void commissions_ignoresUnassigned() {
        LocalDate hoje = AppointmentDates.today();
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of());
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(List.of(
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
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(List.of(
                appt(rafael, "Corte", hoje.minusDays(10)))); // fora do dia
        givenPrices();

        assertThat(service.commissionsForDay(hoje)).isEmpty();
    }

    @Test
    @DisplayName("relatório textual: sem atendimentos informa que não há barbeiro atribuído")
    void report_empty() {
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of());
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(List.of());
        givenPrices();

        assertThat(service.getComissoesDoDiaText()).contains("Nenhum atendimento");
    }

    @Test
    @DisplayName("relatório textual lista o barbeiro, sua comissão e o total")
    void report_listsProfessionalAndTotal() {
        LocalDate hoje = AppointmentDates.today();
        UUID rafael = UUID.randomUUID();
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of(prof(rafael, "Rafael", "50")));
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(List.of(appt(rafael, "Corte", hoje)));
        givenPrices();

        String out = service.getComissoesDoDiaText();

        // Valor formatado no locale default (pt-BR usa vírgula), igual ao restante do financeiro.
        assertThat(out).contains("Rafael").contains("50%")
                .contains(String.format("%.2f", 17.50)) // 35 * 50%
                .contains("Total de comissões");
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
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(List.of(appt(rafael, "Corte", hoje)));
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
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(List.of());
        when(productSaleRepository.findByBarbershopIdAndSaleDateBetween(any(), any(), any()))
                .thenReturn(List.of(venda(null, "60.00", 1, hoje)));
        givenPrices();

        assertThat(service.commissionsForDay(hoje)).isEmpty();
    }

    @Test
    @DisplayName("barbeiro que só vendeu produto aparece no relatório, com zero atendimentos")
    void productSale_onlyProductSeller_appearsWithZeroAppointments() {
        LocalDate hoje = AppointmentDates.today();
        UUID rafael = UUID.randomUUID();
        when(professionalRepository.findByBarbershopId(any())).thenReturn(List.of(prof(rafael, "Rafael", "10")));
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(List.of());
        when(productSaleRepository.findByBarbershopIdAndSaleDateBetween(any(), any(), any()))
                .thenReturn(List.of(venda(rafael, "100.00", 1, hoje)));
        givenPrices();

        ProfessionalCommission c = service.commissionsForDay(hoje).get(0);

        assertThat(c.atendimentos()).isZero();
        assertThat(c.faturamento()).isEqualByComparingTo("0.00");
        assertThat(c.comissao()).isEqualByComparingTo("10.00"); // 100 * 10%
    }
}
