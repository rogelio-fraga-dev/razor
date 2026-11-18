package com.razor.api.service;

import com.razor.api.dto.RetentionResponse;
import com.razor.api.model.Customer;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Retenção da base: quem volta, quem está prestes a sumir e quem já sumiu.
 *
 * <p>É a outra metade da pergunta que o financeiro responde. Faturamento diz quanto entrou;
 * retenção diz se vai continuar entrando — barbearia vive de recorrência, e cliente que some não
 * aparece em relatório nenhum justamente por ter parado de gerar linha.
 *
 * <p>A regra de classificação mora em {@link CustomerStatusRules} desde a V36, compartilhada com
 * o status mostrado na tela de Clientes: os dois respondem a mesma pergunta e não podem divergir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

    private final CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerService customerService;

    /** Mantido para quem já referenciava aqui; a fonte é {@link CustomerStatusRules}. */
    static final int FALLBACK_INTERVAL_DAYS = CustomerStatusRules.FALLBACK_INTERVAL_DAYS;

    /** Quantos clientes listar em cada balde — a lista é para agir, não para auditar. */
    private static final int LIST_LIMIT = 25;

    public RetentionResponse retention(LocalDate today) {
        UUID shop = TenantContext.getCurrent();
        List<Customer> customers = customerRepository.findByBarbershopId(shop);
        Set<String> assinantes = customerService.subscriberPhones();

        CustomerStatusRules.Snapshot snapshot =
                CustomerStatusRules.classify(customers, appointmentRepository.findByBarbershopId(shop), today);

        int novo = 0, ativo = 0, emRisco = 0, sumido = 0, voltaram = 0;
        List<RetentionResponse.CustomerAtRisk> risco = new ArrayList<>();
        List<RetentionResponse.CustomerAtRisk> perdidos = new ArrayList<>();

        for (Customer c : customers) {
            CustomerStatusRules.Classification k = snapshot.byCustomer().get(c.getId());
            if (k.visits() >= 2) voltaram++;
            switch (k.status()) {
                case NOVO -> novo++;
                case ATIVO -> ativo++;
                case EM_RISCO -> {
                    emRisco++;
                    risco.add(toItem(c, k, assinantes));
                }
                case SUMIDO -> {
                    sumido++;
                    perdidos.add(toItem(c, k, assinantes));
                }
            }
        }

        // Mais atrasado primeiro: é quem corre mais risco de não voltar nunca.
        Comparator<RetentionResponse.CustomerAtRisk> maisAtrasado =
                Comparator.comparingInt(RetentionResponse.CustomerAtRisk::daysSinceLastVisit).reversed();
        risco.sort(maisAtrasado);
        perdidos.sort(maisAtrasado);

        BigDecimal taxaRetorno = customers.isEmpty()
                ? null
                : BigDecimal.valueOf(voltaram)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(customers.size()), 1, RoundingMode.HALF_UP);

        return new RetentionResponse(
                today,
                customers.size(),
                new RetentionResponse.Counts(novo, ativo, emRisco, sumido),
                snapshot.medianIntervalDays(),
                snapshot.defaultIntervalDays(),
                taxaRetorno,
                risco.stream().limit(LIST_LIMIT).toList(),
                perdidos.stream().limit(LIST_LIMIT).toList());
    }

    private RetentionResponse.CustomerAtRisk toItem(Customer c, CustomerStatusRules.Classification k,
                                                    Set<String> assinantes) {
        return new RetentionResponse.CustomerAtRisk(
                c.getId(), c.getName(), c.getPhoneNumber(), k.lastVisit(), k.daysSinceLastVisit(),
                k.expectedIntervalDays(), k.visits(),
                c.getPhoneNumber() != null && assinantes.contains(c.getPhoneNumber()));
    }
}
