package com.razor.api.controller;

import com.razor.api.dto.CustomerPlanRequest;
import com.razor.api.dto.CustomerResponse;
import com.razor.api.model.Customer;
import com.razor.api.service.CustomerService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Base de clientes da barbearia no painel web (tenant vem do JWT). */
@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final CustomerService customerService;

    @GetMapping
    public List<CustomerResponse> list() {
        Map<UUID, String> planNames = customerService.planNamesByTenant();
        return customerService.listCustomers().stream()
                .map(c -> CustomerResponse.from(c, planNameOf(planNames, c.getSubscriptionPlanId())))
                .sorted(Comparator.comparing(CustomerResponse::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    /** Liga (ou desliga, com planId nulo) o cliente a um plano de assinatura. */
    @PutMapping("/{id}/plan")
    public CustomerResponse assignPlan(@PathVariable UUID id, @RequestBody CustomerPlanRequest req) {
        Customer updated = customerService.assignPlan(id, req.planId());
        String planName = planNameOf(customerService.planNamesByTenant(), updated.getSubscriptionPlanId());
        return CustomerResponse.from(updated, planName);
    }

    /** Resolve o nome do plano com seguranca — cliente avulso (planId nulo) devolve nulo sem tocar no mapa. */
    private static String planNameOf(Map<UUID, String> planNames, UUID planId) {
        return planId == null ? null : planNames.get(planId);
    }
}
