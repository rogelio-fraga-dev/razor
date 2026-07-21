package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Professional;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.tenant.TenantContext;
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
class ProfessionalServiceTest {

    @Mock private ProfessionalRepository professionalRepository;
    @InjectMocks private ProfessionalService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("listActive consulta os profissionais do tenant atual")
    void listActive_scopedToCurrentTenant() {
        UUID shop = UUID.randomUUID();
        TenantContext.set(shop);
        Professional ze = Professional.builder().barbershopId(shop).name("Zé").build();
        when(professionalRepository.findByBarbershopIdAndActiveTrue(shop)).thenReturn(List.of(ze));

        assertThat(service.listActive()).containsExactly(ze);
    }

    @Test
    @DisplayName("create salva o profissional com o barbershop_id do tenant atual")
    void create_setsTenant() {
        when(professionalRepository.save(any(Professional.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create("Maria", "5511911112222");

        ArgumentCaptor<Professional> cap = ArgumentCaptor.forClass(Professional.class);
        verify(professionalRepository).save(cap.capture());
        assertThat(cap.getValue().getBarbershopId()).isEqualTo(TenantContext.DEFAULT_BARBERSHOP_ID);
        assertThat(cap.getValue().getName()).isEqualTo("Maria");
        assertThat(cap.getValue().getPhone()).isEqualTo("5511911112222");
        assertThat(cap.getValue().isActive()).isTrue();
    }
}
