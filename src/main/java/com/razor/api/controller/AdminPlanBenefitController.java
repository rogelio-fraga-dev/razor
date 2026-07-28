package com.razor.api.controller;

import com.razor.api.dto.PlanBenefitRequest;
import com.razor.api.dto.PlanBenefitResponse;
import com.razor.api.dto.SubscriptionPlanResponse;
import com.razor.api.service.PlanBenefitService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Beneficios dos planos no painel web (tenant vem do JWT) — o que o assinante ganha em cada
 * servico/produto (V27).
 */
@RestController
@RequestMapping("/api/admin/plans")
@RequiredArgsConstructor
public class AdminPlanBenefitController {

    private final PlanBenefitService planBenefitService;

    @GetMapping("/{planId}/benefits")
    public List<PlanBenefitResponse> list(@PathVariable UUID planId) {
        return planBenefitService.listByPlan(planId).stream().map(PlanBenefitResponse::from).toList();
    }

    /** Cria ou atualiza o beneficio daquele alvo — um alvo tem no maximo um beneficio no plano. */
    @PutMapping("/{planId}/benefits")
    public PlanBenefitResponse upsert(@PathVariable UUID planId,
                                      @Valid @RequestBody PlanBenefitRequest req) {
        return PlanBenefitResponse.from(planBenefitService.upsert(
                planId, req.targetType(), req.targetId(), req.discountPercent(),
                req.monthlyQuota(), req.weekdays()));
    }

    @DeleteMapping("/{planId}/benefits/{benefitId}")
    public void delete(@PathVariable UUID planId, @PathVariable UUID benefitId) {
        planBenefitService.delete(planId, benefitId);
    }

    /**
     * Cria os tres planos sugeridos (Bronze/Prata/Ouro) com seus beneficios. Idempotente: rodar
     * de novo atualiza em vez de duplicar.
     */
    @PostMapping("/suggested")
    public List<SubscriptionPlanResponse> createSuggested() {
        return planBenefitService.createSuggestedPlans().stream()
                .map(SubscriptionPlanResponse::from).toList();
    }
}
