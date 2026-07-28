package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.PlanBenefit;
import com.razor.api.model.ServiceOffering;
import com.razor.api.model.SubscriptionPlan;
import com.razor.api.repository.PlanBenefitRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.repository.SubscriptionPlanRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

/** Testes dos beneficios de plano e dos tres planos sugeridos (V27). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanBenefitServiceTest {

    @Mock private PlanBenefitRepository benefitRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private ServiceOfferingRepository serviceOfferingRepository;

    @InjectMocks private PlanBenefitService service;

    private final UUID planId = UUID.randomUUID();
    private final UUID corteId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void givenPlanExists() {
        when(planRepository.findById(planId)).thenReturn(Optional.of(SubscriptionPlan.builder()
                .id(planId).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID).name("Plano").build()));
        when(benefitRepository.save(any(PlanBenefit.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- upsert ----------

    @Test
    @DisplayName("cria o benefício quando o alvo ainda não tem um")
    void upsert_createsWhenAbsent() {
        givenPlanExists();
        when(benefitRepository.findByPlanId(planId)).thenReturn(List.of());

        PlanBenefit b = service.upsert(planId, PlanBenefit.TargetType.SERVICO, corteId,
                new BigDecimal("100"), null, "1,2,3");

        assertThat(b.getDiscountPercent()).isEqualByComparingTo("100");
        assertThat(b.getWeekdays()).isEqualTo("1,2,3");
        assertThat(b.isFree()).isTrue();
    }

    @Test
    @DisplayName("segundo envio para o mesmo alvo edita em vez de duplicar")
    void upsert_updatesExistingTarget() {
        givenPlanExists();
        PlanBenefit existente = PlanBenefit.builder()
                .id(UUID.randomUUID()).planId(planId)
                .targetType(PlanBenefit.TargetType.SERVICO).targetId(corteId)
                .discountPercent(new BigDecimal("10")).build();
        when(benefitRepository.findByPlanId(planId)).thenReturn(List.of(existente));

        PlanBenefit b = service.upsert(planId, PlanBenefit.TargetType.SERVICO, corteId,
                new BigDecimal("50"), null, null);

        assertThat(b.getId()).isEqualTo(existente.getId()); // mesma linha
        assertThat(b.getDiscountPercent()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("desconto fora de 0–100 e cota não positiva são recusados")
    void upsert_rejectsInvalidValues() {
        givenPlanExists();
        when(benefitRepository.findByPlanId(planId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.upsert(planId, PlanBenefit.TargetType.SERVICO, corteId,
                new BigDecimal("150"), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.upsert(planId, PlanBenefit.TargetType.SERVICO, corteId,
                new BigDecimal("-1"), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.upsert(planId, PlanBenefit.TargetType.SERVICO, corteId,
                new BigDecimal("10"), 0, null)).isInstanceOf(IllegalArgumentException.class);

        verify(benefitRepository, never()).save(any());
    }

    @Test
    @DisplayName("plano de outra barbearia não é encontrado")
    void upsert_otherTenantPlan_notFound() {
        when(planRepository.findById(planId)).thenReturn(Optional.of(SubscriptionPlan.builder()
                .id(planId).barbershopId(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> service.upsert(planId, PlanBenefit.TargetType.SERVICO, corteId,
                new BigDecimal("10"), null, null)).isInstanceOf(NotFoundException.class);
    }

    // ---------- planos sugeridos ----------

    private void givenCatalogAndPlanStore() {
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                ServiceOffering.builder().id(corteId).name("Corte de cabelo").active(true).build(),
                ServiceOffering.builder().id(UUID.randomUUID()).name("Barba").active(true).build()));

        List<SubscriptionPlan> store = new ArrayList<>();
        when(planRepository.findByBarbershopId(any())).thenAnswer(inv -> List.copyOf(store));
        when(planRepository.save(any(SubscriptionPlan.class))).thenAnswer(inv -> {
            SubscriptionPlan p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            store.removeIf(x -> x.getId().equals(p.getId()));
            store.add(p);
            return p;
        });
        when(planRepository.findById(any())).thenAnswer(inv ->
                store.stream().filter(p -> p.getId().equals(inv.getArgument(0))).findFirst());
        when(benefitRepository.findByPlanId(any())).thenReturn(List.of());
        when(benefitRepository.save(any(PlanBenefit.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("cria os três planos sugeridos com preços realistas e crescentes")
    void createSuggestedPlans_createsThreeTiers() {
        givenCatalogAndPlanStore();

        List<SubscriptionPlan> planos = service.createSuggestedPlans();

        assertThat(planos).hasSize(3);
        assertThat(planos.get(0).getPrice()).isEqualByComparingTo("69.90");
        assertThat(planos.get(1).getPrice()).isEqualByComparingTo("94.90");
        assertThat(planos.get(2).getPrice()).isEqualByComparingTo("119.90");
        // preço sobe junto com o benefício
        assertThat(planos.get(0).getPrice()).isLessThan(planos.get(1).getPrice());
        assertThat(planos.get(1).getPrice()).isLessThan(planos.get(2).getPrice());
    }

    @Test
    @DisplayName("o plano de entrada fica restrito a segunda, terça e quarta")
    void createSuggestedPlans_bronzeIsWeekdayRestricted() {
        givenCatalogAndPlanStore();

        service.createSuggestedPlans();

        verify(benefitRepository).save(org.mockito.ArgumentMatchers.argThat(b ->
                "1,2,3".equals(b.getWeekdays())
                        && b.getDiscountPercent().compareTo(new BigDecimal("100")) == 0));
    }

    @Test
    @DisplayName("cada plano define a antecedência de agenda do assinante")
    void createSuggestedPlans_setBookingWindow() {
        givenCatalogAndPlanStore();

        List<SubscriptionPlan> planos = service.createSuggestedPlans();

        assertThat(planos).allSatisfy(p -> assertThat(p.getBookingWindowDays()).isNotNull());
        assertThat(planos.get(2).getBookingWindowDays())
                .isGreaterThan(planos.get(0).getBookingWindowDays()); // plano melhor agenda antes
    }

    @Test
    @DisplayName("rodar duas vezes não duplica os planos")
    void createSuggestedPlans_isIdempotentByName() {
        givenCatalogAndPlanStore();

        service.createSuggestedPlans();
        List<SubscriptionPlan> segunda = service.createSuggestedPlans();

        assertThat(segunda).hasSize(3);
        assertThat(planRepository.findByBarbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)).hasSize(3);
    }

    @Test
    @DisplayName("catálogo sem 'barba' não quebra a criação dos planos")
    void createSuggestedPlans_missingServiceIsTolerated() {
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                ServiceOffering.builder().id(corteId).name("Corte").active(true).build()));
        List<SubscriptionPlan> store = new ArrayList<>();
        when(planRepository.findByBarbershopId(any())).thenAnswer(inv -> List.copyOf(store));
        when(planRepository.save(any(SubscriptionPlan.class))).thenAnswer(inv -> {
            SubscriptionPlan p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            store.add(p);
            return p;
        });
        when(planRepository.findById(any())).thenAnswer(inv ->
                store.stream().filter(p -> p.getId().equals(inv.getArgument(0))).findFirst());
        when(benefitRepository.findByPlanId(any())).thenReturn(List.of());
        when(benefitRepository.save(any(PlanBenefit.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.createSuggestedPlans()).hasSize(3);
    }
}
