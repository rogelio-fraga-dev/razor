package com.razor.api.service;

import com.razor.api.model.Professional;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
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

    private static final BigDecimal CEM = new BigDecimal("100");

    /**
     * Define o percentual de comissao (0–100) de um profissional do tenant atual.
     * Rejeita valores fora do intervalo e profissional de outro tenant (isolamento).
     */
    public Professional updateCommission(UUID professionalId, BigDecimal percent) {
        if (percent == null || percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(CEM) > 0) {
            throw new IllegalArgumentException("Comissao deve estar entre 0 e 100.");
        }
        Professional professional = professionalRepository.findById(professionalId)
                .filter(p -> TenantContext.getCurrent().equals(p.getBarbershopId()))
                .orElseThrow(() -> new IllegalArgumentException("Profissional nao encontrado no tenant atual."));
        professional.setCommissionPercent(percent);
        Professional saved = professionalRepository.save(professional);
        log.info("[PROFISSIONAL] Comissao de '{}' definida em {}%", professional.getName(), percent);
        return saved;
    }
}
