package com.razor.api.service;

import com.razor.api.model.Customer;
import com.razor.api.model.CustomerStatus;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.tenant.TenantContext;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Recortes da base de clientes por <b>tag</b> e <b>status</b> (V36): alimenta o filtro da tela de
 * Clientes e o publico do disparo "Enviar mensagem para este grupo".
 *
 * <p>Os dois usos passam pelo mesmo {@link #idsMatching}, de proposito: o numero que a tela mostra
 * ("12 encontrados") tem que ser exatamente quem recebe a mensagem.
 */
@Service
@RequiredArgsConstructor
public class CustomerSegmentService {

    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final CustomerTagService customerTagService;
    private final CustomerStatusService customerStatusService;
    private final TenantAdminService tenantAdminService;

    /**
     * Pagina da base com busca, filtro de assinante/novos e, opcionalmente, tag e status. Sem tag
     * nem status cai na busca de sempre ({@link CustomerService#searchCustomers}).
     */
    public Page<Customer> search(String query, CustomerService.CustomerFilter filter, UUID tagId,
                                 CustomerStatus status, Pageable pageable) {
        if (tagId == null && status == null) {
            return customerService.searchCustomers(query, filter, pageable);
        }
        Set<UUID> ids = idsMatching(tagId, status);
        if (ids.isEmpty()) {
            // "in ()" e erro de sintaxe no Postgres; e sem ninguem no recorte nao ha o que buscar.
            return Page.empty(pageable);
        }
        String term = (query == null || query.isBlank()) ? null : "%" + query.trim().toLowerCase() + "%";
        UUID shop = TenantContext.getCurrent();
        boolean onlySubscribers = filter == CustomerService.CustomerFilter.SUBSCRIBERS;
        if (filter == CustomerService.CustomerFilter.NEW) {
            return customerRepository.searchAmongCreatedSince(shop, ids, term, onlySubscribers,
                    LocalDateTime.now().minusDays(CustomerService.NEW_WINDOW_DAYS), pageable);
        }
        return customerRepository.searchAmong(shop, ids, term, onlySubscribers, pageable);
    }

    /**
     * Quem recebe um disparo para o grupo: clientes do tenant no recorte (tag e/ou status; sem
     * nenhum dos dois = a base toda, como o disparo do WhatsApp), menos os administradores.
     */
    public List<Customer> recipients(UUID tagId, CustomerStatus status) {
        List<Customer> base = customerRepository.findByBarbershopId(TenantContext.getCurrent());
        Set<UUID> ids = (tagId == null && status == null) ? null : idsMatching(tagId, status);
        return base.stream()
                .filter(c -> ids == null || ids.contains(c.getId()))
                .filter(c -> c.getPhoneNumber() != null && !c.getPhoneNumber().isBlank())
                .filter(c -> !tenantAdminService.isAdmin(c.getPhoneNumber()))
                .toList();
    }

    /** Interseccao de "tem a tag" com "esta no status". Pelo menos um dos dois nao e nulo. */
    Set<UUID> idsMatching(UUID tagId, CustomerStatus status) {
        Set<UUID> ids = null;
        if (tagId != null) {
            ids = customerTagService.customerIdsWithTag(tagId);
        }
        if (status != null) {
            Set<UUID> noStatus = customerStatusService.statuses().entrySet().stream()
                    .filter(e -> e.getValue() == status)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toCollection(HashSet::new));
            if (ids == null) {
                ids = noStatus;
            } else {
                ids.retainAll(noStatus);
            }
        }
        return ids == null ? Set.of() : ids;
    }
}
