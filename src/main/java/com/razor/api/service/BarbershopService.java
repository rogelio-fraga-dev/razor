package com.razor.api.service;

import com.razor.api.model.Barbershop;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Perfil da barbearia (tenant atual) para o painel web: leitura e edicao de nome e instancia de
 * WhatsApp. Slug e imutavel (identidade de URL). Sempre escopado no tenant atual.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BarbershopService {

    private final BarbershopRepository barbershopRepository;

    public Barbershop current() {
        return barbershopRepository.findById(TenantContext.getCurrent())
                .orElseThrow(() -> new NotFoundException("Barbearia nao encontrada."));
    }

    /** Atualiza nome e instancia de WhatsApp; a instancia deve ser unica entre tenants. */
    public Barbershop updateProfile(String name, String whatsappInstance) {
        Barbershop shop = current();
        String instance = (whatsappInstance == null || whatsappInstance.isBlank()) ? null : whatsappInstance.trim();
        if (instance != null) {
            barbershopRepository.findByWhatsappInstance(instance)
                    .filter(other -> !other.getId().equals(shop.getId()))
                    .ifPresent(other -> {
                        throw new IllegalArgumentException("A instancia de WhatsApp '" + instance + "' ja esta em uso.");
                    });
        }
        shop.setName(name);
        shop.setWhatsappInstance(instance);
        Barbershop saved = barbershopRepository.save(shop);
        log.info("[BARBEARIA] Perfil do tenant {} atualizado", shop.getId());
        return saved;
    }
}
