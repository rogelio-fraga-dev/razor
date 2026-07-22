package com.razor.api.service;

import com.razor.api.model.Customer;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class CustomerService {

    private final CustomerRepository customerRepository;

    // Memória viva: Guarda a pausa de cada cliente, chaveada por tenant+telefone
    // (o mesmo telefone pode existir em barbearias diferentes).
    private final Map<String, LocalDateTime> pausedCustomers = new ConcurrentHashMap<>();

    private String pauseKey(String phoneNumber) {
        return TenantContext.getCurrent() + "|" + phoneNumber;
    }

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

    public void pauseCustomer(String phoneNumber, int minutes) {
        pausedCustomers.put(pauseKey(phoneNumber), LocalDateTime.now().plusMinutes(minutes));
        log.info("Pausando cliente {} por {} minutos", phoneNumber, minutes);
    }

    public void resumeCustomer(String phoneNumber) {
        pausedCustomers.remove(pauseKey(phoneNumber));
        log.info("Retomando cliente {}", phoneNumber);
    }

    public boolean isCustomerPaused(String phoneNumber) {
        String key = pauseKey(phoneNumber);
        LocalDateTime unpauseTime = pausedCustomers.get(key);
        if (unpauseTime != null) {
            if (LocalDateTime.now().isAfter(unpauseTime)) {
                pausedCustomers.remove(key); // O tempo expirou, tira da pausa
                return false;
            }
            return true; // Ainda está pausado
        }
        return false;
    }

    // Retorna os telefones pausados DO TENANT ATUAL para o administrador escolher
    public List<String> getPausedPhones() {
        // Primeiro limpa os que já venceram o tempo
        pausedCustomers.entrySet().removeIf(e -> LocalDateTime.now().isAfter(e.getValue()));
        String prefix = TenantContext.getCurrent() + "|";
        return pausedCustomers.keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .map(k -> k.substring(prefix.length()))
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