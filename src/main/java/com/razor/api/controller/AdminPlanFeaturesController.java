package com.razor.api.controller;

import com.razor.api.service.PlanFeatures;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** O que o plano da barbearia libera, para o painel esconder o que não vale (26/11, piloto). */
@RestController
@RequestMapping("/api/admin/plan-features")
@RequiredArgsConstructor
public class AdminPlanFeaturesController {

    private final PlanFeatures planFeatures;

    public record Features(String plan, List<String> disabled) {
    }

    @GetMapping
    public Features get() {
        String plan = planFeatures.currentPlan();
        return new Features(plan, PlanFeatures.disabledFor(plan).stream().map(Enum::name).sorted().toList());
    }
}
