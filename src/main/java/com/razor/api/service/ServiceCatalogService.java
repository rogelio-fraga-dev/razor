package com.razor.api.service;

import com.razor.api.model.ServiceOffering;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Catalogo de servicos da barbearia (Fase 1) — sempre escopado no tenant atual.
 * Base para o menu de precos, a duracao de agendamentos e o financeiro por servico.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceCatalogService {

    private final ServiceOfferingRepository serviceOfferingRepository;

    /** Servicos ativos do tenant atual. */
    public List<ServiceOffering> listActive() {
        return serviceOfferingRepository.findByBarbershopIdAndActiveTrue(TenantContext.getCurrent());
    }

    /** Todos os servicos (ativos e inativos) do tenant atual. */
    public List<ServiceOffering> listAll() {
        return serviceOfferingRepository.findByBarbershopId(TenantContext.getCurrent());
    }

    public ServiceOffering create(String name, BigDecimal price, int durationMinutes) {
        return create(name, price, durationMinutes, null);
    }

    public ServiceOffering create(String name, BigDecimal price, int durationMinutes, BigDecimal commissionPercent) {
        ServiceOffering created = serviceOfferingRepository.save(ServiceOffering.builder()
                .barbershopId(TenantContext.getCurrent())
                .name(name)
                .price(price)
                .durationMinutes(durationMinutes)
                .active(true)
                .commissionPercent(commissionPercent)
                .build());
        log.info("[CATALOGO] Servico '{}' criado para o tenant {}", name, TenantContext.getCurrent());
        return created;
    }

    /** Atualiza um servico do tenant atual (isolamento: rejeita servico de outro tenant). */
    public ServiceOffering update(UUID id, String name, BigDecimal price, int durationMinutes, boolean active) {
        return update(id, name, price, durationMinutes, active, null);
    }

    /** {@code commissionPercent} null = sem comissao propria (vale o % do barbeiro). */
    public ServiceOffering update(UUID id, String name, BigDecimal price, int durationMinutes, boolean active,
                                  BigDecimal commissionPercent) {
        ServiceOffering svc = serviceOfferingRepository.findById(id)
                .filter(s -> TenantContext.getCurrent().equals(s.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Servico nao encontrado."));
        svc.setName(name);
        svc.setPrice(price);
        svc.setDurationMinutes(durationMinutes);
        svc.setActive(active);
        svc.setCommissionPercent(commissionPercent);
        return serviceOfferingRepository.save(svc);
    }
}
