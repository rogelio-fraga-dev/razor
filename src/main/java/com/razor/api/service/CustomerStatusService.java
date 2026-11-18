package com.razor.api.service;

import com.razor.api.model.CustomerStatus;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Status (novo/ativo/em risco/sumido) de cada cliente do tenant atual, pela regra de
 * {@link CustomerStatusRules} — a mesma do relatório de retenção.
 *
 * <p>Calculado na hora, numa varredura dos agendamentos do tenant (o mesmo custo do relatório de
 * retenção). Gravar o status numa coluna o deixaria velho no dia seguinte sem nenhum evento para
 * atualizá-lo — cliente vira "sumido" justamente por <i>não</i> fazer nada.
 */
@Service
@RequiredArgsConstructor
public class CustomerStatusService {

    private final CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;

    /** Status de todos os clientes do tenant atual, hoje. */
    public Map<UUID, CustomerStatus> statuses() {
        return statuses(LocalDate.now(AppointmentDates.ZONE));
    }

    public Map<UUID, CustomerStatus> statuses(LocalDate today) {
        UUID shop = TenantContext.getCurrent();
        CustomerStatusRules.Snapshot snapshot = CustomerStatusRules.classify(
                customerRepository.findByBarbershopId(shop),
                appointmentRepository.findByBarbershopId(shop),
                today);
        Map<UUID, CustomerStatus> out = new HashMap<>();
        snapshot.byCustomer().forEach((id, k) -> out.put(id, k.status()));
        return out;
    }
}
