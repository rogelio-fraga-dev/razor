package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.razor.api.model.Appointment;
import com.razor.api.model.Customer;
import com.razor.api.model.CustomerStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Status do cliente (regra única com a retenção)")
class CustomerStatusRulesTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 17);

    private Customer cliente(String phone, LocalDate criadoEm) {
        return Customer.builder().id(UUID.randomUUID()).phoneNumber(phone)
                .createdAt(criadoEm.atStartOfDay()).build();
    }

    private Appointment visita(String phone, LocalDate dia) {
        return Appointment.builder().customerPhone(phone).appointmentDate(dia).build();
    }

    private List<Appointment> ritmo(String phone, int intervalo, int quantas, int diasAtras) {
        List<Appointment> out = new ArrayList<>();
        for (int i = 0; i < quantas; i++) out.add(visita(phone, HOJE.minusDays(diasAtras + (long) i * intervalo)));
        return out;
    }

    private CustomerStatus status(Customer c, List<Appointment> visitas) {
        return CustomerStatusRules.classify(List.of(c), visitas, HOJE).byCustomer().get(c.getId()).status();
    }

    @Test
    @DisplayName("cadastro recente sem visita é NOVO")
    void recentWithoutVisits_isNovo() {
        assertThat(status(cliente("1", HOJE.minusDays(5)), List.of())).isEqualTo(CustomerStatus.NOVO);
    }

    @Test
    @DisplayName("primeira visita dentro de 30 dias de cadastro é NOVO")
    void firstVisitRecent_isNovo() {
        Customer c = cliente("1", HOJE.minusDays(20));
        assertThat(status(c, List.of(visita("1", HOJE.minusDays(10))))).isEqualTo(CustomerStatus.NOVO);
    }

    @Test
    @DisplayName("cadastro antigo que nunca virou visita é SUMIDO")
    void oldWithoutVisits_isSumido() {
        assertThat(status(cliente("1", HOJE.minusDays(200)), List.of())).isEqualTo(CustomerStatus.SUMIDO);
    }

    @Test
    @DisplayName("dentro do próprio ritmo é ATIVO; passou de 1x é EM_RISCO; de 2x é SUMIDO")
    void rhythmBuckets() {
        Customer c = cliente("1", HOJE.minusYears(1));
        assertThat(status(c, ritmo("1", 20, 5, 10))).isEqualTo(CustomerStatus.ATIVO);
        assertThat(status(c, ritmo("1", 20, 5, 30))).isEqualTo(CustomerStatus.EM_RISCO);
        assertThat(status(c, ritmo("1", 20, 5, 50))).isEqualTo(CustomerStatus.SUMIDO);
    }

    @Test
    @DisplayName("agendamento futuro não conta como visita")
    void futureAppointmentIsNotAVisit() {
        Customer c = cliente("1", HOJE.minusYears(1));
        List<Appointment> visitas = new ArrayList<>(ritmo("1", 20, 5, 50));
        visitas.add(visita("1", HOJE.plusDays(3)));
        assertThat(status(c, visitas)).isEqualTo(CustomerStatus.SUMIDO);
    }

    @Test
    @DisplayName("atendimento no balcão (lastServiceDate) conta como visita")
    void lastServiceDateCounts() {
        Customer c = cliente("1", HOJE.minusYears(1));
        c.setLastServiceDate(HOJE.minusDays(2).atStartOfDay());
        assertThat(status(c, ritmo("1", 20, 5, 50))).isEqualTo(CustomerStatus.ATIVO);
    }

    @Test
    @DisplayName("classificar pelas visitas agregadas (min/max/count do banco) dá o mesmo que pela lista inteira")
    void aggregatedStats_matchFullScan() {
        LocalDate hoje = LocalDate.of(2026, 11, 18);
        java.util.Random rnd = new java.util.Random(42);
        List<Customer> clientes = new ArrayList<>();
        List<Appointment> agendamentos = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            String tel = "55119" + String.format("%08d", i);
            clientes.add(cliente(tel, hoje.minusDays(rnd.nextInt(400))));
            int n = rnd.nextInt(6);
            for (int k = 0; k < n; k++) {
                agendamentos.add(Appointment.builder().customerPhone(tel)
                        .appointmentDate(hoje.minusDays(rnd.nextInt(300) - 10)).build()); // inclui futuros
            }
        }
        var cheio = CustomerStatusRules.classify(clientes, agendamentos, hoje);

        java.util.Map<String, CustomerStatusRules.VisitStats> stats = new java.util.HashMap<>();
        agendamentos.stream().filter(a -> !a.getAppointmentDate().isAfter(hoje)).forEach(a -> stats.merge(
                a.getCustomerPhone(),
                new CustomerStatusRules.VisitStats(a.getAppointmentDate(), a.getAppointmentDate(), 1),
                (x, y) -> new CustomerStatusRules.VisitStats(
                        x.first().isBefore(y.first()) ? x.first() : y.first(),
                        x.last().isAfter(y.last()) ? x.last() : y.last(), x.count() + y.count())));
        var agregado = CustomerStatusRules.classifyStats(clientes, stats, hoje);

        assertThat(agregado.byCustomer()).isEqualTo(cheio.byCustomer());
        assertThat(agregado.medianIntervalDays()).isEqualTo(cheio.medianIntervalDays());
    }
}
