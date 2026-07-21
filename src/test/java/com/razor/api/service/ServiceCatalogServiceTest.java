package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.ServiceOffering;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceCatalogServiceTest {

    @Mock private ServiceOfferingRepository serviceOfferingRepository;
    @InjectMocks private ServiceCatalogService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("listActive consulta o catálogo do tenant atual")
    void listActive_scopedToCurrentTenant() {
        UUID shop = UUID.randomUUID();
        TenantContext.set(shop);
        ServiceOffering corte = ServiceOffering.builder().barbershopId(shop).name("Corte").build();
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(shop)).thenReturn(List.of(corte));

        assertThat(service.listActive()).containsExactly(corte);
    }

    @Test
    @DisplayName("create salva o serviço com o barbershop_id do tenant atual")
    void create_setsTenant() {
        when(serviceOfferingRepository.save(any(ServiceOffering.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create("Barba", new BigDecimal("30.00"), 30);

        ArgumentCaptor<ServiceOffering> cap = ArgumentCaptor.forClass(ServiceOffering.class);
        verify(serviceOfferingRepository).save(cap.capture());
        assertThat(cap.getValue().getBarbershopId()).isEqualTo(TenantContext.DEFAULT_BARBERSHOP_ID);
        assertThat(cap.getValue().getName()).isEqualTo("Barba");
        assertThat(cap.getValue().getPrice()).isEqualByComparingTo("30.00");
        assertThat(cap.getValue().getDurationMinutes()).isEqualTo(30);
        assertThat(cap.getValue().isActive()).isTrue();
    }
}
