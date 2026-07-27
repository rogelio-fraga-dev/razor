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
        validateCommission(percent);
        Professional professional = findOwned(professionalId);
        professional.setCommissionPercent(percent);
        Professional saved = professionalRepository.save(professional);
        log.info("[PROFISSIONAL] Comissao de '{}' definida em {}%", professional.getName(), percent);
        return saved;
    }

    /**
     * Atualiza nome, telefone, status e comissoes de um profissional do tenant atual.
     *
     * <p>{@code productCommissionPercent} nulo significa "usa o mesmo percentual dos servicos" —
     * e o que vale para quem nunca configurou um percentual proprio de produto (V23).
     */
    public Professional update(UUID id, String name, String phone, boolean active,
                               BigDecimal commissionPercent, BigDecimal productCommissionPercent) {
        validateCommission(commissionPercent);
        if (productCommissionPercent != null) {
            validateCommission(productCommissionPercent);
        }
        Professional professional = findOwned(id);
        professional.setName(name);
        professional.setPhone(phone);
        professional.setActive(active);
        professional.setCommissionPercent(commissionPercent);
        professional.setProductCommissionPercent(productCommissionPercent);
        return professionalRepository.save(professional);
    }

    private void validateCommission(BigDecimal percent) {
        if (percent == null || percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(CEM) > 0) {
            throw new IllegalArgumentException("Comissao deve estar entre 0 e 100.");
        }
    }

    private Professional findOwned(UUID id) {
        return professionalRepository.findById(id)
                .filter(p -> TenantContext.getCurrent().equals(p.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Profissional nao encontrado."));
    }
}
