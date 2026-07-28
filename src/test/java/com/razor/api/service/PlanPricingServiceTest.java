package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.razor.api.model.Appointment;
import com.razor.api.model.Customer;
import com.razor.api.model.PlanBenefit;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.model.ServiceOffering;
import com.razor.api.repository.PlanBenefitRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.service.PlanPricingService.PricedItem;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Testes do motor de beneficios (V27), espelhando o que o app do cliente mostra:
 * "De R$ 35,00 / Por R$ 0,00", 10% em categoria e restricao de dia.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanPricingServiceTest {

    @Mock private PlanBenefitRepository benefitRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private ServiceOfferingRepository serviceOfferingRepository;

    @InjectMocks private PlanPricingService service;

    private final UUID planId = UUID.randomUUID();
    private final UUID corteId = UUID.randomUUID();
    /** 2026-07-27 é uma segunda-feira. */
    private final LocalDate segunda = LocalDate.of(2026, 7, 27);
    private final LocalDate sabado = LocalDate.of(2026, 8, 1);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Customer assinante() {
        return Customer.builder()
                .id(UUID.randomUUID()).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .phoneNumber("5511999").subscriptionPlanId(planId).build();
    }

    private PlanBenefit benefit(PlanBenefit.TargetType type, UUID targetId, String pct,
                                Integer quota, String weekdays) {
        return PlanBenefit.builder()
                .id(UUID.randomUUID()).planId(planId)
                .barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .targetType(type).targetId(targetId)
                .discountPercent(new BigDecimal(pct))
                .monthlyQuota(quota).weekdays(weekdays)
                .build();
    }

    private void givenBenefits(PlanBenefit... benefits) {
        when(benefitRepository.findByPlanId(planId)).thenReturn(List.of(benefits));
    }

    // ---------- sem plano ----------

    @Test
    @DisplayName("visitante sem plano paga o preço cheio")
    void noPlan_paysFullPrice() {
        PricedItem p = service.priceFor(null, PlanBenefit.TargetType.SERVICO, corteId,
                "Corte", new BigDecimal("35.00"), segunda);

        assertThat(p.finalPrice()).isEqualByComparingTo("35.00");
        assertThat(p.discounted()).isFalse();
    }

    @Test
    @DisplayName("cliente cadastrado sem plano paga o preço cheio")
    void customerWithoutPlan_paysFullPrice() {
        Customer semPlano = Customer.builder().phoneNumber("5511999").build();

        PricedItem p = service.priceFor(semPlano, PlanBenefit.TargetType.SERVICO, corteId,
                "Corte", new BigDecimal("35.00"), segunda);

        assertThat(p.discounted()).isFalse();
    }

    // ---------- desconto ----------

    @Test
    @DisplayName("100% OFF zera o preço — o “GRÁTIS” da tela")
    void hundredPercentOff_isFree() {
        givenBenefits(benefit(PlanBenefit.TargetType.SERVICO, corteId, "100", null, null));

        PricedItem p = service.priceFor(assinante(), PlanBenefit.TargetType.SERVICO, corteId,
                "Corte", new BigDecimal("35.00"), segunda);

        assertThat(p.originalPrice()).isEqualByComparingTo("35.00");
        assertThat(p.finalPrice()).isEqualByComparingTo("0.00");
        assertThat(p.isFree()).isTrue();
    }

    @Test
    @DisplayName("10% OFF em toda a categoria — benefício sem alvo específico")
    void categoryWideDiscount_applies() {
        givenBenefits(benefit(PlanBenefit.TargetType.PRODUTO, null, "10", null, null));

        PricedItem p = service.priceFor(assinante(), PlanBenefit.TargetType.PRODUTO,
                UUID.randomUUID(), "Pomada", new BigDecimal("48.00"), segunda);

        assertThat(p.finalPrice()).isEqualByComparingTo("43.20"); // igual à tela do app
    }

    @Test
    @DisplayName("benefício do item específico ganha do benefício geral")
    void specificBenefit_beatsCategoryWide() {
        givenBenefits(
                benefit(PlanBenefit.TargetType.SERVICO, null, "10", null, null),
                benefit(PlanBenefit.TargetType.SERVICO, corteId, "100", null, null));

        PricedItem p = service.priceFor(assinante(), PlanBenefit.TargetType.SERVICO, corteId,
                "Corte", new BigDecimal("35.00"), segunda);

        assertThat(p.finalPrice()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("benefício de serviço não vaza para produto")
    void benefitOfOtherType_doesNotApply() {
        givenBenefits(benefit(PlanBenefit.TargetType.SERVICO, null, "100", null, null));

        PricedItem p = service.priceFor(assinante(), PlanBenefit.TargetType.PRODUTO,
                UUID.randomUUID(), "Pomada", new BigDecimal("48.00"), segunda);

        assertThat(p.finalPrice()).isEqualByComparingTo("48.00");
        assertThat(p.discounted()).isFalse();
    }

    // ---------- restrição de dia ----------

    @Test
    @DisplayName("plano “segunda a quarta” vale na segunda")
    void weekdayRestricted_appliesOnAllowedDay() {
        givenBenefits(benefit(PlanBenefit.TargetType.SERVICO, corteId, "100", null, "1,2,3"));

        PricedItem p = service.priceFor(assinante(), PlanBenefit.TargetType.SERVICO, corteId,
                "Corte", new BigDecimal("35.00"), segunda);

        assertThat(p.isFree()).isTrue();
    }

    @Test
    @DisplayName("plano “segunda a quarta” não vale no sábado — paga cheio")
    void weekdayRestricted_doesNotApplyOnOtherDay() {
        givenBenefits(benefit(PlanBenefit.TargetType.SERVICO, corteId, "100", null, "1,2,3"));

        PricedItem p = service.priceFor(assinante(), PlanBenefit.TargetType.SERVICO, corteId,
                "Corte", new BigDecimal("35.00"), sabado);

        assertThat(p.finalPrice()).isEqualByComparingTo("35.00");
        assertThat(p.discounted()).isFalse();
    }

    // ---------- cota mensal ----------

    @Test
    @DisplayName("dentro da cota do mês, o benefício vale")
    void withinQuota_applies() {
        givenBenefits(benefit(PlanBenefit.TargetType.SERVICO, corteId, "100", 2, null));
        when(appointmentRepository.findByBarbershopIdAndCustomerPhone(any(), any()))
                .thenReturn(List.of(appt("Corte", segunda.withDayOfMonth(5))));

        PricedItem p = service.priceFor(assinante(), PlanBenefit.TargetType.SERVICO, corteId,
                "Corte", new BigDecimal("35.00"), segunda);

        assertThat(p.isFree()).isTrue(); // 1 usado de 2
    }

    @Test
    @DisplayName("cota estourada: volta a pagar o preço cheio")
    void quotaExhausted_paysFullPrice() {
        givenBenefits(benefit(PlanBenefit.TargetType.SERVICO, corteId, "100", 2, null));
        when(appointmentRepository.findByBarbershopIdAndCustomerPhone(any(), any()))
                .thenReturn(List.of(
                        appt("Corte", segunda.withDayOfMonth(5)),
                        appt("Corte", segunda.withDayOfMonth(10))));

        PricedItem p = service.priceFor(assinante(), PlanBenefit.TargetType.SERVICO, corteId,
                "Corte", new BigDecimal("35.00"), segunda);

        assertThat(p.finalPrice()).isEqualByComparingTo("35.00");
        assertThat(p.discounted()).isFalse();
    }

    @Test
    @DisplayName("atendimento de outro serviço não consome a cota")
    void quota_countsOnlyTheSameService() {
        givenBenefits(benefit(PlanBenefit.TargetType.SERVICO, corteId, "100", 1, null));
        when(appointmentRepository.findByBarbershopIdAndCustomerPhone(any(), any()))
                .thenReturn(List.of(appt("Barba", segunda.withDayOfMonth(5))));

        PricedItem p = service.priceFor(assinante(), PlanBenefit.TargetType.SERVICO, corteId,
                "Corte", new BigDecimal("35.00"), segunda);

        assertThat(p.isFree()).isTrue();
    }

    @Test
    @DisplayName("atendimento do mês anterior não consome a cota deste mês")
    void quota_ignoresPreviousMonth() {
        givenBenefits(benefit(PlanBenefit.TargetType.SERVICO, corteId, "100", 1, null));
        when(appointmentRepository.findByBarbershopIdAndCustomerPhone(any(), any()))
                .thenReturn(List.of(appt("Corte", segunda.minusMonths(1))));

        PricedItem p = service.priceFor(assinante(), PlanBenefit.TargetType.SERVICO, corteId,
                "Corte", new BigDecimal("35.00"), segunda);

        assertThat(p.isFree()).isTrue();
    }

    private Appointment appt(String service, LocalDate date) {
        return Appointment.builder().service(service).appointmentDate(date).build();
    }

    // --- Catalogo precificado para o bot ---

    @Test
    @DisplayName("catálogo do assinante sai com o preço do plano; o de quem não assina, cheio")
    void priceCatalog_appliesPlanToSubscriberOnly() {
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                ServiceOffering.builder().id(corteId).name("Corte").price(new BigDecimal("35.00")).active(true).build(),
                ServiceOffering.builder().id(UUID.randomUUID()).name("Barba").price(new BigDecimal("30.00")).active(true).build()));
        givenBenefits(benefit(PlanBenefit.TargetType.SERVICO, corteId, "100", null, null));

        List<PricedItem> doAssinante = service.priceCatalogFor(assinante(), segunda);
        List<PricedItem> doVisitante = service.priceCatalogFor(null, segunda);

        assertThat(doAssinante.get(0).isFree()).isTrue();          // corte gratis pelo plano
        assertThat(doAssinante.get(1).finalPrice()).isEqualByComparingTo("30.00"); // barba sem beneficio
        assertThat(doVisitante).allSatisfy(i -> assertThat(i.discounted()).isFalse());
    }

    @Test
    @DisplayName("catálogo vazio devolve lista vazia — o bot cai no texto livre das configurações")
    void priceCatalog_emptyCatalog_returnsEmpty() {
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of());

        assertThat(service.priceCatalogFor(assinante(), segunda)).isEmpty();
    }
}