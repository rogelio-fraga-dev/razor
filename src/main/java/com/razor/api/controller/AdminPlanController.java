package com.razor.api.controller;

import com.razor.api.dto.SubscriptionPlanRequest;
import com.razor.api.dto.SubscriptionPlanResponse;
import com.razor.api.model.SubscriptionPlan;
import com.razor.api.service.SubscriptionPlanService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Planos de assinatura (clube) da barbearia no painel web (tenant vem do JWT). */
@RestController
@RequestMapping("/api/admin/plans")
@RequiredArgsConstructor
public class AdminPlanController {

    private final SubscriptionPlanService subscriptionPlanService;

    @GetMapping
    public List<SubscriptionPlanResponse> list() {
        return subscriptionPlanService.listAll().stream().map(SubscriptionPlanResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<SubscriptionPlanResponse> create(@Valid @RequestBody SubscriptionPlanRequest req) {
        SubscriptionPlan created = subscriptionPlanService.create(req.name(), req.price(), req.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(SubscriptionPlanResponse.from(created));
    }

    @PutMapping("/{id}")
    public SubscriptionPlanResponse update(@PathVariable UUID id, @Valid @RequestBody SubscriptionPlanRequest req) {
        boolean active = req.active() == null || req.active();
        SubscriptionPlan updated = subscriptionPlanService.update(id, req.name(), req.price(), req.description(), active);
        return SubscriptionPlanResponse.from(updated);
    }
}
