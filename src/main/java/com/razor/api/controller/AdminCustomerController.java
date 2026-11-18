package com.razor.api.controller;

import com.razor.api.dto.CustomerPlanRequest;
import com.razor.api.dto.CustomerResponse;
import com.razor.api.dto.CustomerTagRequests;
import com.razor.api.dto.CustomerTagResponse;
import com.razor.api.dto.PageResponse;
import com.razor.api.model.Customer;
import com.razor.api.model.CustomerStatus;
import com.razor.api.model.CustomerTag;
import com.razor.api.service.CustomerSegmentService;
import com.razor.api.service.CustomerService;
import com.razor.api.service.CustomerStatusService;
import com.razor.api.service.CustomerTagService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Base de clientes da barbearia no painel web (tenant vem do JWT). */
@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final CustomerService customerService;
    private final CustomerSegmentService customerSegmentService;
    private final CustomerTagService customerTagService;
    private final CustomerStatusService customerStatusService;

    /**
     * Pagina da base de clientes. Busca ({@code q}) e filtro sao resolvidos no banco — antes a
     * tela baixava a base inteira e filtrava no navegador, o que deixa de funcionar assim que a
     * lista e paginada.
     *
     * <p>A ordenacao por nome tambem desceu para o banco: ordenar uma pagina ja recortada daria
     * uma lista "ordenada" por pedaco, com a pagina 2 comecando antes do fim da 1.
     *
     * <p>{@code tagId} e {@code status} (V36) recortam por grupo e situacao; cada linha volta com
     * suas tags e o status calculado.
     */
    @GetMapping
    public PageResponse<CustomerResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CustomerService.CustomerFilter filter,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) CustomerStatus status) {

        Map<UUID, String> planNames = customerService.planNamesByTenant();
        Pageable pageable = PageResponse.pageable(page, size,
                Sort.by(Sort.Order.asc("name").ignoreCase().nullsLast()));

        Page<Customer> pagina = customerSegmentService.search(
                q, filter != null ? filter : CustomerService.CustomerFilter.ALL, tagId, status, pageable);
        Map<UUID, List<CustomerTag>> tags = customerTagService.tagsByCustomer(
                pagina.getContent().stream().map(Customer::getId).toList());
        Map<UUID, CustomerStatus> statuses = pagina.isEmpty() ? Map.of() : customerStatusService.statuses();

        return PageResponse.of(pagina, c -> CustomerResponse.from(c,
                planNameOf(planNames, c.getSubscriptionPlanId()),
                tags.getOrDefault(c.getId(), List.of()).stream().map(CustomerTagResponse::from).toList(),
                statuses.get(c.getId())));
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

    // ---------- Tags (V36) ----------

    @GetMapping("/tags")
    public List<CustomerTagResponse> listTags() {
        return customerTagService.list().stream().map(CustomerTagResponse::from).toList();
    }

    @PostMapping("/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerTagResponse createTag(@Valid @RequestBody CustomerTagRequests.TagRequest req) {
        return CustomerTagResponse.from(customerTagService.create(req.name(), req.color()));
    }

    @PutMapping("/tags/{tagId}")
    public CustomerTagResponse updateTag(@PathVariable UUID tagId,
                                         @Valid @RequestBody CustomerTagRequests.TagRequest req) {
        return CustomerTagResponse.from(customerTagService.update(tagId, req.name(), req.color()));
    }

    @DeleteMapping("/tags/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTag(@PathVariable UUID tagId) {
        customerTagService.delete(tagId);
    }

    /** Troca o conjunto de tags do cliente (lista vazia tira todas). */
    @PutMapping("/{id}/tags")
    public List<CustomerTagResponse> setTags(@PathVariable UUID id,
                                             @RequestBody CustomerTagRequests.CustomerTagsRequest req) {
        return customerTagService.setCustomerTags(id, req.tagIds()).stream()
                .map(CustomerTagResponse::from).toList();
    }

    /** Resolve o nome do plano com seguranca — cliente avulso (planId nulo) devolve nulo sem tocar no mapa. */
    private static String planNameOf(Map<UUID, String> planNames, UUID planId) {
        return planId == null ? null : planNames.get(planId);
    }
}
