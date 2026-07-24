package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.razor.api.model.Customer;
import com.razor.api.model.SubscriptionPlan;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.SubscriptionPlanRepository;
import com.razor.api.tenant.TenantContext;
import java.util.List;
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

/**
 * Testes do {@link CustomerService} (Fase 1, increment 3): identidade do cliente
 * escopada por tenant — lookup por (tenant, telefone), criacao com barbershop_id e
 * estado de pausa isolado entre barbearias.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private SubscriptionPlanRepository subscriptionPlanRepository;
    // Store REAL (nao mock): estes testes exercitam pausa/expiracao de verdade.
    @org.mockito.Spy
    private com.razor.api.state.StateStore stateStore =
            new com.razor.api.state.InMemoryStateStore(new com.fasterxml.jackson.databind.ObjectMapper());
    @InjectMocks private CustomerService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("findOrCreate retorna o cliente existente do tenant sem salvar")
    void findOrCreate_existing_returnsWithoutSaving() {
        UUID shop = TenantContext.getCurrent();
        Customer c = Customer.builder().id(UUID.randomUUID()).barbershopId(shop).phoneNumber("555").build();
        when(customerRepository.findByBarbershopIdAndPhoneNumber(shop, "555")).thenReturn(Optional.of(c));

        Customer result = service.findOrCreateCustomer("555", "Ignorado");

        assertThat(result).isSameAs(c);
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreate cria o cliente com o barbershop_id do tenant atual")
    void findOrCreate_new_savesWithTenant() {
        when(customerRepository.findByBarbershopIdAndPhoneNumber(any(UUID.class), eq("555")))
                .thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.findOrCreateCustomer("555", "João");

        ArgumentCaptor<Customer> cap = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(cap.capture());
        assertThat(cap.getValue().getBarbershopId()).isEqualTo(TenantContext.DEFAULT_BARBERSHOP_ID);
        assertThat(cap.getValue().getPhoneNumber()).isEqualTo("555");
        assertThat(cap.getValue().getName()).isEqualTo("João");
    }

    @Test
    @DisplayName("pausa/retomada e listagem operam sobre o tenant atual")
    void pause_resume_scoped() {
        service.pauseCustomer("555", 10);

        assertThat(service.isCustomerPaused("555")).isTrue();
        assertThat(service.getPausedPhones()).containsExactly("555");

        service.resumeCustomer("555");

        assertThat(service.isCustomerPaused("555")).isFalse();
        assertThat(service.getPausedPhones()).isEmpty();
    }

    @Test
    @DisplayName("pausa é isolada entre tenants (mesmo telefone)")
    void pause_isolatedBetweenTenants() {
        // pausa no tenant padrão
        service.pauseCustomer("555", 10);

        // outro tenant não vê a pausa
        TenantContext.set(UUID.randomUUID());
        assertThat(service.isCustomerPaused("555")).isFalse();
        assertThat(service.getPausedPhones()).isEmpty();

        // de volta ao tenant padrão, a pausa continua valendo
        TenantContext.clear();
        assertThat(service.isCustomerPaused("555")).isTrue();
    }

    @Test
    @DisplayName("listCustomers consulta a base do tenant atual")
    void listCustomers_scopedToTenant() {
        Customer c = Customer.builder().id(UUID.randomUUID()).phoneNumber("555").name("Ana").build();
        when(customerRepository.findByBarbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)).thenReturn(List.of(c));

        assertThat(service.listCustomers()).containsExactly(c);
    }

    @Test
    @DisplayName("assignPlan vincula o cliente ao plano do tenant")
    void assignPlan_setsPlan() {
        UUID shop = TenantContext.getCurrent();
        UUID planId = UUID.randomUUID();
        Customer c = Customer.builder().id(UUID.randomUUID()).barbershopId(shop).phoneNumber("555").build();
        SubscriptionPlan plan = SubscriptionPlan.builder().id(planId).barbershopId(shop).name("VIP").build();
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer saved = service.assignPlan(c.getId(), planId);

        assertThat(saved.getSubscriptionPlanId()).isEqualTo(planId);
    }

    @Test
    @DisplayName("assignPlan com planId nulo desvincula (cliente volta a avulso)")
    void assignPlan_nullRemoves() {
        UUID shop = TenantContext.getCurrent();
        Customer c = Customer.builder().id(UUID.randomUUID()).barbershopId(shop).phoneNumber("555")
                .subscriptionPlanId(UUID.randomUUID()).build();
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer saved = service.assignPlan(c.getId(), null);

        assertThat(saved.getSubscriptionPlanId()).isNull();
        verify(subscriptionPlanRepository, never()).findById(any());
    }

    @Test
    @DisplayName("assignPlan rejeita cliente de outro tenant com NotFound")
    void assignPlan_otherTenantCustomer_throws() {
        Customer deOutro = Customer.builder().id(UUID.randomUUID()).barbershopId(UUID.randomUUID()).phoneNumber("555").build();
        when(customerRepository.findById(deOutro.getId())).thenReturn(Optional.of(deOutro));

        assertThatThrownBy(() -> service.assignPlan(deOutro.getId(), null))
                .isInstanceOf(NotFoundException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignPlan rejeita plano de outro tenant com NotFound")
    void assignPlan_otherTenantPlan_throws() {
        UUID shop = TenantContext.getCurrent();
        UUID planId = UUID.randomUUID();
        Customer c = Customer.builder().id(UUID.randomUUID()).barbershopId(shop).phoneNumber("555").build();
        SubscriptionPlan deOutro = SubscriptionPlan.builder().id(planId).barbershopId(UUID.randomUUID()).name("X").build();
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(deOutro));

        assertThatThrownBy(() -> service.assignPlan(c.getId(), planId))
                .isInstanceOf(NotFoundException.class);
        verify(customerRepository, never()).save(any());
    }
}
