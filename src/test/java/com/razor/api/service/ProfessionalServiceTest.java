package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Professional;
import com.razor.api.model.ServiceOffering;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.ServiceOfferingRepository;
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
    @Mock private ServiceOfferingRepository serviceOfferingRepository;
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
                .isInstanceOf(NotFoundException.class);
        verify(professionalRepository, never()).save(any());
    }

    @Test
    @DisplayName("update altera nome, telefone, status e comissão do profissional do tenant atual")
    void update_setsAllFields() {
        UUID id = UUID.randomUUID();
        Professional p = Professional.builder()
                .id(id).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID).name("Antigo").active(true).build();
        when(professionalRepository.findById(id)).thenReturn(Optional.of(p));
        when(professionalRepository.save(any(Professional.class))).thenAnswer(inv -> inv.getArgument(0));

        Professional saved = service.update(id, "Novo", "5511999998888", false, new BigDecimal("40"), null);

        assertThat(saved.getName()).isEqualTo("Novo");
        assertThat(saved.getPhone()).isEqualTo("5511999998888");
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getCommissionPercent()).isEqualByComparingTo("40");
    }

    @Test
    @DisplayName("update rejeita comissão fora de 0–100")
    void update_invalidCommission_throws() {
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), "X", null, true, new BigDecimal("150"), null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(professionalRepository, never()).save(any());
    }

    @Test
    @DisplayName("update em profissional inexistente lança NotFound")
    void update_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(professionalRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, "X", null, true, new BigDecimal("10"), null))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- perfil (V37) ----------

    private Professional owned(UUID id) {
        Professional p = Professional.builder()
                .id(id).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID).name("Diego Aparecido Santos").build();
        org.mockito.Mockito.lenient().when(professionalRepository.findById(id)).thenReturn(Optional.of(p));
        org.mockito.Mockito.lenient().when(professionalRepository.save(any(Professional.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        return p;
    }

    @Test
    @DisplayName("updateProfile grava foto, apelido, bio, especialidades, Instagram e serviços")
    void updateProfile_setsEverything() {
        UUID id = UUID.randomUUID();
        UUID barba = UUID.randomUUID();
        owned(id);
        when(serviceOfferingRepository.findByBarbershopId(TenantContext.DEFAULT_BARBERSHOP_ID))
                .thenReturn(List.of(ServiceOffering.builder().id(barba).name("Barba").build()));
        String foto = ProfessionalProfileRulesTest.jpegDataUri(2048);

        Professional saved = service.updateProfile(id, new ProfessionalService.ProfileUpdate(
                foto, " Diego ", "10 anos de navalha", List.of("Degradê", "Barba"), "@diego", List.of(barba)));

        assertThat(saved.getPhotoData()).isEqualTo(foto);
        assertThat(saved.displayName()).isEqualTo("Diego");
        assertThat(saved.getBio()).isEqualTo("10 anos de navalha");
        assertThat(saved.specialtyList()).containsExactly("Degradê", "Barba");
        assertThat(saved.getInstagramUrl()).isEqualTo("https://instagram.com/diego");
        assertThat(saved.getServiceIds()).containsExactly(barba);
    }

    @Test
    @DisplayName("updateProfile: nulo não mexe, vazio limpa")
    void updateProfile_nullKeeps_emptyClears() {
        UUID id = UUID.randomUUID();
        Professional p = owned(id);
        p.setPhotoData(ProfessionalProfileRulesTest.jpegDataUri(100));
        p.setNickname("Diego");
        p.getServiceIds().add(UUID.randomUUID());

        Professional saved = service.updateProfile(id,
                new ProfessionalService.ProfileUpdate(null, "", null, null, null, List.of()));

        assertThat(saved.getPhotoData()).isNotNull();          // nulo: mantida
        assertThat(saved.getNickname()).isNull();              // vazio: limpo
        assertThat(saved.displayName()).isEqualTo("Diego Aparecido Santos");
        assertThat(saved.servesAllServices()).isTrue();         // lista vazia = todos
    }

    @Test
    @DisplayName("updateProfile recusa foto inválida sem gravar nada")
    void updateProfile_invalidPhoto_rejectedWithoutSaving() {
        UUID id = UUID.randomUUID();
        owned(id);

        assertThatThrownBy(() -> service.updateProfile(id, new ProfessionalService.ProfileUpdate(
                "https://evil.example/x.jpg", "Diego", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(professionalRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProfile recusa serviço de outro tenant (isolamento)")
    void updateProfile_foreignService_rejected() {
        UUID id = UUID.randomUUID();
        owned(id);
        when(serviceOfferingRepository.findByBarbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.updateProfile(id, new ProfessionalService.ProfileUpdate(
                null, null, null, null, null, List.of(UUID.randomUUID()))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(professionalRepository, never()).save(any());
    }

    @Test
    @DisplayName("listActiveFor só devolve quem faz o serviço; sem restrição devolve todos")
    void listActiveFor_filtersByService() {
        UUID barba = UUID.randomUUID();
        UUID corte = UUID.randomUUID();
        Professional soCorte = Professional.builder().id(UUID.randomUUID()).name("A")
                .serviceIds(new java.util.HashSet<>(List.of(corte))).build();
        Professional todos = Professional.builder().id(UUID.randomUUID()).name("B").build();
        when(professionalRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(soCorte, todos));
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                ServiceOffering.builder().id(barba).name("Barba").build(),
                ServiceOffering.builder().id(corte).name("Corte").build()));

        assertThat(service.listActiveFor("Barba")).containsExactly(todos);
        assertThat(service.listActiveFor("Corte")).containsExactly(soCorte, todos);
        assertThat(service.listActiveFor(null)).containsExactly(soCorte, todos);
    }
}
