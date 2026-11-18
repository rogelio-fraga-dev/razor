package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.razor.api.dto.AiImpactResponse;
import com.razor.api.model.AiImpactEvent;
import com.razor.api.model.Appointment;
import com.razor.api.model.AppointmentSource;
import com.razor.api.model.AttendanceStatus;
import com.razor.api.model.Customer;
import com.razor.api.model.Interaction;
import com.razor.api.repository.AiImpactEventRepository;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.InteractionRepository;
import com.razor.api.service.PricingService.ServiceCategory;
import com.razor.api.util.WorkingDays;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Card "O que a IA fez por voce" (V42): numeros medidos, por origem, fora do horario, resgates. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiImpactServiceTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private InteractionRepository interactionRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private AiImpactEventRepository eventRepository;
    @Mock private PricingService pricingService;
    @Mock private BarbershopSettingsService barbershopSettingsService;
    @InjectMocks private AiImpactService service;

    // Novembro de 2026: 01/11 e domingo.
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 17);

    private final List<Appointment> created = new ArrayList<>();
    private final List<Appointment> all = new ArrayList<>();
    private final List<LocalDateTime> botMsgs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Catalogo real pelo calculo unico: Corte 40, Barba 30 e o combo "Corte e Barba" 60.
        when(pricingService.quoter()).thenReturn(PricingService.Quoter.of(List.of(
                com.razor.api.model.ServiceOffering.builder().id(UUID.randomUUID()).name("Corte").price(new BigDecimal("40.00")).build(),
                com.razor.api.model.ServiceOffering.builder().id(UUID.randomUUID()).name("Barba").price(new BigDecimal("30.00")).build(),
                com.razor.api.model.ServiceOffering.builder().id(UUID.randomUUID()).name("Corte e Barba").price(new BigDecimal("60.00")).build())));
        when(appointmentRepository.findByBarbershopIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(), eq(FROM.atStartOfDay()), eq(TO.plusDays(1).atStartOfDay()))).thenReturn(created);
        when(appointmentRepository.findByBarbershopId(any())).thenReturn(all);
        when(interactionRepository.findTimestamps(any(), eq(Interaction.InteractionType.BOT), any(), any())).thenReturn(botMsgs);
        // Seg a sab, 09:00-19:00.
        when(barbershopSettingsService.resolve()).thenReturn(new BarbershopSettingsService.BotSettings(
                "", "", "", null, "", "Barbearia", null, null, null, "09:00", "19:00",
                WorkingDays.parse("1,2,3,4,5,6"), null, null, null, null, null, null));
        when(customerRepository.findByBarbershopId(any())).thenReturn(List.of());
    }

    private Appointment booking(AppointmentSource src, String service, String phone, LocalDateTime createdAt, LocalDate date) {
        Appointment a = Appointment.builder().source(src).service(service).customerPhone(phone)
                .appointmentDate(date).appointmentTime(LocalTime.of(10, 0)).createdAt(createdAt).build();
        created.add(a);
        all.add(a);
        return a;
    }

    @Test
    @DisplayName("separa bot, site e painel; valor pela tabela; falta nao conta")
    void bySource() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 10, 14, 0);
        booking(AppointmentSource.BOT, "Corte", "1", t, TO.plusDays(3));
        booking(AppointmentSource.BOT, "Corte e Barba", "2", t, TO.plusDays(3));
        booking(AppointmentSource.BOT, "Corte", "3", t, LocalDate.of(2026, 11, 11)).setAttendanceStatus(AttendanceStatus.NO_SHOW);
        booking(AppointmentSource.SITE, "Barba", "4", t, TO.plusDays(1));
        booking(AppointmentSource.PAINEL, "Corte", "5", t, TO.plusDays(1));
        booking(AppointmentSource.PAINEL, "Corte", "6", t, TO.plusDays(1));

        AiImpactResponse r = service.impact(FROM, TO);

        assertThat(r.bot().appointments()).isEqualTo(2);
        assertThat(r.bot().revenue()).isEqualByComparingTo("100.00");
        assertThat(r.site().appointments()).isEqualTo(1);
        assertThat(r.site().revenue()).isEqualByComparingTo("30.00");
        assertThat(r.panel().appointments()).isEqualTo(2);
        assertThat(r.panel().revenue()).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("periodo vazio devolve zeros (nunca inventa numero)")
    void empty_zeros() {
        AiImpactResponse r = service.impact(FROM, TO);
        assertThat(r.bot().appointments()).isZero();
        assertThat(r.bot().revenue()).isEqualByComparingTo("0");
        assertThat(r.botMessages()).isZero();
        assertThat(r.botMessagesOutsideHours()).isZero();
        assertThat(r.rescuedCustomers()).isZero();
        assertThat(r.noShowsAvoided()).isZero();
    }

    @Test
    @DisplayName("mensagens fora do expediente: domingo, antes de abrir e a partir do fechamento")
    void outsideHours() {
        botMsgs.add(LocalDateTime.of(2026, 11, 2, 10, 0));  // seg 10h — dentro
        botMsgs.add(LocalDateTime.of(2026, 11, 2, 18, 59)); // seg 18:59 — dentro
        botMsgs.add(LocalDateTime.of(2026, 11, 2, 19, 0));  // seg 19:00 — fora (fechou)
        botMsgs.add(LocalDateTime.of(2026, 11, 3, 7, 30));  // ter 7:30 — fora
        botMsgs.add(LocalDateTime.of(2026, 11, 1, 12, 0));  // dom — fora (fechado)

        AiImpactResponse r = service.impact(FROM, TO);

        assertThat(r.botMessages()).isEqualTo(5);
        assertThat(r.botMessagesOutsideHours()).isEqualTo(3);
    }

    @Test
    @DisplayName("isOutsideHours: regra pura")
    void outsideHoursRule() {
        Set<DayOfWeek> dias = Set.of(DayOfWeek.MONDAY);
        LocalTime abre = LocalTime.of(9, 0), fecha = LocalTime.of(19, 0);
        assertThat(AiImpactService.isOutsideHours(LocalDateTime.of(2026, 11, 2, 9, 0), dias, abre, fecha)).isFalse();
        assertThat(AiImpactService.isOutsideHours(LocalDateTime.of(2026, 11, 2, 8, 59), dias, abre, fecha)).isTrue();
        assertThat(AiImpactService.isOutsideHours(LocalDateTime.of(2026, 11, 3, 10, 0), dias, abre, fecha)).isTrue();
    }

    @Test
    @DisplayName("resgatado: cliente sumido que marcou pelo bot; ativo e novo nao contam; site nao conta")
    void rescued() {
        UUID sumidoId = UUID.randomUUID(), ativoId = UUID.randomUUID(), novoId = UUID.randomUUID(), siteId = UUID.randomUUID();
        Customer sumido = Customer.builder().id(sumidoId).phoneNumber("S").createdAt(LocalDateTime.of(2025, 1, 1, 0, 0)).build();
        Customer ativo = Customer.builder().id(ativoId).phoneNumber("A").createdAt(LocalDateTime.of(2025, 1, 1, 0, 0)).build();
        Customer novo = Customer.builder().id(novoId).phoneNumber("N").createdAt(LocalDateTime.of(2026, 11, 9, 0, 0)).build();
        Customer viaSite = Customer.builder().id(siteId).phoneNumber("X").createdAt(LocalDateTime.of(2025, 1, 1, 0, 0)).build();
        when(customerRepository.findByBarbershopId(any())).thenReturn(List.of(sumido, ativo, novo, viaSite));

        // Historico: ritmo de 30 dias. "S" parou em maio; "A" veio em outubro; "X" parou em maio.
        for (String p : List.of("S", "X")) {
            all.add(Appointment.builder().customerPhone(p).appointmentDate(LocalDate.of(2026, 4, 1)).build());
            all.add(Appointment.builder().customerPhone(p).appointmentDate(LocalDate.of(2026, 5, 1)).build());
        }
        all.add(Appointment.builder().customerPhone("A").appointmentDate(LocalDate.of(2026, 9, 20)).build());
        all.add(Appointment.builder().customerPhone("A").appointmentDate(LocalDate.of(2026, 10, 20)).build());

        LocalDateTime t = LocalDateTime.of(2026, 11, 10, 14, 0);
        booking(AppointmentSource.BOT, "Corte", "S", t, LocalDate.of(2026, 11, 12));
        booking(AppointmentSource.BOT, "Corte", "S", t.plusDays(1), LocalDate.of(2026, 11, 20)); // mesmo cliente: 1 resgate
        booking(AppointmentSource.BOT, "Corte", "A", t, LocalDate.of(2026, 11, 12));
        booking(AppointmentSource.BOT, "Corte", "N", t, LocalDate.of(2026, 11, 12));
        booking(AppointmentSource.SITE, "Corte", "X", t, LocalDate.of(2026, 11, 12));

        assertThat(service.impact(FROM, TO).rescuedCustomers()).isEqualTo(1);
    }

    @Test
    @DisplayName("anti-falta vem dos eventos; faltas evitadas = desmarcacoes pela confirmacao")
    void events() {
        when(eventRepository.countByBarbershopIdAndEventTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(), eq(AiImpactEvent.Type.CONFIRMED), any(), any())).thenReturn(12L);
        when(eventRepository.countByBarbershopIdAndEventTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(), eq(AiImpactEvent.Type.DECLINED), any(), any())).thenReturn(3L);
        when(eventRepository.countByBarbershopIdAndEventTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(), eq(AiImpactEvent.Type.WAITLIST_REFILL), any(), any())).thenReturn(2L);

        AiImpactResponse r = service.impact(FROM, TO);

        assertThat(r.confirmationsReceived()).isEqualTo(12);
        assertThat(r.slotsFreedInAdvance()).isEqualTo(3);
        assertThat(r.noShowsAvoided()).isEqualTo(3);
        assertThat(r.waitlistRefills()).isEqualTo(2);
        assertThat(r.autoReleased()).isZero();
    }

    @Test
    @DisplayName("periodos: mes corrente ate hoje e ultimos 30 dias")
    void ranges() {
        LocalDate hoje = LocalDate.of(2026, 11, 17);
        assertThat(AiImpactService.range(AiImpactService.Period.MONTH, hoje)).containsExactly(LocalDate.of(2026, 11, 1), hoje);
        assertThat(AiImpactService.range(AiImpactService.Period.LAST_30_DAYS, hoje)).containsExactly(LocalDate.of(2026, 10, 19), hoje);
    }
}
