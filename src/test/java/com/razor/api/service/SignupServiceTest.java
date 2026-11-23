package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.config.RazorProperties;
import com.razor.api.dto.SignupRequest;
import com.razor.api.model.Barbershop;
import com.razor.api.model.TenantUser;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.repository.TenantUserRepository;
import com.razor.api.service.BarbershopProvisioningService.Provisioned;
import com.razor.api.service.BarbershopProvisioningService.TrialSignup;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Cadastro sozinho pela landing (V49)")
class SignupServiceTest {

    @Mock private BarbershopProvisioningService provisioningService;
    @Mock private BarbershopRepository barbershopRepository;
    @Mock private TenantUserRepository tenantUserRepository;

    private SignupService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-18T13:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    @BeforeEach
    void setUp() {
        RazorProperties props = new RazorProperties();
        service = new SignupService(provisioningService, barbershopRepository, tenantUserRepository, props, clock);
        when(barbershopRepository.findBySlug(anyString())).thenReturn(Optional.empty());
        when(barbershopRepository.findByWhatsappInstance(anyString())).thenReturn(Optional.empty());
        when(provisioningService.provisionTrial(any())).thenAnswer(inv -> {
            TrialSignup s = inv.getArgument(0);
            Barbershop shop = Barbershop.builder().id(UUID.randomUUID()).name(s.name()).slug(s.slug())
                    .status(Barbershop.STATUS_TRIAL).trialEndsAt(s.trialEndsAt()).build();
            return new Provisioned(shop, TenantUser.builder().email(s.ownerEmail()).build());
        });
    }

    private SignupRequest req(String shop, String email, String password, String phone, String plan, String website) {
        return new SignupRequest(shop, "José Silva", email, password, phone, "Niterói", plan, website);
    }

    private TrialSignup captured() {
        ArgumentCaptor<TrialSignup> cap = ArgumentCaptor.forClass(TrialSignup.class);
        verify(provisioningService).provisionTrial(cap.capture());
        return cap.getValue();
    }

    @Test
    @DisplayName("cria em TRIAL de 14 dias, com slug do nome, e-mail normalizado, telefone canônico e plano")
    void signup_happyPath() {
        service.signup(req("Barbearia do Zé", " Ze@Loja.COM ", "senha1234", "(21) 97000-1111", "equipe", ""));

        TrialSignup s = captured();
        assertThat(s.slug()).isEqualTo("barbearia-do-ze");
        assertThat(s.ownerEmail()).isEqualTo("ze@loja.com");
        assertThat(s.ownerPhone()).isEqualTo("5521970001111");
        assertThat(s.plan()).isEqualTo("EQUIPE");
        assertThat(s.city()).isEqualTo("Niterói");
        assertThat(s.trialEndsAt()).isEqualTo(LocalDateTime.of(2026, 12, 2, 10, 0));
    }

    @Test
    @DisplayName("honeypot preenchido: recusa com mensagem genérica e não cria nada")
    void signup_honeypot() {
        assertThatThrownBy(() -> service.signup(req("Barbearia", "a@b.com", "senha1234", "21970001111", null, "http://spam")))
                .isInstanceOf(SignupService.SuspectedBotException.class);
        verify(provisioningService, never()).provisionTrial(any());
    }

    @Test
    @DisplayName("e-mail já cadastrado: 409 que leva ao login")
    void signup_duplicateEmail() {
        when(tenantUserRepository.existsByEmail("a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> service.signup(req("Barbearia", "A@b.com", "senha1234", "21970001111", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("login");
        verify(provisioningService, never()).provisionTrial(any());
    }

    @Test
    @DisplayName("senha curta, só espaços ou WhatsApp inválido são recusados")
    void signup_validation() {
        assertThatThrownBy(() -> service.signup(req("Barbearia", "a@b.com", "1234567", "21970001111", null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("8");
        assertThatThrownBy(() -> service.signup(req("Barbearia", "a@b.com", "          ", "21970001111", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.signup(req("Barbearia", "a@b.com", "senha1234", "12345", null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("WhatsApp");
        verify(provisioningService, never()).provisionTrial(any());
    }

    @Test
    @DisplayName("plano desconhecido não barra o cadastro: só não é gravado")
    void signup_unknownPlan() {
        service.signup(req("Barbearia Top", "a@b.com", "senha1234", "21970001111", "diamante", null));
        assertThat(captured().plan()).isNull();
    }

    @Test
    @DisplayName("slug: sem acento, sem símbolo, com sufixo quando já existe (no slug ou como instância)")
    void availableSlug_suffix() {
        assertThat(SignupService.slugify("  Barbearia São João & Cia!! ")).isEqualTo("barbearia-sao-joao-e-cia");
        when(barbershopRepository.findBySlug("barbearia-do-ze"))
                .thenReturn(Optional.of(Barbershop.builder().build()));
        when(barbershopRepository.findByWhatsappInstance("barbearia-do-ze-2"))
                .thenReturn(Optional.of(Barbershop.builder().build()));

        assertThat(service.availableSlug("Barbearia do Zé")).isEqualTo("barbearia-do-ze-3");
    }

    @Test
    @DisplayName("slug reservado, curto demais ou longo demais é ajustado")
    void availableSlug_reservedAndLength() {
        assertThat(service.availableSlug("Admin")).isEqualTo("barbearia-admin");
        assertThat(service.availableSlug("Zé")).isEqualTo("barbearia-ze");
        assertThat(service.availableSlug("💈💈")).isEqualTo("barbearia");
        String longo = service.availableSlug("Barbearia Muito Muito Muito Muito Comprida Demais Mesmo");
        assertThat(longo.length()).isLessThanOrEqualTo(SignupService.MAX_SLUG);
        assertThat(longo).doesNotEndWith("-").matches("^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$");
    }

    @Test
    @DisplayName("corrida no slug: a segunda tentativa escolhe outro; e-mail tomado na corrida vira 409")
    void signup_retryOnSlugRace() {
        org.mockito.Mockito.doThrow(new OnboardingConflictException("slug"))
                .doAnswer(inv -> new Provisioned(Barbershop.builder().build(), TenantUser.builder().build()))
                .when(provisioningService).provisionTrial(any());

        service.signup(req("Barbearia Top", "a@b.com", "senha1234", "21970001111", null, null));
        verify(provisioningService, times(2)).provisionTrial(any());
    }
}
