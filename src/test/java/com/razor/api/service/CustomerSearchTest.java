package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.SubscriptionPlanRepository;
import com.razor.api.state.StateStore;
import com.razor.api.tenant.TenantContext;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Busca e totais da base de clientes (resolvidos no banco)")
class CustomerSearchTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock private StateStore stateStore;
    @InjectMocks private CustomerService service;

    private final PageRequest pageable = PageRequest.of(0, 20);

    @Test
    @DisplayName("o termo vira LIKE em minúsculas — a busca não pode diferenciar maiúsculas")
    void search_lowercasesAndWrapsTerm() {
        when(customerRepository.search(any(), any(), anyBool(), any())).thenReturn(Page.empty());
        when(customerRepository.searchCreatedSince(any(), any(), anyBool(), any(), any()))
                .thenReturn(Page.empty());

        service.searchCustomers("  AnA  ", CustomerService.CustomerFilter.ALL, pageable);

        verify(customerRepository).search(
                eq(TenantContext.getCurrent()), eq("%ana%"), eq(false), eq(pageable));
    }

    @Test
    @DisplayName("busca vazia desliga o filtro em vez de virar '%%'")
    void search_blankTerm_isNull() {
        when(customerRepository.search(any(), any(), anyBool(), any())).thenReturn(Page.empty());
        when(customerRepository.searchCreatedSince(any(), any(), anyBool(), any(), any()))
                .thenReturn(Page.empty());

        service.searchCustomers("   ", CustomerService.CustomerFilter.ALL, pageable);

        // '%%' casaria tudo do mesmo jeito, mas obrigaria o Postgres a varrer com LIKE em vez
        // de pular a condição inteira.
        verify(customerRepository).search(any(), isNull(), eq(false), any());
    }

    @Test
    @DisplayName("filtro de assinantes vira a flag, não um segundo filtro no Java")
    void search_subscribersFilter() {
        when(customerRepository.search(any(), any(), anyBool(), any())).thenReturn(Page.empty());
        when(customerRepository.searchCreatedSince(any(), any(), anyBool(), any(), any()))
                .thenReturn(Page.empty());

        service.searchCustomers(null, CustomerService.CustomerFilter.SUBSCRIBERS, pageable);

        verify(customerRepository).search(any(), isNull(), eq(true), any());
    }

    @Test
    @DisplayName("filtro de novos manda a data de corte da janela de 30 dias")
    void search_newFilter_sendsCutoff() {
        when(customerRepository.search(any(), any(), anyBool(), any())).thenReturn(Page.empty());
        when(customerRepository.searchCreatedSince(any(), any(), anyBool(), any(), any()))
                .thenReturn(Page.empty());
        LocalDateTime antes = LocalDateTime.now().minusDays(CustomerService.NEW_WINDOW_DAYS);

        service.searchCustomers(null, CustomerService.CustomerFilter.NEW, pageable);

        ArgumentCaptor<LocalDateTime> cap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(customerRepository).searchCreatedSince(any(), isNull(), eq(false), cap.capture(), any());
        assertThat(cap.getValue())
                .isCloseTo(antes, org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("os totais do topo vêm de contagens no banco, não da página carregada")
    void summary_countsInDatabase() {
        when(customerRepository.countByBarbershopId(any())).thenReturn(412L);
        when(customerRepository.countByBarbershopIdAndSubscriptionPlanIdIsNotNull(any())).thenReturn(37L);
        when(customerRepository.countByBarbershopIdAndCreatedAtGreaterThanEqual(any(), any())).thenReturn(9L);

        var summary = service.customerSummary();

        // Deduzir isso de uma página de 20 daria "3 assinantes" numa base com 412.
        assertThat(summary.total()).isEqualTo(412L);
        assertThat(summary.subscribers()).isEqualTo(37L);
        assertThat(summary.newInWindow()).isEqualTo(9L);
    }

    private static boolean anyBool() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }
}
