package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Appointment;
import com.razor.api.model.ScheduledTask;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.ScheduledTaskRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tarefas da confirmacao da vespera (V42): quando nascem, presas ao agendamento e canceladas com ele. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendaTaskConfirmationTest {

    @Mock private ScheduledTaskRepository taskRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private BarbershopSettingsService barbershopSettingsService;
    @Mock private TenantAdminService tenantAdminService;

    private AgendaTaskService service;
    private final List<ScheduledTask> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new AgendaTaskService(taskRepository, customerRepository, barbershopSettingsService, tenantAdminService);
        when(barbershopSettingsService.resolve()).thenReturn(new BarbershopSettingsService.BotSettings("", "", "", null, ""));
        when(barbershopSettingsService.getNoShowSettings()).thenReturn(BarbershopSettingsService.NoShowSettings.DEFAULTS);
        when(taskRepository.save(any())).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
    }

    private Appointment appt(long id, LocalDate date, LocalTime time) {
        return Appointment.builder().id(id).customerPhone("5511911112222").customerName("Ana Souza")
                .service("Corte").appointmentDate(date).appointmentTime(time).build();
    }

    @Test
    @DisplayName("plano: vespera as 18h, prazo 3h antes")
    void plan_default() {
        LocalDateTime horario = LocalDateTime.of(2026, 12, 10, 15, 0);
        var plan = AgendaTaskService.planConfirmation(horario, LocalDateTime.of(2026, 12, 1, 10, 0), 18, 3);
        assertThat(plan).get().satisfies(p -> {
            assertThat(p.askAt()).isEqualTo(LocalDateTime.of(2026, 12, 9, 18, 0));
            assertThat(p.deadline()).isEqualTo(LocalDateTime.of(2026, 12, 10, 12, 0));
        });
    }

    @Test
    @DisplayName("plano: marcado depois das 18h da vespera (ou para hoje) nao pergunta")
    void plan_lateBooking_skipped() {
        LocalDateTime horario = LocalDateTime.of(2026, 12, 10, 15, 0);
        assertThat(AgendaTaskService.planConfirmation(horario, LocalDateTime.of(2026, 12, 9, 19, 0), 18, 3)).isEmpty();
        assertThat(AgendaTaskService.planConfirmation(horario, LocalDateTime.of(2026, 12, 10, 8, 0), 18, 3)).isEmpty();
    }

    @Test
    @DisplayName("plano: prazo antes da propria pergunta (madrugada) nao pergunta")
    void plan_deadlineBeforeAsk_skipped() {
        LocalDateTime horario = LocalDateTime.of(2026, 12, 10, 0, 30);
        assertThat(AgendaTaskService.planConfirmation(horario, LocalDateTime.of(2026, 12, 1, 10, 0), 23, 3)).isEmpty();
    }

    @Test
    @DisplayName("agendamento com antecedencia ganha pergunta e prazo presos ao appointmentId")
    void tasksLinkedToAppointment() {
        Appointment ag = appt(99L, AppointmentDates.today().plusDays(3), LocalTime.of(15, 0));

        service.criarTarefasAgendadas(ag);

        assertThat(saved).extracting(ScheduledTask::getTaskType)
                .containsExactlyInAnyOrder("REMINDER",
                        AgendaTaskService.CONFIRMATION_REQUEST, AgendaTaskService.CONFIRMATION_DEADLINE);
        assertThat(saved).allSatisfy(t -> assertThat(t.getAppointmentId()).isEqualTo(99L));
        ScheduledTask pergunta = saved.stream()
                .filter(t -> AgendaTaskService.CONFIRMATION_REQUEST.equals(t.getTaskType())).findFirst().orElseThrow();
        assertThat(pergunta.getMessageContent()).contains("*1*").contains("*2*").contains("Ana");
        assertThat(pergunta.getExecutionTime()).isEqualTo(ag.getAppointmentDate().minusDays(1).atTime(18, 0));
    }

    @Test
    @DisplayName("confirmacao desligada: so o lembrete (a avaliacao sai depois da comanda, V46)")
    void disabled_noConfirmationTasks() {
        when(barbershopSettingsService.getNoShowSettings())
                .thenReturn(new BarbershopSettingsService.NoShowSettings(false, 18, 3, false, null, null));
        service.criarTarefasAgendadas(appt(5L, AppointmentDates.today().plusDays(3), LocalTime.of(15, 0)));
        assertThat(saved).extracting(ScheduledTask::getTaskType).containsExactlyInAnyOrder("REMINDER");
    }

    @Test
    @DisplayName("cancelar o agendamento cancela tambem a pergunta e o prazo dele")
    void cancelledWithAppointment() {
        Appointment ag = appt(99L, AppointmentDates.today().plusDays(3), LocalTime.of(15, 0));
        service.criarTarefasAgendadas(ag);
        List<ScheduledTask> pendentes = new ArrayList<>(saved);
        pendentes.forEach(t -> t.setStatus(ScheduledTask.TaskStatus.PENDING));
        when(taskRepository.findByBarbershopIdAndAppointmentIdAndStatus(
                TenantContext.getCurrent(), 99L, ScheduledTask.TaskStatus.PENDING)).thenReturn(pendentes);

        int n = service.cancelarTarefasDoAgendamento(ag);

        assertThat(n).isEqualTo(3);
        ArgumentCaptor<ScheduledTask> cap = ArgumentCaptor.forClass(ScheduledTask.class);
        verify(taskRepository, atLeastOnce()).save(cap.capture());
        assertThat(pendentes).allSatisfy(t -> assertThat(t.getStatus()).isEqualTo(ScheduledTask.TaskStatus.CANCELLED));
    }
}
