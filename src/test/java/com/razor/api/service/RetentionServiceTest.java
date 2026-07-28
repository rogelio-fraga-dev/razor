package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.razor.api.model.Appointment;
import com.razor.api.model.Customer;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.CustomerRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Retenção da base de clientes")
class RetentionServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private CustomerService customerService;
    @InjectMocks private RetentionService service;

    private static final LocalDate HOJE = LocalDate.of(2026, 7, 28);

    private Customer cliente(String phone, LocalDate criadoEm) {
        return Customer.builder()
                .id(UUID.randomUUID()).phoneNumber(phone).name("Cliente " + phone)
                .createdAt(criadoEm == null ? null : criadoEm.atStartOfDay())
                .build();
    }

    private Appointment visita(String phone, LocalDate dia) {
        return Appointment.builder().customerPhone(phone).appointmentDate(dia).build();
    }

    private void given(List<Customer> clientes, List<Appointment> visitas) {
        when(customerRepository.findByBarbershopId(any())).thenReturn(clientes);
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(visitas);
        when(customerService.subscriberPhones()).thenReturn(Set.of());
    }

    /** Visitas a cada `intervalo` dias, terminando `diasAtras` antes de hoje. */
    private List<Appointment> ritmo(String phone, int intervalo, int quantas, int diasAtras) {
        List<Appointment> out = new ArrayList<>();
        for (int i = 0; i < quantas; i++) {
            out.add(visita(phone, HOJE.minusDays(diasAtras + (long) i * intervalo)));
        }
        return out;
    }

    @Test
    @DisplayName("o status é relativo ao ritmo do cliente, não a um prazo fixo")
    void statusIsRelativeToEachCustomerRhythm() {
        // Os dois pararam há 40 dias. Quem corta a cada 15 está sumido; quem corta a cada 60
        // está em dia. Um limite único trataria os dois igual.
        Customer quinzenal = cliente("11111", HOJE.minusYears(1));
        Customer bimestral = cliente("22222", HOJE.minusYears(1));
        List<Appointment> visitas = new ArrayList<>();
        visitas.addAll(ritmo("11111", 15, 5, 40));
        visitas.addAll(ritmo("22222", 60, 5, 40));
        given(List.of(quinzenal, bimestral), visitas);

        var out = service.retention(HOJE);

        assertThat(out.counts().sumido()).isEqualTo(1);
        assertThat(out.counts().ativo()).isEqualTo(1);
        assertThat(out.lost()).singleElement()
                .satisfies(c -> assertThat(c.phone()).isEqualTo("11111"));
    }

    @Test
    @DisplayName("em risco é entre 1x e 2x o intervalo; sumido passa de 2x")
    void atRiskBetweenOneAndTwoIntervals() {
        // Ritmo de 20 dias. 25 dias parado = em risco; 50 = sumido.
        Customer risco = cliente("33333", HOJE.minusYears(1));
        Customer perdido = cliente("44444", HOJE.minusYears(1));
        List<Appointment> visitas = new ArrayList<>();
        visitas.addAll(ritmo("33333", 20, 4, 25));
        visitas.addAll(ritmo("44444", 20, 4, 50));
        given(List.of(risco, perdido), visitas);

        var out = service.retention(HOJE);

        assertThat(out.counts().emRisco()).isEqualTo(1);
        assertThat(out.counts().sumido()).isEqualTo(1);
        assertThat(out.atRisk()).singleElement()
                .satisfies(c -> {
                    assertThat(c.phone()).isEqualTo("33333");
                    assertThat(c.daysSinceLastVisit()).isEqualTo(25);
                    assertThat(c.expectedIntervalDays()).isEqualTo(20);
                });
    }

    @Test
    @DisplayName("cliente recém-cadastrado sem 2ª visita é NOVO, não sumido")
    void recentCustomerIsNewNotLost() {
        Customer novo = cliente("55555", HOJE.minusDays(10));
        given(List.of(novo), List.of(visita("55555", HOJE.minusDays(10))));

        var out = service.retention(HOJE);

        // Sem essa separação, toda barbearia veria a base inflada de "sumidos" que na verdade
        // acabaram de chegar.
        assertThat(out.counts().novo()).isEqualTo(1);
        assertThat(out.counts().sumido()).isZero();
    }

    @Test
    @DisplayName("cadastro antigo que nunca virou atendimento é sumido, não novo")
    void oldCustomerWithoutVisitsIsLost() {
        Customer fantasma = cliente("66666", HOJE.minusMonths(8));
        given(List.of(fantasma), List.of());

        var out = service.retention(HOJE);

        assertThat(out.counts().sumido()).isEqualTo(1);
        assertThat(out.counts().novo()).isZero();
        assertThat(out.lost()).singleElement()
                .satisfies(c -> assertThat(c.lastVisit()).isNull());
    }

    @Test
    @DisplayName("quem não tem ritmo próprio usa a mediana da barbearia")
    void singleVisitCustomerUsesShopMedian() {
        // Dois clientes com ritmo de 10 dias definem a mediana; o terceiro tem uma visita só.
        Customer a = cliente("77777", HOJE.minusYears(1));
        Customer b = cliente("88888", HOJE.minusYears(1));
        Customer soUma = cliente("99999", HOJE.minusMonths(6));
        List<Appointment> visitas = new ArrayList<>();
        visitas.addAll(ritmo("77777", 10, 4, 2));
        visitas.addAll(ritmo("88888", 10, 4, 2));
        visitas.add(visita("99999", HOJE.minusDays(30)));
        given(List.of(a, b, soUma), visitas);

        var out = service.retention(HOJE);

        assertThat(out.medianIntervalDays()).isEqualTo(10);
        assertThat(out.defaultIntervalDays()).isEqualTo(10);
        // 30 dias parado contra um esperado de 10 → passou de 2x → sumido.
        assertThat(out.lost()).extracting(c -> c.phone()).contains("99999");
    }

    @Test
    @DisplayName("sem ninguém com 2 visitas, a mediana é nula e vale o padrão de 30 dias")
    void noRepeatCustomers_usesFallback() {
        Customer c = cliente("10101", HOJE.minusMonths(6));
        given(List.of(c), List.of(visita("10101", HOJE.minusDays(20))));

        var out = service.retention(HOJE);

        assertThat(out.medianIntervalDays()).isNull();
        assertThat(out.defaultIntervalDays()).isEqualTo(RetentionService.FALLBACK_INTERVAL_DAYS);
        // 20 dias contra 30 esperados: ainda ativo.
        assertThat(out.counts().ativo()).isEqualTo(1);
    }

    @Test
    @DisplayName("atendimento de balcão conta como visita — o cadastro é consultado junto")
    void lastServiceDateFromCounterCounts() {
        // Comanda sem agendamento (V28) não gera linha em appointments, mas atualiza o cliente.
        // Sem olhar os dois, quem foi atendido ontem no balcão apareceria como sumido.
        Customer c = cliente("12121", HOJE.minusYears(1));
        c.setLastServiceDate(LocalDateTime.of(HOJE.minusDays(1), java.time.LocalTime.NOON));
        given(List.of(c), List.of(visita("12121", HOJE.minusDays(200))));

        var out = service.retention(HOJE);

        assertThat(out.counts().ativo()).isEqualTo(1);
        assertThat(out.counts().sumido()).isZero();
    }

    @Test
    @DisplayName("agendamento futuro não conta como visita já ocorrida")
    void futureAppointmentIsNotAVisit() {
        Customer c = cliente("13131", HOJE.minusYears(1));
        given(List.of(c), List.of(
                visita("13131", HOJE.minusDays(120)),
                visita("13131", HOJE.plusDays(3))));

        var out = service.retention(HOJE);

        // Marcar horário para semana que vem não desfaz os 120 dias parado.
        assertThat(out.counts().ativo()).isZero();
    }

    @Test
    @DisplayName("taxa de retorno é a fatia da base que voltou ao menos uma vez")
    void returnRate() {
        Customer voltou = cliente("14141", HOJE.minusYears(1));
        Customer soUma = cliente("15151", HOJE.minusYears(1));
        List<Appointment> visitas = new ArrayList<>(ritmo("14141", 20, 3, 5));
        visitas.add(visita("15151", HOJE.minusDays(5)));
        given(List.of(voltou, soUma), visitas);

        var out = service.retention(HOJE);

        assertThat(out.returnRate()).isEqualByComparingTo("50.0");
    }

    @Test
    @DisplayName("base vazia devolve taxa nula em vez de 0% ou divisão por zero")
    void emptyBase() {
        given(List.of(), List.of());

        var out = service.retention(HOJE);

        assertThat(out.totalCustomers()).isZero();
        assertThat(out.returnRate()).isNull();
        assertThat(out.atRisk()).isEmpty();
    }

    @Test
    @DisplayName("as listas vêm do mais atrasado para o menos — é quem corre mais risco")
    void listsSortedByDaysDesc() {
        Customer a = cliente("16161", HOJE.minusYears(1));
        Customer b = cliente("17171", HOJE.minusYears(1));
        List<Appointment> visitas = new ArrayList<>();
        visitas.addAll(ritmo("16161", 10, 3, 60));
        visitas.addAll(ritmo("17171", 10, 3, 90));
        given(List.of(a, b), visitas);

        var out = service.retention(HOJE);

        assertThat(out.lost()).extracting(c -> c.phone()).containsExactly("17171", "16161");
    }

    @Test
    @DisplayName("duas visitas no mesmo dia não viram ritmo — cai no padrão da casa")
    void sameDayVisitsDoNotCollapseInterval() {
        // Corte de manhã e barba à tarde (ou um lançamento duplicado) dão intervalo zero. Tratar
        // isso como o ritmo do cliente o classificaria como sumido poucos dias depois de ele ter
        // estado na cadeira.
        Customer c = cliente("18181", HOJE.minusYears(1));
        given(List.of(c), List.of(
                visita("18181", HOJE.minusDays(3)),
                visita("18181", HOJE.minusDays(3))));

        var out = service.retention(HOJE);

        // Nenhum ritmo utilizável na barbearia inteira: a mediana é nula e vale o padrão.
        assertThat(out.medianIntervalDays()).isNull();
        assertThat(out.defaultIntervalDays()).isEqualTo(RetentionService.FALLBACK_INTERVAL_DAYS);
        assertThat(out.counts().sumido()).isZero();
        assertThat(out.counts().ativo()).isEqualTo(1);
    }

    @Test
    @DisplayName("ritmo implausível (abaixo de uma semana) é descartado, não usado")
    void implausibleRhythmIsDiscarded() {
        // Um cliente com visitas a cada 2 dias é ruído de dado, não comportamento. Se esse
        // "ritmo" valesse, ele seria dado como sumido 5 dias depois da última visita.
        Customer ruidoso = cliente("19191", HOJE.minusYears(1));
        given(List.of(ruidoso), ritmo("19191", 2, 5, 10));

        var out = service.retention(HOJE);

        assertThat(out.medianIntervalDays()).isNull();
        // 10 dias parado contra os 30 do padrão: ativo.
        assertThat(out.counts().ativo()).isEqualTo(1);
    }
}
