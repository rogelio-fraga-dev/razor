package com.razor.api.controller;

import com.razor.api.dto.CustomerPlanRequest;
import com.razor.api.dto.CustomerResponse;
import com.razor.api.dto.PageResponse;
import com.razor.api.model.Customer;
import com.razor.api.service.CustomerService;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

/** Base de clientes da barbearia no painel web (tenant vem do JWT). */
@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final CustomerService customerService;

    /**
     * Pagina da base de clientes. Busca ({@code q}) e filtro sao resolvidos no banco — antes a
     * tela baixava a base inteira e filtrava no navegador, o que deixa de funcionar assim que a
     * lista e paginada.
     *
     * <p>A ordenacao por nome tambem desceu para o banco: ordenar uma pagina ja recortada daria
     * uma lista "ordenada" por pedaco, com a pagina 2 comecando antes do fim da 1.
     */
    @GetMapping
    public PageResponse<CustomerResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CustomerService.CustomerFilter filter) {

        Map<UUID, String> planNames = customerService.planNamesByTenant();
        Pageable pageable = PageResponse.pageable(page, size,
                Sort.by(Sort.Order.asc("name").ignoreCase().nullsLast()));

        return PageResponse.of(
                customerService.searchCustomers(q, filter != null ? filter : CustomerService.CustomerFilter.ALL, pageable),
                c -> CustomerResponse.from(c, planNameOf(planNames, c.getSubscriptionPlanId())));
    }

    /**
     * Totais da base inteira (não da página). Endpoint separado de propósito: são agregados que
     * não mudam ao virar a página, e embutir no envelope de paginação misturaria o genérico com
     * o específico desta tela.
     */
    @GetMapping("/summary")
    public CustomerService.CustomerSummary summary() {
        return customerService.customerSummary();
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
