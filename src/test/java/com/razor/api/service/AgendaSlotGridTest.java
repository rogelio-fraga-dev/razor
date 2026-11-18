package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.razor.api.model.Appointment;
import com.razor.api.model.AppointmentSource;
import com.razor.api.model.Professional;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import com.razor.api.util.WorkingDays;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Grade completa de horarios (agendamento online) e a checagem de overbooking na hora de gravar,
 * que vale para bot, painel e site.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Grade de horários e checagem de overbooking")
class AgendaSlotGridTest {

    private static final UUID SHOP = UUID.randomUUID();
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private ProfessionalRepository professionalRepository;
    @Mock private ServiceOfferingRepository serviceOfferingRepository;
    @Mock private BarbershopSettingsService settingsService;
    @Mock private ScheduleBlockService scheduleBlockService;
    @Mock private CustomerService customerService;
    @Mock private AgendaTaskService taskService;
    @Mock private FidelityService fidelityService;
    @Mock private WhatsAppService whatsAppService;

    private AgendaAvailabilityService availability;
    private AgendaBookingService booking;
    private final List<Appointment> agenda = new ArrayList<>();
    private LocalDate dia;

    @BeforeEach
    void setUp() {
        TenantContext.set(SHOP);
        PricingService pricing = new PricingService(serviceOfferingRepository);
        availability = new AgendaAvailabilityService(appointmentRepository, professionalRepository,
                pricing, settingsService, scheduleBlockService);
        ProfessionalAssignmentService assignment = new ProfessionalAssignmentService(
                professionalRepository, appointmentRepository, scheduleBlockService, serviceOfferingRepository);
        booking = new AgendaBookingService(appointmentRepository, customerService, assignment,
                taskService, availability, settingsService, BookingLock.inline());

        // Expediente padrao: 09:00-19:00, seg a sab.
        when(settingsService.resolve()).thenReturn(new BarbershopSettingsService.BotSettings("", "", "", null, ""));
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(SHOP)).thenReturn(List.of());
        when(appointmentRepository.findByBarbershopIdAndAppointmentDate(eq(SHOP), any())).thenReturn(agenda);
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(eq(SHOP), any(), any()))
                .thenReturn(List.of());
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        dia = AppointmentDates.today().plusDays(3);
        while (!WorkingDays.DEFAULT.contains(dia.getDayOfWeek())) dia = dia.plusDays(1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void barbeiros(UUID... ids) {
        List<Professional> ps = new ArrayList<>();
        for (UUID id : ids) ps.add(Professional.builder().id(id).barbershopId(SHOP).name("P").active(true).build());
        when(professionalRepository.findByBarbershopIdAndActiveTrue(SHOP)).thenReturn(ps);
    }

    private void ocupado(UUID professionalId, String hora, String servico) {
        agenda.add(Appointment.builder().barbershopId(SHOP).professionalId(professionalId)
                .appointmentDate(dia).appointmentTime(LocalTime.parse(hora)).service(servico)
                .customerPhone("5511900000000").build());
    }

    private static LocalTime t(String hhmm) {
        return LocalTime.parse(hhmm);
    }

    @Test
    @DisplayName("a grade inteira sai, sem o corte em 4 opções do texto da IA")
    void fullGrid() {
        barbeiros(ANA);
        List<LocalTime> livres = availability.horariosLivres(dia, 30, null);
        assertThat(livres).hasSize(20).startsWith(t("09:00")).endsWith(t("18:30"));
    }

    @Test
    @DisplayName("serviço de 60min não é oferecido se não cabe até o fechamento")
    void durationRespectsClosing() {
        barbeiros(ANA);
        List<LocalTime> livres = availability.horariosLivres(dia, 60, null);
        assertThat(livres).contains(t("18:00")).doesNotContain(t("18:30"));
    }

    @Test
    @DisplayName("serviço de 60min não começa meia hora antes de um horário ocupado")
    void durationRespectsNextAppointment() {
        barbeiros(ANA);
        ocupado(ANA, "10:00", "Corte");
        List<LocalTime> livres = availability.horariosLivres(dia, 60, null);
        assertThat(livres).doesNotContain(t("09:30"), t("10:00")).contains(t("09:00"), t("10:30"));
    }

    @Test
    @DisplayName("combo existente (60min) ocupa as duas meias horas")
    void existingComboBlocksTwoSlots() {
        barbeiros(ANA);
        ocupado(ANA, "10:00", "Combo Corte + Barba");
        assertThat(availability.horariosLivres(dia, 30, null)).doesNotContain(t("10:00"), t("10:30")).contains(t("11:00"));
    }

    @Test
    @DisplayName("sem preferência basta um barbeiro livre; com preferência, vale o escolhido")
    void perProfessional() {
        barbeiros(ANA, BRUNO);
        ocupado(ANA, "10:00", "Corte");
        assertThat(availability.horariosLivres(dia, 30, null)).contains(t("10:00"));
        assertThat(availability.horariosLivres(dia, 30, ANA)).doesNotContain(t("10:00"));
        assertThat(availability.horariosLivres(dia, 30, BRUNO)).contains(t("10:00"));
    }

    @Test
    @DisplayName("agendamento sem barbeiro também ocupa cadeira (capacidade)")
    void unassignedConsumesCapacity() {
        barbeiros(ANA, BRUNO);
        ocupado(ANA, "10:00", "Corte");
        ocupado(null, "10:00", "Corte");
        assertThat(availability.horariosLivres(dia, 30, null)).doesNotContain(t("10:00"));
    }

    @Test
    @DisplayName("bloqueio do barbeiro tira o horário dele")
    void blockedProfessional() {
        barbeiros(ANA);
        when(scheduleBlockService.isBlocked(eq(dia), eq(t("12:00")), any(Integer.class), eq(ANA))).thenReturn(true);
        assertThat(availability.horariosLivres(dia, 30, null)).doesNotContain(t("12:00")).contains(t("11:30"));
    }

    @Test
    @DisplayName("dia fechado e data passada não têm grade")
    void closedAndPast() {
        barbeiros(ANA);
        LocalDate domingo = dia;
        while (domingo.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) domingo = domingo.plusDays(1);
        assertThat(availability.horariosLivres(domingo, 30, null)).isEmpty();
        assertThat(availability.horariosLivres(AppointmentDates.today().minusDays(1), 30, null)).isEmpty();
    }

    @Test
    @DisplayName("gravar num horário lotado é recusado — nada é salvo (overbooking)")
    void agendar_full_refuses() {
        barbeiros(ANA);
        ocupado(ANA, "10:00", "Corte");

        Optional<Appointment> r = booking.adicionarAgendamentoViaIA("João", "5511999990000",
                AppointmentDates.formatDate(dia), "10:00", "Corte");

        assertThat(r).isEmpty();
        verify(appointmentRepository, never()).save(any());
        verify(taskService, never()).criarTarefasAgendadas(any());
    }

    @Test
    @DisplayName("bot: preferido ocupado → grava com outro barbeiro e devolve QUEM foi atribuído")
    void agendar_preferredBusy_assignsOther() {
        barbeiros(ANA, BRUNO);
        ocupado(ANA, "10:00", "Corte");

        Optional<Appointment> r = booking.adicionarAgendamentoViaIA("João", "5511999990000",
                AppointmentDates.formatDate(dia), "10:00", "Corte", ANA);

        assertThat(r).isPresent();
        assertThat(r.get().getProfessionalId()).isEqualTo(BRUNO);
        assertThat(r.get().getSource()).isEqualTo(AppointmentSource.BOT);
    }

    @Test
    @DisplayName("site: barbeiro escolhido ocupado → recusa em vez de trocar de barbeiro")
    void agendar_strictPreferred_refuses() {
        barbeiros(ANA, BRUNO);
        ocupado(ANA, "10:00", "Corte");

        Optional<Appointment> r = booking.agendar(new AgendaBookingService.NovoAgendamento(
                "João", "5511999990000", dia, t("10:00"), "Corte", 30, ANA, true, AppointmentSource.SITE));

        assertThat(r).isEmpty();
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("gravação pelo site marca a origem SITE, cria lembrete/avaliação e não dá ponto de fidelidade")
    void agendar_site_sourceAndSideEffects() {
        barbeiros(ANA);

        Optional<Appointment> r = booking.agendar(new AgendaBookingService.NovoAgendamento(
                "João", "5511999990000", dia, t("15:00"), "Corte", 30, null, false, AppointmentSource.SITE));

        assertThat(r).isPresent();
        ArgumentCaptor<Appointment> saved = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(saved.capture());
        assertThat(saved.getValue().getSource()).isEqualTo(AppointmentSource.SITE);
        assertThat(saved.getValue().getProfessionalId()).isEqualTo(ANA);
        verify(taskService).criarTarefasAgendadas(saved.getValue());
        // Fidelidade vem no fechamento da comanda (atendimento feito), nunca na reserva.
        verifyNoInteractions(fidelityService);
    }

    @Test
    @DisplayName("sem barbeiro cadastrado o comportamento antigo segue: grava sem atribuir")
    void agendar_noProfessionals_stillBooks() {
        barbeiros();
        Optional<Appointment> r = booking.adicionarAgendamentoViaIA("João", "5511999990000",
                AppointmentDates.formatDate(dia), "10:00", "Corte");
        assertThat(r).isPresent();
        assertThat(r.get().getProfessionalId()).isNull();
    }

    @Test
    @DisplayName("grade só conta barbeiros que fazem o serviço: horário em que só o outro está livre não aparece")
    void grid_respectsServiceCapability() {
        barbeiros(ANA, BRUNO);
        ocupado(ANA, "10:00", "Corte");
        // So a ANA faz o servico pedido; o BRUNO esta livre as 10:00 mas nao conta.
        List<LocalTime> soAptos = availability.horariosLivres(dia, 30, null, List.of(ANA));
        assertThat(soAptos).doesNotContain(t("10:00")).contains(t("10:30"));
        // Sem restricao, as 10:00 o BRUNO atende.
        assertThat(availability.horariosLivres(dia, 30, null)).contains(t("10:00"));
    }

    // ---- Texto da IA com o servico pedido (so barbeiros aptos) --------------------------------

    private static final UUID CORTE = UUID.randomUUID();
    private static final UUID BARBA = UUID.randomUUID();

    /** ANA so faz barba; BRUNO so faz corte. Catalogo: Corte, Barba. */
    private ProfessionalAssignmentService catalogoComEspecialistas() {
        Professional ana = Professional.builder().id(ANA).barbershopId(SHOP).name("Ana").active(true)
                .serviceIds(new java.util.HashSet<>(java.util.Set.of(BARBA))).build();
        Professional bruno = Professional.builder().id(BRUNO).barbershopId(SHOP).name("Bruno").active(true)
                .serviceIds(new java.util.HashSet<>(java.util.Set.of(CORTE))).build();
        when(professionalRepository.findByBarbershopIdAndActiveTrue(SHOP)).thenReturn(List.of(ana, bruno));
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(SHOP)).thenReturn(List.of(
                com.razor.api.model.ServiceOffering.builder().id(CORTE).barbershopId(SHOP).name("Corte").build(),
                com.razor.api.model.ServiceOffering.builder().id(BARBA).barbershopId(SHOP).name("Barba").build()));
        when(appointmentRepository.findByBarbershopIdAndAppointmentDateGreaterThanEqual(eq(SHOP), any())).thenReturn(agenda);
        return new ProfessionalAssignmentService(professionalRepository, appointmentRepository, scheduleBlockService,
                serviceOfferingRepository);
    }

    @Test
    @DisplayName("bot: serviço citado na conversa vira os barbeiros que o fazem; sem serviço ou ninguém apto = desconhecido")
    void servicoCitado() {
        ProfessionalAssignmentService assignment = catalogoComEspecialistas();

        var barba = assignment.servicoCitado(List.of("amanhã às 10", "quero fazer a barba"));
        assertThat(barba.nome()).isEqualTo("Barba");
        assertThat(barba.aptos()).containsExactly(ANA);

        assertThat(assignment.servicoCitado(List.of("oi, tem horário amanhã?"))).isNull();
        // Ninguem faz corte E barba: segue a grade da barbearia toda (a gravacao tambem nao filtra).
        assertThat(assignment.servicoCitado(List.of("corte e barba"))).isNull();
        assertThat(assignment.servicoCitado(List.of())).isNull();
    }

    @Test
    @DisplayName("bot: horários livres para o serviço só contam quem o faz — e todos passam na checagem da gravação")
    void freeSlotsText_onlyQualified() {
        ProfessionalAssignmentService assignment = catalogoComEspecialistas();
        // A ANA (unica que faz barba) so tem as 15:00 livres; o BRUNO esta livre o dia todo.
        for (LocalTime h = t("09:00"); h.isBefore(t("19:00")); h = h.plusMinutes(30)) {
            if (!h.equals(t("15:00"))) ocupado(ANA, AppointmentDates.formatTime(h), "Barba");
        }
        var barba = assignment.servicoCitado(List.of("quero barba"));
        String data = AppointmentDates.formatDate(dia);

        assertThat(availability.getHorariosDisponiveisDoDia(data, barba.nome(), barba.aptos())).isEqualTo("15:00");
        assertThat(availability.checarHorario(dia, t("15:00"), 30, null, false, barba.aptos()).livre()).isTrue();
        // Para o corte (BRUNO) o dia inteiro serve: horario da barba nao vaza para outro servico.
        var corte = assignment.servicoCitado(List.of("quero um corte"));
        assertThat(availability.getHorariosDisponiveisDoDia(data, corte.nome(), corte.aptos())).contains("09:00");
    }

    @Test
    @DisplayName("bot: serviço desconhecido mantém o texto antigo da barbearia toda")
    void freeSlotsText_unknownServiceKeepsBehavior() {
        barbeiros(ANA, BRUNO);
        ocupado(ANA, "10:00", "Corte");
        String data = AppointmentDates.formatDate(dia);
        assertThat(availability.getHorariosDisponiveisDoDia(data, null, null))
                .isEqualTo(availability.getHorariosDisponiveisDoDia(data));
        assertThat(availability.getHorariosDisponiveisDoDia(data, "Barba", List.of()))
                .isEqualTo(availability.getHorariosDisponiveisDoDia(data));
    }

    @Test
    @DisplayName("bot: status da agenda marca ocupado quando nenhum barbeiro APTO está livre, mesmo com cadeira sobrando")
    void busyText_onlyQualified() {
        catalogoComEspecialistas();
        ocupado(ANA, "10:00", "Barba");

        // Visao geral: 2 cadeiras, 1 ocupada → nada lotado.
        assertThat(availability.getHorariosOcupadosParaIA()).contains("Agenda totalmente livre");
        // Para barba (so a ANA faz): 10:00 indisponivel; 10:30 segue livre.
        String barba = availability.getHorariosOcupadosParaIA("Barba", List.of(ANA));
        assertThat(barba).contains(AppointmentDates.formatDate(dia)).contains("10:00").doesNotContain("10:30");
        // Corte (BRUNO livre): nada indisponivel.
        assertThat(availability.getHorariosOcupadosParaIA("Corte", List.of(BRUNO))).contains("Agenda totalmente livre");
    }

    @Test
    @DisplayName("bot: combo de 60min não cabe meia hora antes de um horário ocupado do único apto")
    void busyText_respectsDuration() {
        catalogoComEspecialistas();
        ocupado(ANA, "10:30", "Barba");
        String combo = availability.getHorariosOcupadosParaIA("Combo Corte + Barba", List.of(ANA));
        assertThat(combo).contains("10:00").contains("10:30").doesNotContain("11:00");
    }
}
