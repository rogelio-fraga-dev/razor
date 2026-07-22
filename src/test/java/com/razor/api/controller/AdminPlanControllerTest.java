package com.razor.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.dto.SubscriptionPlanRequest;
import com.razor.api.model.SubscriptionPlan;
import com.razor.api.service.SubscriptionPlanService;
import java.math.BigDecimal;
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
class AdminPlanControllerTest {

    @Mock private SubscriptionPlanService subscriptionPlanService;
    @InjectMocks private AdminPlanController controller;

    private SubscriptionPlan plan(UUID id, String name) {
        return SubscriptionPlan.builder().id(id).name(name).price(new BigDecimal("99.90")).active(true).build();
    }

    @Test
    @DisplayName("listar mapeia os planos")
    void list_maps() {
        when(subscriptionPlanService.listAll()).thenReturn(List.of(plan(UUID.randomUUID(), "VIP")));
        assertThat(controller.list()).hasSize(1);
    }

    @Test
    @DisplayName("criar retorna 201")
    void create_returns201() {
        UUID id = UUID.randomUUID();
        when(subscriptionPlanService.create(eq("VIP"), eq(new BigDecimal("99.90")), isNull())).thenReturn(plan(id, "VIP"));

        var resp = controller.create(new SubscriptionPlanRequest("VIP", new BigDecimal("99.90"), null, null));

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody().id()).isEqualTo(id);
    }

    @Test
    @DisplayName("atualizar sem 'active' assume ativo")
    void update_defaultsActive() {
        UUID id = UUID.randomUUID();
        when(subscriptionPlanService.update(eq(id), eq("VIP"), eq(new BigDecimal("99.90")), isNull(), eq(true)))
                .thenReturn(plan(id, "VIP"));

        controller.update(id, new SubscriptionPlanRequest("VIP", new BigDecimal("99.90"), null, null));

        verify(subscriptionPlanService).update(eq(id), eq("VIP"), eq(new BigDecimal("99.90")), isNull(), eq(true));
    }
}
