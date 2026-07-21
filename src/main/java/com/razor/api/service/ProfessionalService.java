package com.razor.api.service;

import com.razor.api.model.Professional;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.tenant.TenantContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Gestao dos profissionais (barbeiros) da barbearia — sempre escopado no tenant atual.
 * Base da agenda multiprofissional (Fase 1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfessionalService {

    private final ProfessionalRepository professionalRepository;

    /** Profissionais ativos do tenant atual. */
    public List<Professional> listActive() {
        return professionalRepository.findByBarbershopIdAndActiveTrue(TenantContext.getCurrent());
    }

    /** Todos os profissionais (ativos e inativos) do tenant atual. */
    public List<Professional> listAll() {
        return professionalRepository.findByBarbershopId(TenantContext.getCurrent());
    }

    public Professional create(String name, String phone) {
        Professional created = professionalRepository.save(Professional.builder()
                .barbershopId(TenantContext.getCurrent())
                .name(name)
                .phone(phone)
                .active(true)
                .build());
        log.info("[PROFISSIONAL] '{}' criado para o tenant {}", name, TenantContext.getCurrent());
        return created;
    }
}
