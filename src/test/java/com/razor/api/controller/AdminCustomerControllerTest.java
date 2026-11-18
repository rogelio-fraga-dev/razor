package com.razor.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.dto.CustomerPlanRequest;
import com.razor.api.dto.CustomerResponse;
import com.razor.api.dto.PageResponse;
import com.razor.api.model.Customer;
import com.razor.api.service.CustomerService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminCustomerControllerTest {

    @Mock private CustomerService customerService;
    @Mock private com.razor.api.service.CustomerSegmentService customerSegmentService;
    @Mock private com.razor.api.service.CustomerTagService customerTagService;
    @Mock private com.razor.api.service.CustomerStatusService customerStatusService;
    @InjectMocks private AdminCustomerController controller;

    private Customer c(String name) {
        return Customer.builder().id(UUID.randomUUID()).phoneNumber("5511999990000").name(name).fidelityPoints(3).build();
    }

    @Test
    @DisplayName("devolve a página do serviço com o nome do plano resolvido")
    void list_returnsPageWithPlanName() {
        UUID planId = UUID.randomUUID();
        Customer ana = c("Ana");
        ana.setSubscriptionPlanId(planId);
        when(customerSegmentService.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(ana, c("Zeca"))));
        when(customerService.planNamesByTenant()).thenReturn(Map.of(planId, "VIP"));

        PageResponse<CustomerResponse> out = controller.list(null, null, null, null, null, null);

        assertThat(out.items()).hasSize(2);
        assertThat(out.items().get(0).planName()).isEqualTo("VIP");
        assertThat(out.items().get(1).planName()).isNull(); // avulso
        assertThat(out.total()).isEqualTo(2);
    }

    @Test
    @DisplayName("a ordenação por nome desce para o banco — ordenar a página já recortada mentiria")
    void list_delegatesSortingToRepository() {
        when(customerSegmentService.search(any(), any(), any(), any(), any())).thenReturn(Page.empty());
        when(customerService.planNamesByTenant()).thenReturn(Map.of());

        controller.list(2, 15, "ana", CustomerService.CustomerFilter.SUBSCRIBERS, null, null);

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(customerSegmentService).search(
                eq("ana"), eq(CustomerService.CustomerFilter.SUBSCRIBERS), isNull(), isNull(), cap.capture());

        Pageable p = cap.getValue();
        assertThat(p.getPageNumber()).isEqualTo(2);
        assertThat(p.getPageSize()).isEqualTo(15);
        // Sem isto, a página 2 poderia começar antes do fim da 1 — "ordenado" por pedaço.
        assertThat(p.getSort().getOrderFor("name")).isNotNull();
    }

    @Test
    @DisplayName("busca e filtro ausentes viram base inteira, com o padrão de paginação")
    void list_defaults() {
        when(customerSegmentService.search(any(), any(), any(), any(), any())).thenReturn(Page.empty());
        when(customerService.planNamesByTenant()).thenReturn(Map.of());

        controller.list(null, null, null, null, null, null);

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(customerSegmentService).search(isNull(), eq(CustomerService.CustomerFilter.ALL), isNull(), isNull(), cap.capture());
        assertThat(cap.getValue().getPageNumber()).isZero();
        assertThat(cap.getValue().getPageSize()).isEqualTo(PageResponse.DEFAULT_SIZE);
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

    @Test
    @DisplayName("cada linha volta com as tags e o status calculado (V36)")
    void list_includesTagsAndStatus() {
        Customer ana = c("Ana");
        com.razor.api.model.CustomerTag vip = com.razor.api.model.CustomerTag.builder()
                .id(UUID.randomUUID()).name("VIP").color("#FFAA00").build();
        when(customerSegmentService.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(ana)));
        when(customerService.planNamesByTenant()).thenReturn(Map.of());
        when(customerTagService.tagsByCustomer(any())).thenReturn(Map.of(ana.getId(), List.of(vip)));
        when(customerStatusService.statuses()).thenReturn(
                Map.of(ana.getId(), com.razor.api.model.CustomerStatus.SUMIDO));

        PageResponse<CustomerResponse> out = controller.list(null, null, null, null, vip.getId(),
                com.razor.api.model.CustomerStatus.SUMIDO);

        CustomerResponse row = out.items().get(0);
        assertThat(row.tags()).extracting(com.razor.api.dto.CustomerTagResponse::name).containsExactly("VIP");
        assertThat(row.status()).isEqualTo(com.razor.api.model.CustomerStatus.SUMIDO);
        verify(customerSegmentService).search(isNull(), eq(CustomerService.CustomerFilter.ALL),
                eq(vip.getId()), eq(com.razor.api.model.CustomerStatus.SUMIDO), any());
    }
}
