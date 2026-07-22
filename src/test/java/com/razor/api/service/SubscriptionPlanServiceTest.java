package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.SubscriptionPlan;
import com.razor.api.repository.SubscriptionPlanRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionPlanServiceTest {

    @Mock private SubscriptionPlanRepository subscriptionPlanRepository;
    @InjectMocks private SubscriptionPlanService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("create salva o plano no tenant atual")
    void create_setsTenant() {
        when(subscriptionPlanRepository.save(any(SubscriptionPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create("VIP", new BigDecimal("99.90"), "Cortes ilimitados");

        ArgumentCaptor<SubscriptionPlan> cap = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository).save(cap.capture());
        assertThat(cap.getValue().getBarbershopId()).isEqualTo(TenantContext.DEFAULT_BARBERSHOP_ID);
        assertThat(cap.getValue().getName()).isEqualTo("VIP");
        assertThat(cap.getValue().getPrice()).isEqualByComparingTo("99.90");
        assertThat(cap.getValue().isActive()).isTrue();
    }

    @Test
    @DisplayName("update altera os campos do plano do tenant atual")
    void update_setsFields() {
        UUID id = UUID.randomUUID();
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .id(id).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID).name("VIP").build();
        when(subscriptionPlanRepository.findById(id)).thenReturn(Optional.of(plan));
        when(subscriptionPlanRepository.save(any(SubscriptionPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionPlan saved = service.update(id, "Premium", new BigDecimal("149.90"), "Tudo incluso", false);

        assertThat(saved.getName()).isEqualTo("Premium");
        assertThat(saved.getPrice()).isEqualByComparingTo("149.90");
        assertThat(saved.isActive()).isFalse();
    }

    @Test
    @DisplayName("update rejeita plano de outro tenant com NotFound")
    void update_otherTenant_throws() {
        UUID id = UUID.randomUUID();
        SubscriptionPlan deOutro = SubscriptionPlan.builder().id(id).barbershopId(UUID.randomUUID()).name("X").build();
        when(subscriptionPlanRepository.findById(id)).thenReturn(Optional.of(deOutro));

        assertThatThrownBy(() -> service.update(id, "Y", new BigDecimal("10"), null, true))
                .isInstanceOf(NotFoundException.class);
    }
}
