package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.dto.OnboardingRequest;
import com.razor.api.model.Barbershop;
import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.repository.TenantUserRepository;
import com.razor.api.tenant.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Testes do {@link BarbershopProvisioningService}: cria o tenant, semeia catalogo/barbeiro/admins
 * no contexto do tenant recem-criado e rejeita conflitos de unicidade — tudo com mocks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BarbershopProvisioningServiceTest {

    @Mock private BarbershopRepository barbershopRepository;
    @Mock private ServiceCatalogService serviceCatalogService;
    @Mock private ProfessionalService professionalService;
    @Mock private TenantAdminService tenantAdminService;
    @Mock private TenantUserRepository tenantUserRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private com.razor.api.repository.BarbershopSettingsRepository settingsRepository;

    @InjectMocks private BarbershopProvisioningService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private OnboardingRequest request(String slug, String instance, String prof, List<String> admins) {
        return new OnboardingRequest("Barbearia Nova", slug, instance, prof, admins, "dono@loja.com", "senha1234");
    }

    private void givenSaveAssignsId() {
        when(barbershopRepository.save(any())).thenAnswer(inv -> {
            Barbershop b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });
    }

    @Test
    @DisplayName("provisiona: cria a barbearia, semeia 3 serviços, 1 barbeiro e os admins informados")
    void provision_createsShopSeedsAndAdmins() {
        when(barbershopRepository.findBySlug("loja-2")).thenReturn(Optional.empty());
        when(barbershopRepository.findByWhatsappInstance("loja2-wa")).thenReturn(Optional.empty());
        givenSaveAssignsId();

        when(passwordEncoder.encode("senha1234")).thenReturn("hash-bcrypt");

        Barbershop shop = service.provision(
                request("loja-2", "loja2-wa", "Rafael", List.of("5521970001111", "5521970002222")));

        assertThat(shop.getId()).isNotNull();
        assertThat(shop.getSlug()).isEqualTo("loja-2");
        assertThat(shop.getWhatsappInstance()).isEqualTo("loja2-wa");
        verify(serviceCatalogService, times(3)).create(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        verify(professionalService).create(eq("Rafael"), isNull());
        verify(tenantAdminService).add(eq("5521970001111"), isNull());
        verify(tenantAdminService).add(eq("5521970002222"), isNull());

        // usuario OWNER criado com senha em hash e vinculado ao tenant
        ArgumentCaptor<TenantUser> userCap = ArgumentCaptor.forClass(TenantUser.class);
        verify(tenantUserRepository).save(userCap.capture());
        assertThat(userCap.getValue().getEmail()).isEqualTo("dono@loja.com");
        assertThat(userCap.getValue().getPasswordHash()).isEqualTo("hash-bcrypt");
        assertThat(userCap.getValue().getRole()).isEqualTo(UserRole.OWNER);
        assertThat(userCap.getValue().getBarbershopId()).isEqualTo(shop.getId());
        assertThat(TenantContext.isSet()).isFalse(); // contexto limpo ao final
    }

    @Test
    @DisplayName("email de dono já em uso é rejeitado (409)")
    void provision_duplicateEmail_throws() {
        when(barbershopRepository.findBySlug("loja-2")).thenReturn(Optional.empty());
        when(tenantUserRepository.existsByEmail("dono@loja.com")).thenReturn(true);

        assertThatThrownBy(() -> service.provision(request("loja-2", null, "Rafael", List.of("5521970001111"))))
                .isInstanceOf(OnboardingConflictException.class)
                .hasMessageContaining("email");
        verify(barbershopRepository, never()).save(any());
    }

    @Test
    @DisplayName("sem nome de profissional usa 'Barbeiro' como padrão")
    void provision_defaultProfessionalName() {
        when(barbershopRepository.findBySlug(any())).thenReturn(Optional.empty());
        givenSaveAssignsId();

        service.provision(request("loja-3", null, "  ", List.of("5521970003333")));

        verify(professionalService).create(eq("Barbeiro"), isNull());
    }

    @Test
    @DisplayName("slug já existente é rejeitado (409) e nada é provisionado")
    void provision_duplicateSlug_throws() {
        when(barbershopRepository.findBySlug("loja-2"))
                .thenReturn(Optional.of(Barbershop.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> service.provision(request("loja-2", "x", "Rafael", List.of("5521970001111"))))
                .isInstanceOf(OnboardingConflictException.class)
                .hasMessageContaining("slug");
        verify(barbershopRepository, never()).save(any());
        verify(serviceCatalogService, never()).create(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("instância de WhatsApp já em uso é rejeitada (409)")
    void provision_duplicateInstance_throws() {
        when(barbershopRepository.findBySlug("loja-2")).thenReturn(Optional.empty());
        when(barbershopRepository.findByWhatsappInstance("loja2-wa"))
                .thenReturn(Optional.of(Barbershop.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> service.provision(request("loja-2", "loja2-wa", "Rafael", List.of("5521970001111"))))
                .isInstanceOf(OnboardingConflictException.class)
                .hasMessageContaining("instancia");
        verify(barbershopRepository, never()).save(any());
    }

    @Test
    @DisplayName("cadastro pela landing: TRIAL com fim do teste, dono vira barbeiro e admin, nome do bot preenchido")
    void provisionTrial_createsTrialShop() {
        when(barbershopRepository.findBySlug("barbearia-do-ze")).thenReturn(Optional.empty());
        givenSaveAssignsId();
        when(passwordEncoder.encode("senha1234")).thenReturn("hash");
        when(tenantUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        java.time.LocalDateTime fim = java.time.LocalDateTime.of(2026, 12, 2, 10, 0);

        BarbershopProvisioningService.Provisioned p = service.provisionTrial(new BarbershopProvisioningService.TrialSignup(
                "Barbearia do Zé", "barbearia-do-ze", "Niterói", "EQUIPE", "José Silva",
                "Ze@Loja.com", "5521970001111", "senha1234", fim));

        Barbershop shop = p.shop();
        assertThat(shop.getStatus()).isEqualTo(Barbershop.STATUS_TRIAL);
        assertThat(shop.getTrialEndsAt()).isEqualTo(fim);
        assertThat(shop.getCity()).isEqualTo("Niterói");
        assertThat(shop.getSignupPlan()).isEqualTo("EQUIPE");
        assertThat(shop.getWhatsappInstance()).isNull(); // derivada do slug no primeiro QR
        verify(professionalService).create(eq("José Silva"), isNull());
        verify(tenantAdminService).add(eq("5521970001111"), isNull());
        assertThat(p.owner().getEmail()).isEqualTo("ze@loja.com");
        assertThat(p.owner().getName()).isEqualTo("José Silva");
        assertThat(p.owner().getRole()).isEqualTo(UserRole.OWNER);

        ArgumentCaptor<com.razor.api.model.BarbershopSettings> settings =
                ArgumentCaptor.forClass(com.razor.api.model.BarbershopSettings.class);
        verify(settingsRepository).save(settings.capture());
        assertThat(settings.getValue().getDisplayName()).isEqualTo("Barbearia do Zé");
        assertThat(settings.getValue().getBarbershopId()).isEqualTo(shop.getId());
    }
}
