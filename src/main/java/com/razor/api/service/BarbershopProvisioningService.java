package com.razor.api.service;

import com.razor.api.dto.OnboardingRequest;
import com.razor.api.model.Barbershop;
import com.razor.api.model.BarbershopSettings;
import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.repository.BarbershopSettingsRepository;
import com.razor.api.repository.TenantUserRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisiona uma nova barbearia (tenant) no onboarding self-service: cria o registro do tenant,
 * semeia o catalogo de servicos padrao, o barbeiro padrao e os admins informados.
 *
 * <p>Reutiliza os servicos ja escopados por tenant ({@link ServiceCatalogService},
 * {@link ProfessionalService}, {@link TenantAdminService}) definindo o {@link TenantContext} para
 * a barbearia recem-criada durante o provisionamento. Tudo em uma unica transacao.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BarbershopProvisioningService {

    private final BarbershopRepository barbershopRepository;
    private final ServiceCatalogService serviceCatalogService;
    private final ProfessionalService professionalService;
    private final TenantAdminService tenantAdminService;
    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;

    private final BarbershopSettingsRepository settingsRepository;

    /**
     * Cadastro sozinho pela landing (V49): barbearia em TRIAL + dono. Diferente do provisionamento
     * da plataforma, o dono nao escolhe slug nem instancia (o slug vem pronto do
     * {@link SignupService}; a instancia e derivada do slug no primeiro QR) e o WhatsApp
     * informado vira o numero de admin — e o celular dele, nao o da barbearia.
     */
    public record TrialSignup(String name, String slug, String city, String plan, String ownerName,
                              String ownerEmail, String ownerPhone, String ownerPassword,
                              LocalDateTime trialEndsAt) {
    }

    /** O que o cadastro criou: a barbearia e o usuario dono (para o login automatico). */
    public record Provisioned(Barbershop shop, TenantUser owner) {
    }

    @Transactional
    public Barbershop provision(OnboardingRequest request) {
        return create(request.name(), request.slug(), normalizeBlankToNull(request.whatsappInstance()),
                request.professionalName(), request.adminPhones(), request.ownerEmail(),
                request.ownerPassword(), request.name(), b -> { }).shop();
    }

    @Transactional
    public Provisioned provisionTrial(TrialSignup s) {
        Provisioned p = create(s.name(), s.slug(), null, s.ownerName(), List.of(s.ownerPhone()),
                s.ownerEmail(), s.ownerPassword(), s.ownerName(), b -> b
                        .status(Barbershop.STATUS_TRIAL)
                        .trialEndsAt(s.trialEndsAt())
                        .city(s.city())
                        .signupPlan(s.plan()));
        // O nome que o bot usa ao falar com o cliente ja nasce preenchido: e o primeiro campo do
        // assistente, e o dono acabou de digita-lo no cadastro.
        settingsRepository.save(BarbershopSettings.builder()
                .barbershopId(p.shop().getId())
                .displayName(s.name())
                .build());
        return p;
    }

    private Provisioned create(String name, String slug, String instance, String professionalName,
                               List<String> adminPhones, String rawOwnerEmail, String ownerPassword,
                               String ownerName, Consumer<Barbershop.BarbershopBuilder> customizer) {
        String ownerEmail = AuthService.normalizeEmail(rawOwnerEmail);

        if (barbershopRepository.findBySlug(slug).isPresent()) {
            throw new OnboardingConflictException("Ja existe uma barbearia com o slug '" + slug + "'.");
        }
        if (instance != null && barbershopRepository.findByWhatsappInstance(instance).isPresent()) {
            throw new OnboardingConflictException("A instancia de WhatsApp '" + instance + "' ja esta em uso.");
        }
        if (tenantUserRepository.existsByEmail(ownerEmail)) {
            throw new OnboardingConflictException("O email '" + ownerEmail + "' ja esta em uso.");
        }

        Barbershop.BarbershopBuilder builder = Barbershop.builder()
                .name(name)
                .slug(slug)
                .whatsappInstance(instance)
                .active(true);
        customizer.accept(builder);
        Barbershop shop = barbershopRepository.save(builder.build());

        // O TenantContext do onboarding nao vem de um webhook; escopamos manualmente para que os
        // servicos por-tenant gravem tudo na barbearia recem-criada, e limpamos ao final.
        try {
            TenantContext.set(shop.getId());
            seedCatalogoPadrao();
            professionalService.create(defaultProfessionalName(professionalName), null);
            for (String phone : adminPhones) {
                tenantAdminService.add(phone, null);
            }
        } finally {
            TenantContext.clear();
        }

        // Usuario dono (login do painel web) — fora do TenantContext: o vinculo e o barbershopId.
        TenantUser owner = tenantUserRepository.save(TenantUser.builder()
                .barbershopId(shop.getId())
                .email(ownerEmail)
                .passwordHash(passwordEncoder.encode(ownerPassword))
                .name(ownerName)
                .role(UserRole.OWNER)
                .active(true)
                .build());

        log.info("[ONBOARDING] Barbearia '{}' (slug '{}') provisionada: id {} (owner {})",
                shop.getName(), slug, shop.getId(), ownerEmail);
        return new Provisioned(shop, owner);
    }

    /** Catalogo inicial, alinhado ao seed do tenant padrao (V5). */
    private void seedCatalogoPadrao() {
        serviceCatalogService.create("Corte de Cabelo", new BigDecimal("35.00"), 30);
        serviceCatalogService.create("Barba", new BigDecimal("30.00"), 30);
        serviceCatalogService.create("Corte + Barba (Combo)", new BigDecimal("60.00"), 60);
    }

    private String defaultProfessionalName(String provided) {
        return (provided == null || provided.isBlank()) ? "Barbeiro" : provided.trim();
    }

    private String normalizeBlankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
