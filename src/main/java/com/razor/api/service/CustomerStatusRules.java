package com.razor.api.service;

import com.razor.api.model.Appointment;
import com.razor.api.model.Customer;
import com.razor.api.model.CustomerStatus;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A regra UNICA que diz se um cliente e novo, ativo, em risco ou sumido.
 *
 * <p>Nasceu dentro do {@link RetentionService} (o relatorio "quem some") e foi extraida para ca
 * quando a tela de Clientes passou a mostrar o status em cada linha e a filtrar por ele (V36). Duas
 * copias da regra seriam dois numeros diferentes para a mesma pergunta — o relatorio dizendo "12
 * sumidos" e o filtro achando 9.
 *
 * <p>Funcao pura (sem Spring, sem banco): recebe clientes e agendamentos ja carregados. Quem
 * carrega e o {@link RetentionService} e o {@link CustomerStatusService}.
 */
public final class CustomerStatusRules {

    /** Intervalo assumido quando a barbearia ainda não tem histórico para calcular a mediana. */
    public static final int FALLBACK_INTERVAL_DAYS = 30;

    /** Cadastrado há menos que isto e sem 2ª visita ainda é "novo", não "sumido". */
    public static final int NEW_CUSTOMER_DAYS = 30;

    /** Passou de 1x o intervalo esperado: em risco. Passou de 2x: sumido. */
    public static final double AT_RISK_FACTOR = 1.0;
    public static final double LOST_FACTOR = 2.0;

    /**
     * Abaixo disto o "ritmo" calculado é ruído de dado, não comportamento — ninguém corta cabelo
     * como rotina semanal. Ver {@link #averageInterval}.
     */
    public static final int MIN_PLAUSIBLE_INTERVAL_DAYS = 7;

    private CustomerStatusRules() {
    }

    /**
     * Situacao de um cliente e os numeros que a justificam.
     *
     * @param lastVisit            última visita (nula se nunca veio)
     * @param daysSinceLastVisit   dias parado (0 se nunca veio)
     * @param expectedIntervalDays ritmo esperado: o do próprio cliente ou a mediana da casa
     * @param visits               quantas visitas (agendamentos passados)
     */
    public record Classification(CustomerStatus status, LocalDate lastVisit, int daysSinceLastVisit,
                                 int expectedIntervalDays, int visits) {
    }

    /**
     * Resultado para a base inteira.
     *
     * @param byCustomer          classificação por id do cliente, na ordem recebida
     * @param medianIntervalDays  mediana dos ritmos da casa (nula sem histórico)
     * @param defaultIntervalDays ritmo usado para quem não tem o próprio
     */
    public record Snapshot(Map<UUID, Classification> byCustomer, Integer medianIntervalDays,
                           int defaultIntervalDays) {
    }

    public static Snapshot classify(Collection<Customer> customers, Collection<Appointment> appointments,
                                    LocalDate today) {
        // Uma varredura só dos agendamentos, agrupada por telefone. A alternativa (uma consulta
        // por cliente) seria N+1 crescendo com a base.
        Map<String, List<LocalDate>> visitasPorTelefone = new HashMap<>();
        for (Appointment a : appointments) {
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

        Map<UUID, Classification> out = new LinkedHashMap<>();
        for (Customer c : customers) {
            List<LocalDate> visitas = visitasPorTelefone.getOrDefault(c.getPhoneNumber(), List.of());
            int esperado = intervaloPorTelefone.getOrDefault(c.getPhoneNumber(), padrao);
            out.put(c.getId(), classifyOne(c, visitas, esperado, today));
        }
        return new Snapshot(out, mediana, padrao);
    }

    private static Classification classifyOne(Customer c, List<LocalDate> visitas, int esperado, LocalDate today) {
        LocalDate ultima = lastVisit(c, visitas);

        // Sem nenhuma visita registrada: só o cadastro. É "novo" enquanto for recente, e
        // "sumido" depois — um cadastro de seis meses que nunca virou atendimento é um
        // cliente perdido, não um cliente novo.
        if (ultima == null) {
            CustomerStatus s = isRecentlyCreated(c, today) ? CustomerStatus.NOVO : CustomerStatus.SUMIDO;
            return new Classification(s, null, 0, esperado, 0);
        }

        int diasParado = (int) ChronoUnit.DAYS.between(ultima, today);
        CustomerStatus status;
        if (visitas.size() <= 1 && isRecentlyCreated(c, today)) {
            status = CustomerStatus.NOVO;
        } else if (diasParado > esperado * LOST_FACTOR) {
            status = CustomerStatus.SUMIDO;
        } else if (diasParado > esperado * AT_RISK_FACTOR) {
            status = CustomerStatus.EM_RISCO;
        } else {
            status = CustomerStatus.ATIVO;
        }
        return new Classification(status, ultima, diasParado, esperado, visitas.size());
    }

    /**
     * Última visita: a mais recente entre os agendamentos e o {@code lastServiceDate} do cadastro.
     *
     * <p>Os dois são consultados porque o campo do cliente é atualizado também por atendimento
     * lançado em comanda sem agendamento (V28) — usar só os agendamentos marcaria como sumido
     * quem foi atendido ontem no balcão.
     */
    private static LocalDate lastVisit(Customer c, List<LocalDate> visitas) {
        LocalDate porAgendamento = visitas.stream().max(Comparator.naturalOrder()).orElse(null);
        LocalDate porCadastro = c.getLastServiceDate() == null ? null : c.getLastServiceDate().toLocalDate();
        if (porAgendamento == null) return porCadastro;
        if (porCadastro == null) return porAgendamento;
        return porAgendamento.isAfter(porCadastro) ? porAgendamento : porCadastro;
    }

    private static boolean isRecentlyCreated(Customer c, LocalDate today) {
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
     * ter estado na cadeira. Nesses casos vale a mediana da casa.
     */
    private static Integer averageInterval(List<LocalDate> visitas) {
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
    private static Integer median(List<Integer> valores) {
        if (valores.isEmpty()) return null;
        List<Integer> ordenados = valores.stream().sorted().toList();
        int meio = ordenados.size() / 2;
        return ordenados.size() % 2 == 1
                ? ordenados.get(meio)
                : (ordenados.get(meio - 1) + ordenados.get(meio)) / 2;
    }
}
