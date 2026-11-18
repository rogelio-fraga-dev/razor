package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
                professionalRepository, appointmentRepository, scheduleBlockService);
        booking = new AgendaBookingService(appointmentRepository, customerService, assignment,
                taskService, fidelityService, whatsAppService, settingsService, availability);

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
    @DisplayName("gravação pelo site marca a origem SITE e cria lembrete/avaliação como o bot")
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
        verify(fidelityService).registerVisit("5511999990000");
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
}
