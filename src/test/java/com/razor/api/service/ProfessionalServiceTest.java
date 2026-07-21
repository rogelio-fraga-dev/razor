package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Professional;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
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

    @Test
    @DisplayName("updateCommission define o percentual do profissional do tenant atual")
    void updateCommission_setsPercent() {
        UUID id = UUID.randomUUID();
        Professional p = Professional.builder()
                .id(id).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID).name("Zé").build();
        when(professionalRepository.findById(id)).thenReturn(Optional.of(p));
        when(professionalRepository.save(any(Professional.class))).thenAnswer(inv -> inv.getArgument(0));

        Professional saved = service.updateCommission(id, new BigDecimal("45"));

        assertThat(saved.getCommissionPercent()).isEqualByComparingTo("45");
        verify(professionalRepository).save(p);
    }

    @Test
    @DisplayName("updateCommission rejeita percentual fora de 0–100")
    void updateCommission_outOfRange_throws() {
        assertThatThrownBy(() -> service.updateCommission(UUID.randomUUID(), new BigDecimal("150")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(professionalRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateCommission rejeita profissional de outro tenant (isolamento)")
    void updateCommission_otherTenant_throws() {
        UUID id = UUID.randomUUID();
        Professional deOutroTenant = Professional.builder()
                .id(id).barbershopId(UUID.randomUUID()).name("Estranho").build();
        when(professionalRepository.findById(id)).thenReturn(Optional.of(deOutroTenant));

        assertThatThrownBy(() -> service.updateCommission(id, new BigDecimal("30")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(professionalRepository, never()).save(any());
    }
}
