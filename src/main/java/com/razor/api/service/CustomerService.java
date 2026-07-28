package com.razor.api.service;

import com.razor.api.model.Customer;
import com.razor.api.model.SubscriptionPlan;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.SubscriptionPlanRepository;
import com.razor.api.state.StateKeys;
import com.razor.api.state.StateStore;
import com.razor.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    /** Pausa do bot por cliente — no StateStore (chave ja escopada por tenant). */
    private final StateStore stateStore;

    public Customer findOrCreateCustomer(String phoneNumber, String name) {
        UUID shopId = TenantContext.getCurrent();
        return customerRepository.findByBarbershopIdAndPhoneNumber(shopId, phoneNumber)
                .orElseGet(() -> customerRepository.save(Customer.builder()
                        .barbershopId(shopId)
                        .phoneNumber(phoneNumber)
                        .name(name != null ? name : "Cliente")
                        .build()));
    }

    public Customer findByName(String name) {
        return customerRepository.findByBarbershopIdAndNameIgnoreCase(TenantContext.getCurrent(), name.trim())
                .orElse(null);
    }

    /** Todos os clientes do tenant atual (base para o painel web). */
    public List<Customer> listCustomers() {
        return customerRepository.findByBarbershopId(TenantContext.getCurrent());
    }

    /** Filtro da tela de Clientes. */
    public enum CustomerFilter {
        /** Todos. */
        ALL,
        /** So quem tem plano de assinatura. */
        SUBSCRIBERS,
        /** Cadastrados nos ultimos {@link #NEW_WINDOW_DAYS} dias. */
        NEW
    }

    /** Janela do filtro "novos" — o mesmo numero que a tela exibe no rotulo. */
    public static final int NEW_WINDOW_DAYS = 30;

    /**
     * Pagina da base de clientes, com busca e filtro resolvidos no banco.
     *
     * <p>O termo casa por nome (sem diferenciar maiusculas) ou por telefone. Busca vazia devolve
     * a base inteira, paginada.
     */
    public Page<Customer> searchCustomers(String query, CustomerFilter filter, Pageable pageable) {
        String term = (query == null || query.isBlank())
                ? null
                : "%" + query.trim().toLowerCase() + "%";
        UUID shop = TenantContext.getCurrent();
        boolean onlySubscribers = filter == CustomerFilter.SUBSCRIBERS;

        // Duas consultas em vez de uma com data opcional — o porque esta no CustomerRepository.
        if (filter == CustomerFilter.NEW) {
            return customerRepository.searchCreatedSince(shop, term, onlySubscribers,
                    LocalDateTime.now().minusDays(NEW_WINDOW_DAYS), pageable);
        }
        return customerRepository.search(shop, term, onlySubscribers, pageable);
    }

    /** Totais do cabecalho da tela, contados no banco — nao dava para deduzir de uma pagina so. */
    public CustomerSummary customerSummary() {
        UUID shop = TenantContext.getCurrent();
        return new CustomerSummary(
                customerRepository.countByBarbershopId(shop),
                customerRepository.countByBarbershopIdAndSubscriptionPlanIdIsNotNull(shop),
                customerRepository.countByBarbershopIdAndCreatedAtGreaterThanEqual(
                        shop, LocalDateTime.now().minusDays(NEW_WINDOW_DAYS)));
    }

    /** Contadores da base inteira (nao da pagina): total, assinantes e cadastrados no mes. */
    public record CustomerSummary(long total, long subscribers, long newInWindow) {
    }

    /**
     * Liga (ou desliga) um cliente a um plano de assinatura — assim identificamos assinante x avulso.
     * {@code planId} nulo remove o vinculo. Cliente e plano devem pertencer ao tenant atual, senao
     * {@link NotFoundException} (nao vaza dados de outra barbearia).
     */
    public Customer assignPlan(UUID customerId, UUID planId) {
        UUID shop = TenantContext.getCurrent();
        Customer customer = customerRepository.findById(customerId)
                .filter(c -> shop.equals(c.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Cliente nao encontrado."));

        if (planId != null) {
            subscriptionPlanRepository.findById(planId)
                    .filter(p -> shop.equals(p.getBarbershopId()))
                    .orElseThrow(() -> new NotFoundException("Plano nao encontrado."));
        }

        customer.setSubscriptionPlanId(planId);
        Customer saved = customerRepository.save(customer);
        log.info("[ASSINANTE] Cliente {} -> plano {} (tenant {})", customerId, planId, shop);
        return saved;
    }

    /** Mapa planId -> nome dos planos do tenant atual, para o painel exibir o nome do plano do cliente. */
    public Map<UUID, String> planNamesByTenant() {
        return subscriptionPlanRepository.findByBarbershopId(TenantContext.getCurrent()).stream()
                .collect(Collectors.toMap(SubscriptionPlan::getId, SubscriptionPlan::getName, (a, b) -> a));
    }

    /** Telefones dos clientes assinantes (com plano) do tenant atual — usado para marcar a agenda. */
    public java.util.Set<String> subscriberPhones() {
        return customerRepository.findByBarbershopId(TenantContext.getCurrent()).stream()
                .filter(c -> c.getSubscriptionPlanId() != null)
                .map(Customer::getPhoneNumber)
                .collect(Collectors.toSet());
    }

    public void pauseCustomer(String phoneNumber, int minutes) {
        // O proprio TTL expira a pausa — nao ha mais varredura manual de vencidos.
        stateStore.put(StateKeys.pause(phoneNumber), Boolean.TRUE, Duration.ofMinutes(minutes));
        log.info("Pausando cliente {} por {} minutos", phoneNumber, minutes);
    }

    public void resumeCustomer(String phoneNumber) {
        stateStore.remove(StateKeys.pause(phoneNumber));
        log.info("Retomando cliente {}", phoneNumber);
    }

    public boolean isCustomerPaused(String phoneNumber) {
        return stateStore.get(StateKeys.pause(phoneNumber), Boolean.class).isPresent();
    }

    /** Telefones pausados DO TENANT ATUAL (as chaves ja carregam o tenant). */
    public List<String> getPausedPhones() {
        return stateStore.keysWithPrefix(StateKeys.pausePrefix()).stream()
                .map(StateKeys::phoneFromPauseKey)
                .collect(Collectors.toList());
    }

    @org.springframework.transaction.annotation.Transactional
    public int importCustomersFromCsvBase64(String base64Content) {
        try {
            String pureBase64 = base64Content.contains(",") ? base64Content.split(",")[1] : base64Content;
            byte[] decodedBytes = Base64.getDecoder().decode(pureBase64);
            String csvText = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
            
            String[] lines = csvText.split("\\r?\\n");
            int count = 0;
            
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                if (line.trim().isEmpty()) continue;
                
                String[] columns = line.split("\",\"");
                if (columns.length >= 5) {
                    String rawName = columns[1].replace("\"", "").trim();
                    String rawPhone = columns[4].replace("\"", "").trim();
                    
                    if (!rawPhone.isEmpty() && !rawPhone.equalsIgnoreCase("N/A")) {
                        String cleanPhone = rawPhone.replaceAll("[^0-9]", "");
                        if (cleanPhone.length() == 10 || cleanPhone.length() == 11) cleanPhone = "55" + cleanPhone;
                        
                        UUID shopId = TenantContext.getCurrent();
                        Customer customer = customerRepository.findByBarbershopIdAndPhoneNumber(shopId, cleanPhone)
                                .orElseGet(Customer::new);
                        customer.setBarbershopId(shopId);
                        customer.setPhoneNumber(cleanPhone);
                        customer.setName(rawName);
                        customerRepository.save(customer);
                        count++;
                    }
                }
            }
            return count;
        } catch (Exception e) {
            log.error("Erro importando CSV: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }
}