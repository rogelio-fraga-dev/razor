package com.razor.api.service;

import com.razor.api.model.CustomerStatus;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
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
        // Visitas agregadas no banco (telefone, primeira, ultima, quantas): antes carregava todos
        // os agendamentos do tenant para a memoria a cada filtro da tela de Clientes.
        Map<String, CustomerStatusRules.VisitStats> visitas = new HashMap<>();
        List<Object[]> linhas = appointmentRepository.visitStatsByPhone(shop, today);
        for (Object[] l : linhas == null ? List.<Object[]>of() : linhas) {
            visitas.put((String) l[0], new CustomerStatusRules.VisitStats(
                    (LocalDate) l[1], (LocalDate) l[2], ((Number) l[3]).longValue()));
        }
        CustomerStatusRules.Snapshot snapshot = CustomerStatusRules.classifyStats(
                customerRepository.findByBarbershopId(shop), visitas, today);
        Map<UUID, CustomerStatus> out = new HashMap<>();
        snapshot.byCustomer().forEach((id, k) -> out.put(id, k.status()));
        return out;
    }
}
