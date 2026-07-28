package com.razor.api.service;

import com.razor.api.dto.RetentionResponse;
import com.razor.api.model.Appointment;
import com.razor.api.model.Customer;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

    private final CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerService customerService;

    /** Intervalo assumido quando a barbearia ainda não tem histórico para calcular a mediana. */
    static final int FALLBACK_INTERVAL_DAYS = 30;

    /** Cadastrado há menos que isto e sem 2ª visita ainda é "novo", não "sumido". */
    static final int NEW_CUSTOMER_DAYS = 30;

    /** Passou de 1x o intervalo esperado: em risco. Passou de 2x: sumido. */
    static final double AT_RISK_FACTOR = 1.0;
    static final double LOST_FACTOR = 2.0;

    /**
     * Abaixo disto o "ritmo" calculado é ruído de dado, não comportamento — ninguém corta cabelo
     * como rotina semanal. Ver {@code averageInterval}.
     */
    static final int MIN_PLAUSIBLE_INTERVAL_DAYS = 7;

    /** Quantos clientes listar em cada balde — a lista é para agir, não para auditar. */
    private static final int LIST_LIMIT = 25;

    public RetentionResponse retention(LocalDate today) {
        UUID shop = TenantContext.getCurrent();
        List<Customer> customers = customerRepository.findByBarbershopId(shop);
        Set<String> assinantes = customerService.subscriberPhones();

        // Uma varredura só dos agendamentos do tenant, agrupada por telefone. A alternativa
        // (uma consulta por cliente) seria N+1 crescendo com a base — justo o que este
        // relatório existe para medir.
        Map<String, List<LocalDate>> visitasPorTelefone = new HashMap<>();
        for (Appointment a : appointmentRepository.findByBarbershopId(shop)) {
            if (a.getCustomerPhone() == null || a.getAppointmentDate() == null) continue;
            if (a.getAppointmentDate().isAfter(today)) continue; // agendamento futuro não é visita
            visitasPorTelefone.computeIfAbsent(a.getCustomerPhone(), k -> new ArrayList<>())
                    .add(a.getAppointmentDate());
        }

        // Ritmo próprio de cada cliente e a mediana da casa.
        Map<String, Integer> intervaloPorTelefone = new HashMap<>();
        List<Integer> intervalos = new ArrayList<>();
        for (var entry : visitasPorTelefone.entrySet()) {
            Integer intervalo = averageInterval(entry.getValue());
            if (intervalo != null) {
                intervaloPorTelefone.put(entry.getKey(), intervalo);
                intervalos.add(intervalo);
            }
        }
        Integer mediana = median(intervalos);
        int padrao = mediana != null ? mediana : FALLBACK_INTERVAL_DAYS;

        int novo = 0, ativo = 0, emRisco = 0, sumido = 0, voltaram = 0;
        List<RetentionResponse.CustomerAtRisk> risco = new ArrayList<>();
        List<RetentionResponse.CustomerAtRisk> perdidos = new ArrayList<>();

        for (Customer c : customers) {
            List<LocalDate> visitas = visitasPorTelefone.getOrDefault(c.getPhoneNumber(), List.of());
            if (visitas.size() >= 2) voltaram++;

            LocalDate ultima = lastVisit(c, visitas);
            int esperado = intervaloPorTelefone.getOrDefault(c.getPhoneNumber(), padrao);

            // Sem nenhuma visita registrada: só o cadastro. É "novo" enquanto for recente, e
            // "sumido" depois — um cadastro de seis meses que nunca virou atendimento é um
            // cliente perdido, não um cliente novo.
            if (ultima == null) {
                if (isRecentlyCreated(c, today)) {
                    novo++;
                } else {
                    sumido++;
                    perdidos.add(toItem(c, null, 0, esperado, 0, assinantes));
                }
                continue;
            }

            int diasParado = (int) ChronoUnit.DAYS.between(ultima, today);

            if (visitas.size() <= 1 && isRecentlyCreated(c, today)) {
                novo++;
            } else if (diasParado > esperado * LOST_FACTOR) {
                sumido++;
                perdidos.add(toItem(c, ultima, diasParado, esperado, visitas.size(), assinantes));
            } else if (diasParado > esperado * AT_RISK_FACTOR) {
                emRisco++;
                risco.add(toItem(c, ultima, diasParado, esperado, visitas.size(), assinantes));
            } else {
                ativo++;
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
                mediana,
                padrao,
                taxaRetorno,
                risco.stream().limit(LIST_LIMIT).toList(),
                perdidos.stream().limit(LIST_LIMIT).toList());
    }

    /**
     * Última visita: a mais recente entre os agendamentos e o {@code lastServiceDate} do cadastro.
     *
     * <p>Os dois são consultados porque o campo do cliente é atualizado também por atendimento
     * lançado em comanda sem agendamento (V28) — usar só os agendamentos marcaria como sumido
     * quem foi atendido ontem no balcão.
     */
    private LocalDate lastVisit(Customer c, List<LocalDate> visitas) {
        LocalDate porAgendamento = visitas.stream().max(Comparator.naturalOrder()).orElse(null);
        LocalDate porCadastro = c.getLastServiceDate() == null ? null : c.getLastServiceDate().toLocalDate();
        if (porAgendamento == null) return porCadastro;
        if (porCadastro == null) return porAgendamento;
        return porAgendamento.isAfter(porCadastro) ? porAgendamento : porCadastro;
    }

    private boolean isRecentlyCreated(Customer c, LocalDate today) {
        if (c.getCreatedAt() == null) return false;
        return ChronoUnit.DAYS.between(c.getCreatedAt().toLocalDate(), today) <= NEW_CUSTOMER_DAYS;
    }

    /**
     * Média de dias entre visitas consecutivas, ou {@code null} quando não há ritmo utilizável.
     *
     * <p>Devolve nulo em dois casos: menos de duas visitas, e intervalo abaixo de
     * {@link #MIN_PLAUSIBLE_INTERVAL_DAYS}. O segundo importa mais do que parece — duas visitas
     * no mesmo dia (corte de manhã, barba à tarde; ou um lançamento duplicado) dão intervalo
     * zero, e qualquer piso pequeno faria o cliente ser dado como sumido poucos dias depois de
     * ter estado na cadeira. Ninguém corta cabelo como rotina semanal, então um "ritmo" abaixo
     * disso é ruído de dado, não comportamento. Nesses casos vale a mediana da casa.
     */
    private Integer averageInterval(List<LocalDate> visitas) {
        if (visitas.size() < 2) return null;
        List<LocalDate> ordenadas = visitas.stream().sorted().toList();
        long soma = 0;
        for (int i = 1; i < ordenadas.size(); i++) {
            soma += ChronoUnit.DAYS.between(ordenadas.get(i - 1), ordenadas.get(i));
        }
        int media = (int) Math.round((double) soma / (ordenadas.size() - 1));
        return media < MIN_PLAUSIBLE_INTERVAL_DAYS ? null : media;
    }

    /** Mediana, e não média: um cliente que sumiu por dois anos distorceria a média da casa. */
    private Integer median(List<Integer> valores) {
        if (valores.isEmpty()) return null;
        List<Integer> ordenados = valores.stream().sorted().toList();
        int meio = ordenados.size() / 2;
        return ordenados.size() % 2 == 1
                ? ordenados.get(meio)
                : (ordenados.get(meio - 1) + ordenados.get(meio)) / 2;
    }

    private RetentionResponse.CustomerAtRisk toItem(Customer c, LocalDate ultima, int dias,
                                                    int esperado, int visitas, Set<String> assinantes) {
        return new RetentionResponse.CustomerAtRisk(
                c.getId(), c.getName(), c.getPhoneNumber(), ultima, dias, esperado, visitas,
                c.getPhoneNumber() != null && assinantes.contains(c.getPhoneNumber()));
    }
}
