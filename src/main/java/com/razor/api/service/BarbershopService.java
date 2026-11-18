package com.razor.api.service;

import com.razor.api.model.Barbershop;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Perfil da barbearia (tenant atual) para o painel web: leitura e edicao do nome. Slug e imutavel (identidade de URL). Sempre escopado no tenant atual.
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

    /**
     * Atualiza o nome. A instancia de WhatsApp nao passa por aqui de proposito: ela decide para
     * qual tenant o webhook entrega as mensagens, entao so o onboarding (chave de plataforma) ou a
     * derivacao pelo slug a definem.
     */
    public Barbershop updateProfile(String name) {
        Barbershop shop = current();
        shop.setName(name);
        Barbershop saved = barbershopRepository.save(shop);
        log.info("[BARBEARIA] Perfil do tenant {} atualizado", shop.getId());
        return saved;
    }
}
