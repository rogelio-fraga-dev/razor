package com.razor.api.service;

import com.razor.api.model.ServiceOffering;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.List;
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
        ServiceOffering created = serviceOfferingRepository.save(ServiceOffering.builder()
                .barbershopId(TenantContext.getCurrent())
                .name(name)
                .price(price)
                .durationMinutes(durationMinutes)
                .active(true)
                .build());
        log.info("[CATALOGO] Servico '{}' criado para o tenant {}", name, TenantContext.getCurrent());
        return created;
    }
}
