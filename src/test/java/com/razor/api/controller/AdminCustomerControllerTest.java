package com.razor.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.razor.api.dto.CustomerPlanRequest;
import com.razor.api.dto.CustomerResponse;
import com.razor.api.model.Customer;
import com.razor.api.service.CustomerService;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminCustomerControllerTest {

    @Mock private CustomerService customerService;
    @InjectMocks private AdminCustomerController controller;

    private Customer c(String name) {
        return Customer.builder().id(UUID.randomUUID()).phoneNumber("5511999990000").name(name).fidelityPoints(3).build();
    }

    @Test
    @DisplayName("lista os clientes do tenant ordenados por nome, com o nome do plano resolvido")
    void list_sortedByNameWithPlan() {
        UUID planId = UUID.randomUUID();
        Customer ana = c("Ana");
        ana.setSubscriptionPlanId(planId);
        when(customerService.listCustomers()).thenReturn(List.of(c("Zeca"), ana));
        when(customerService.planNamesByTenant()).thenReturn(Map.of(planId, "VIP"));

        List<CustomerResponse> out = controller.list();

        assertThat(out).hasSize(2);
        assertThat(out.get(0).name()).isEqualTo("Ana");
        assertThat(out.get(0).planName()).isEqualTo("VIP");
        assertThat(out.get(1).name()).isEqualTo("Zeca");
        assertThat(out.get(1).planName()).isNull(); // avulso
    }

    @Test
    @DisplayName("assignPlan devolve o cliente com o nome do plano vinculado")
    void assignPlan_returnsPlanName() {
        UUID planId = UUID.randomUUID();
        Customer ana = c("Ana");
        ana.setSubscriptionPlanId(planId);
        when(customerService.assignPlan(ana.getId(), planId)).thenReturn(ana);
        when(customerService.planNamesByTenant()).thenReturn(Map.of(planId, "VIP"));

        CustomerResponse out = controller.assignPlan(ana.getId(), new CustomerPlanRequest(planId));

        assertThat(out.planId()).isEqualTo(planId);
        assertThat(out.planName()).isEqualTo("VIP");
    }
}
