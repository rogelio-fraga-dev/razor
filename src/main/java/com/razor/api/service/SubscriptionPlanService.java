package com.razor.api.service;

import com.razor.api.model.SubscriptionPlan;
import com.razor.api.repository.SubscriptionPlanRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Catalogo de planos de assinatura (clube) da barbearia (Fase 2) — sempre escopado no tenant atual.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public List<SubscriptionPlan> listAll() {
        return subscriptionPlanRepository.findByBarbershopId(TenantContext.getCurrent());
    }

    public List<SubscriptionPlan> listActive() {
        return subscriptionPlanRepository.findByBarbershopIdAndActiveTrue(TenantContext.getCurrent());
    }

    public SubscriptionPlan create(String name, BigDecimal price, String description) {
        SubscriptionPlan created = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .barbershopId(TenantContext.getCurrent())
                .name(name)
                .price(price)
                .description(description)
                .active(true)
                .build());
        log.info("[PLANO] '{}' criado para o tenant {}", name, TenantContext.getCurrent());
        return created;
    }

    public SubscriptionPlan update(UUID id, String name, BigDecimal price, String description, boolean active) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .filter(p -> TenantContext.getCurrent().equals(p.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Plano nao encontrado."));
        plan.setName(name);
        plan.setPrice(price);
        plan.setDescription(description);
        plan.setActive(active);
        return subscriptionPlanRepository.save(plan);
    }
}
