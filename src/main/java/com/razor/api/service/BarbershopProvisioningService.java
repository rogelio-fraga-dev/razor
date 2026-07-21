package com.razor.api.service;

import com.razor.api.dto.OnboardingRequest;
import com.razor.api.model.Barbershop;
import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.repository.TenantUserRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
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

    @Transactional
    public Barbershop provision(OnboardingRequest request) {
        String slug = request.slug();
        String instance = normalizeBlankToNull(request.whatsappInstance());
        String ownerEmail = AuthService.normalizeEmail(request.ownerEmail());

        if (barbershopRepository.findBySlug(slug).isPresent()) {
            throw new OnboardingConflictException("Ja existe uma barbearia com o slug '" + slug + "'.");
        }
        if (instance != null && barbershopRepository.findByWhatsappInstance(instance).isPresent()) {
            throw new OnboardingConflictException("A instancia de WhatsApp '" + instance + "' ja esta em uso.");
        }
        if (tenantUserRepository.existsByEmail(ownerEmail)) {
            throw new OnboardingConflictException("O email '" + ownerEmail + "' ja esta em uso.");
        }

        Barbershop shop = barbershopRepository.save(Barbershop.builder()
                .name(request.name())
                .slug(slug)
                .whatsappInstance(instance)
                .active(true)
                .build());

        // O TenantContext do onboarding nao vem de um webhook; escopamos manualmente para que os
        // servicos por-tenant gravem tudo na barbearia recem-criada, e limpamos ao final.
        try {
            TenantContext.set(shop.getId());
            seedCatalogoPadrao();
            professionalService.create(defaultProfessionalName(request.professionalName()), null);
            for (String phone : request.adminPhones()) {
                tenantAdminService.add(phone, null);
            }
        } finally {
            TenantContext.clear();
        }

        // Usuario dono (login do painel web) — fora do TenantContext: o vinculo e o barbershopId.
        tenantUserRepository.save(TenantUser.builder()
                .barbershopId(shop.getId())
                .email(ownerEmail)
                .passwordHash(passwordEncoder.encode(request.ownerPassword()))
                .name(request.name())
                .role(UserRole.OWNER)
                .active(true)
                .build());

        log.info("[ONBOARDING] Barbearia '{}' (slug '{}') provisionada: id {} (owner {})",
                shop.getName(), slug, shop.getId(), ownerEmail);
        return shop;
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
