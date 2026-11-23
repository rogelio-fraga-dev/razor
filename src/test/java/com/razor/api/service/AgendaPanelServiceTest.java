package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.dto.AgendaBoardResponse;
import com.razor.api.model.Appointment;
import com.razor.api.model.AttendanceStatus;
import com.razor.api.model.Comanda;
import com.razor.api.model.PaymentStatus;
import com.razor.api.model.Professional;
import com.razor.api.model.ScheduleBlock;
import com.razor.api.model.ServiceOffering;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.ComandaRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Agenda interativa do painel: as regras de encaixe ao remarcar/criar (expediente, dia fechado,
 * folga, sobreposicao pela duracao, barbeiro inativo), o lembrete que acompanha a remarcacao e o
 * aviso ao cliente pelo WhatsApp.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendaPanelServiceTest {

    private static final String PHONE = "5511999990000";

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private ProfessionalRepository professionalRepository;
    @Mock private ServiceOfferingRepository serviceOfferingRepository;
    @Mock private ComandaRepository comandaRepository;
    @Mock private ScheduleBlockService scheduleBlockService;
    @Mock private BarbershopSettingsService barbershopSettingsService;
    @Mock private CustomerService customerService;
    @Mock private AgendaBookingService bookingService;
    @Mock private AgendaTaskService taskService;
    @Mock private WhatsAppService whatsAppService;
    @Mock private WaitlistOfferService waitlistOfferService;

    private AgendaPanelService service;
    private UUID shop;
    private Professional rafael;
    private Professional bruno;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        shop = TenantContext.getCurrent();
        // Duracoes reais da regra por categoria (catalogo vazio = padroes: corte 30, combo 60).
        PricingService pricing = new PricingService(serviceOfferingRepository);
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of());

        service = new AgendaPanelService(appointmentRepository, professionalRepository, serviceOfferingRepository,
                comandaRepository, scheduleBlockService, barbershopSettingsService, pricing, customerService,
                bookingService, taskService, whatsAppService, BookingLock.inline(), waitlistOfferService);

        // Expediente padrao: 09:00-19:00, segunda a sabado.
        when(barbershopSettingsService.resolve())
                .thenReturn(new BarbershopSettingsService.BotSettings("", "", "", null, ""));

        rafael = Professional.builder().id(UUID.randomUUID()).barbershopId(shop).name("Rafael").active(true).build();
        bruno = Professional.builder().id(UUID.randomUUID()).barbershopId(shop).name("Bruno").active(true).build();
        when(professionalRepository.findByBarbershopId(shop)).thenReturn(List.of(rafael, bruno));
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(any(), any(), any()))
                .thenReturn(List.of());
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        day = nextOpen(DayOfWeek.WEDNESDAY);
    }

    // ------------------------------------------------------------------ remarcar: validacao

    @Test
    @DisplayName("remarcar por cima de outro horário do barbeiro (pela duração) é recusado")
    void move_overlapByDuration_rejected() {
        Appointment ag = appointment(1L, "Corte", rafael.getId(), day, LocalTime.of(10, 0));
        // Combo das 14:00 dura 60min: 14:30 colide mesmo nao sendo o horario de inicio.
        Appointment combo = appointment(2L, "Combo", bruno.getId(), day, LocalTime.of(14, 0));
        combo.setCustomerName("Marcos Silva");
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(ag));
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(shop, bruno.getId(), day))
                .thenReturn(List.of(combo));

        assertThatThrownBy(() -> service.move(1L, day, LocalTime.of(14, 30), bruno.getId(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bruno já tem Marcos às 14:00");

        assertUntouched(ag, LocalTime.of(10, 0), rafael.getId());
    }

    @Test
    @DisplayName("serviço que termina depois do início do próximo também colide")
    void move_ownDurationOverlapsNext_rejected() {
        Appointment combo = appointment(1L, "Combo", rafael.getId(), day, LocalTime.of(10, 0));
        Appointment next = appointment(2L, "Corte", bruno.getId(), day, LocalTime.of(15, 0));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(combo));
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(shop, bruno.getId(), day))
                .thenReturn(List.of(next));

        // 14:30 + 60min = 15:30 > 15:00
        assertThatThrownBy(() -> service.move(1L, day, LocalTime.of(14, 30), bruno.getId(), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("arrastar meia hora para frente não colide com o próprio horário antigo")
    void move_ignoresItself() {
        Appointment combo = appointment(1L, "Combo", rafael.getId(), day, LocalTime.of(10, 0));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(combo));
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(shop, rafael.getId(), day))
                .thenReturn(List.of(combo));

        service.move(1L, day, LocalTime.of(10, 30), rafael.getId(), true);

        assertThat(combo.getAppointmentTime()).isEqualTo(LocalTime.of(10, 30));
    }

    @Test
    @DisplayName("horário de cliente que faltou não bloqueia o encaixe")
    void move_noShowFreesTheChair() {
        Appointment ag = appointment(1L, "Corte", rafael.getId(), day, LocalTime.of(10, 0));
        Appointment faltou = appointment(2L, "Corte", bruno.getId(), day, LocalTime.of(14, 0));
        faltou.setAttendanceStatus(AttendanceStatus.NO_SHOW);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(ag));
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(shop, bruno.getId(), day))
                .thenReturn(List.of(faltou));

        service.move(1L, day, LocalTime.of(14, 0), bruno.getId(), false);

        assertThat(ag.getProfessionalId()).isEqualTo(bruno.getId());
    }

    @Test
    @DisplayName("folga/almoço do barbeiro recusa o horário")
    void move_blocked_rejected() {
        Appointment ag = appointment(1L, "Corte", rafael.getId(), day, LocalTime.of(10, 0));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(ag));
        when(scheduleBlockService.isBlocked(day, LocalTime.of(12, 0), 30, rafael.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.move(1L, day, LocalTime.of(12, 0), rafael.getId(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bloqueado");
        assertUntouched(ag, LocalTime.of(10, 0), rafael.getId());
    }

    @Test
    @DisplayName("fora do expediente: serviço que passa do fechamento é recusado")
    void move_afterClosing_rejected() {
        Appointment combo = appointment(1L, "Combo", rafael.getId(), day, LocalTime.of(10, 0));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(combo));

        // 18:30 + 60min = 19:30 > 19:00
        assertThatThrownBy(() -> service.move(1L, day, LocalTime.of(18, 30), rafael.getId(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Fora do expediente");
        assertThatThrownBy(() -> service.move(1L, day, LocalTime.of(8, 30), rafael.getId(), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("dia em que a barbearia não abre é recusado")
    void move_closedDay_rejected() {
        Appointment ag = appointment(1L, "Corte", rafael.getId(), day, LocalTime.of(10, 0));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(ag));

        assertThatThrownBy(() -> service.move(1L, nextOpen(DayOfWeek.SUNDAY), LocalTime.of(10, 0), rafael.getId(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não abre");
    }

    @Test
    @DisplayName("barbeiro inativo e horário no passado são recusados")
    void move_inactiveOrPast_rejected() {
        Professional inativo = Professional.builder().id(UUID.randomUUID()).barbershopId(shop).name("Zé").active(false).build();
        when(professionalRepository.findByBarbershopId(shop)).thenReturn(List.of(rafael, bruno, inativo));
        Appointment ag = appointment(1L, "Corte", rafael.getId(), day, LocalTime.of(10, 0));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(ag));

        assertThatThrownBy(() -> service.move(1L, day, LocalTime.of(11, 0), inativo.getId(), true))
                .hasMessageContaining("inativo");
        assertThatThrownBy(() -> service.move(1L, AppointmentDates.today().minusDays(2), LocalTime.of(11, 0), rafael.getId(), true))
                .hasMessageContaining("já passou");
    }

    @Test
    @DisplayName("agendamento de outra barbearia não é encontrado")
    void move_otherTenant_notFound() {
        Appointment alheio = appointment(1L, "Corte", rafael.getId(), day, LocalTime.of(10, 0));
        alheio.setBarbershopId(UUID.randomUUID());
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(alheio));

        assertThatThrownBy(() -> service.move(1L, day, LocalTime.of(11, 0), rafael.getId(), true))
                .isInstanceOf(NotFoundException.class);
    }

    // ------------------------------------------------------------------ remarcar: efeitos

    @Test
    @DisplayName("remarcar: lembrete do horário antigo cancelado ANTES da troca, novo criado depois, cliente avisado")
    void move_reschedulesTasksAndNotifies() {
        Appointment ag = appointment(1L, "Corte", rafael.getId(), day, LocalTime.of(10, 0));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(ag));
        List<LocalTime> horarioNoCancelamento = new ArrayList<>();
        doAnswer(inv -> {
            horarioNoCancelamento.add(((Appointment) inv.getArgument(0)).getAppointmentTime());
            return 2;
        }).when(taskService).cancelarTarefasDoAgendamento(any());

        LocalDate novoDia = day.plusDays(1);
        service.move(1L, novoDia, LocalTime.of(16, 30), bruno.getId(), true);

        // O reconhecimento das tarefas legadas usa o horario: precisa ver o ANTIGO.
        assertThat(horarioNoCancelamento).containsExactly(LocalTime.of(10, 0));
        InOrder order = inOrder(taskService, appointmentRepository);
        order.verify(taskService).cancelarTarefasDoAgendamento(ag);
        order.verify(appointmentRepository).save(ag);
        order.verify(taskService).criarTarefasAgendadas(argThat(a ->
                a.getAppointmentDate().equals(novoDia) && a.getAppointmentTime().equals(LocalTime.of(16, 30))));

        assertThat(ag.getProfessionalId()).isEqualTo(bruno.getId());
        verify(whatsAppService).sendTextMessage(eq(PHONE), argThat(m ->
                m.contains("remarcado") && m.contains("16:30") && m.contains("Bruno")
                        && m.contains(String.format("%02d/%02d", novoDia.getDayOfMonth(), novoDia.getMonthValue()))));
    }

    @Test
    @DisplayName("trocar só o barbeiro avisa o cliente da troca, mantendo o horário")
    void move_onlyBarberChanged_notifiesBarberChange() {
        Appointment ag = appointment(1L, "Corte", rafael.getId(), day, LocalTime.of(10, 0));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(ag));

        service.move(1L, day, LocalTime.of(10, 0), bruno.getId(), true);

        verify(whatsAppService).sendTextMessage(eq(PHONE), argThat(m -> m.contains("continua o mesmo") && m.contains("Bruno")));
    }

    @Test
    @DisplayName("remarcar sem aviso não manda WhatsApp, mas o lembrete acompanha")
    void move_withoutNotify() {
        Appointment ag = appointment(1L, "Corte", rafael.getId(), day, LocalTime.of(10, 0));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(ag));

        service.move(1L, day, LocalTime.of(11, 0), rafael.getId(), false);

        verify(whatsAppService, never()).sendTextMessage(anyString(), anyString());
        verify(taskService).criarTarefasAgendadas(ag);
    }

    @Test
    @DisplayName("soltar no mesmo lugar não grava nem avisa")
    void move_samePlace_noop() {
        Appointment ag = appointment(1L, "Corte", rafael.getId(), day, LocalTime.of(10, 0));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(ag));

        service.move(1L, day, LocalTime.of(10, 0), rafael.getId(), true);

        verify(appointmentRepository, never()).save(any());
        verify(whatsAppService, never()).sendTextMessage(anyString(), anyString());
    }

    // ------------------------------------------------------------------ cancelar

    @Test
    @DisplayName("cancelar pelo painel usa o caminho do bot e avisa o cliente")
    void cancel_future_notifies() {
        Appointment ag = appointment(1L, "Corte", rafael.getId(), day, LocalTime.of(10, 0));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(ag));

        service.cancel(1L, true);

        verify(bookingService).cancelarAgendamentoExato(ag);
        verify(whatsAppService).sendTextMessage(eq(PHONE), contains("cancelado"));
    }

    @Test
    @DisplayName("cancelar horário que já passou não manda aviso")
    void cancel_past_noNotice() {
        Appointment ag = appointment(1L, "Corte", rafael.getId(), AppointmentDates.today().minusDays(1), LocalTime.of(10, 0));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(ag));

        service.cancel(1L, true);

        verify(bookingService).cancelarAgendamentoExato(ag);
        verify(whatsAppService, never()).sendTextMessage(anyString(), anyString());
    }

    // ------------------------------------------------------------------ criar

    @Test
    @DisplayName("criar pelo painel valida, cadastra o cliente e grava pelo AgendaBookingService")
    void create_goesThroughBookingService() {
        ServiceOffering corte = ServiceOffering.builder().id(UUID.randomUUID()).barbershopId(shop).name("Corte").active(true).build();
        when(serviceOfferingRepository.findById(corte.getId())).thenReturn(Optional.of(corte));
        when(bookingService.agendarPeloPainel(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> Appointment.builder().id(9L).customerName(inv.getArgument(0))
                        .customerPhone(inv.getArgument(1)).build());

        service.create("João Souza", "(11) 99999-0000", corte.getId(), rafael.getId(), day, LocalTime.of(15, 0), true);

        verify(customerService).findOrCreateCustomer(PHONE, "João Souza");
        verify(bookingService).agendarPeloPainel("João Souza", PHONE, day, LocalTime.of(15, 0), "Corte", rafael.getId());
        verify(whatsAppService).sendTextMessage(eq(PHONE), contains("confirmado"));
    }

    @Test
    @DisplayName("criar em horário ocupado não grava nada")
    void create_conflict_doesNotBook() {
        ServiceOffering corte = ServiceOffering.builder().id(UUID.randomUUID()).barbershopId(shop).name("Corte").active(true).build();
        when(serviceOfferingRepository.findById(corte.getId())).thenReturn(Optional.of(corte));
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(shop, rafael.getId(), day))
                .thenReturn(List.of(appointment(5L, "Corte", rafael.getId(), day, LocalTime.of(15, 0))));

        assertThatThrownBy(() -> service.create("João", "11999990000", corte.getId(), rafael.getId(), day,
                LocalTime.of(15, 0), true)).isInstanceOf(IllegalArgumentException.class);

        verify(bookingService, never()).agendarPeloPainel(any(), any(), any(), any(), any(), any());
        verify(customerService, never()).findOrCreateCustomer(any(), any());
    }

    @Test
    @DisplayName("telefone do painel vira o formato do WhatsApp")
    void normalizePhone() {
        assertThat(AgendaPanelService.normalizePhone("(11) 99999-0000")).isEqualTo("5511999990000");
        assertThat(AgendaPanelService.normalizePhone("+55 11 9999-0000")).isEqualTo("551199990000");
        assertThatThrownBy(() -> AgendaPanelService.normalizePhone("1234")).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ comparecimento

    @Test
    @DisplayName("não dá para marcar falta num horário que ainda não começou")
    void attendance_future_rejected() {
        Appointment ag = appointment(1L, "Corte", rafael.getId(), day, LocalTime.of(10, 0));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(ag));

        assertThatThrownBy(() -> service.setAttendance(1L, AttendanceStatus.NO_SHOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("falta registrada cancela o pedido de avaliação pendente")
    void attendance_noShow_cancelsTasks() {
        Appointment ag = appointment(1L, "Corte", rafael.getId(), AppointmentDates.today().minusDays(1), LocalTime.of(10, 0));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(ag));

        service.setAttendance(1L, AttendanceStatus.NO_SHOW);

        assertThat(ag.getAttendanceStatus()).isEqualTo(AttendanceStatus.NO_SHOW);
        verify(taskService).cancelarTarefasDoAgendamento(ag);
    }

    // ------------------------------------------------------------------ grade

    @Test
    @DisplayName("grade: inativo com horário vira coluna, bloqueio recorrente expande por dia, comanda aparece")
    void board_columnsBlocksAndComanda() {
        Professional inativo = Professional.builder().id(UUID.randomUUID()).barbershopId(shop).name("Zé").active(false).build();
        Professional inativoSemHorario = Professional.builder().id(UUID.randomUUID()).barbershopId(shop).name("Léo").active(false).build();
        when(professionalRepository.findByBarbershopId(shop)).thenReturn(List.of(rafael, inativo, inativoSemHorario));
        Appointment doZe = appointment(3L, "Combo", inativo.getId(), day, LocalTime.of(9, 0));
        when(appointmentRepository.findByBarbershopIdAndAppointmentDateBetween(shop, day, day.plusDays(6)))
                .thenReturn(List.of(doZe));
        when(customerService.subscriberPhones()).thenReturn(Set.of(PHONE));
        UUID comandaId = UUID.randomUUID();
        when(comandaRepository.findByBarbershopIdAndAppointmentIdIn(eq(shop), any())).thenReturn(List.of(
                Comanda.builder().id(comandaId).appointmentId(3L).status(Comanda.Status.ABERTA).build()));
        // Almoco toda quarta 12-13h.
        when(scheduleBlockService.listAll()).thenReturn(List.of(ScheduleBlock.builder().id(UUID.randomUUID())
                .weekday((short) 3).startTime(LocalTime.NOON).endTime(LocalTime.of(13, 0)).build()));

        AgendaBoardResponse board = service.board(day, day.plusDays(6));

        assertThat(board.professionals()).extracting(AgendaBoardResponse.Pro::name).containsExactly("Rafael", "Zé");
        assertThat(board.appointments()).singleElement().satisfies(i -> {
            assertThat(i.durationMinutes()).isEqualTo(60);
            assertThat(i.subscriber()).isTrue();
            assertThat(i.comandaId()).isEqualTo(comandaId);
        });
        assertThat(board.blocks()).singleElement().satisfies(b -> assertThat(b.date()).isEqualTo(day));
        assertThat(board.openingTime()).isEqualTo("09:00");
    }

    @Test
    @DisplayName("grade recusa período invertido ou longo demais")
    void board_rangeGuard() {
        assertThatThrownBy(() -> service.board(day, day.minusDays(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.board(day, day.plusDays(40))).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ apoio

    private Appointment appointment(Long id, String svc, UUID professionalId, LocalDate date, LocalTime time) {
        return Appointment.builder()
                .id(id)
                .barbershopId(shop)
                .customerName("Cliente Teste")
                .customerPhone(PHONE)
                .service(svc)
                .professionalId(professionalId)
                .appointmentDate(date)
                .appointmentTime(time)
                .paymentStatus(PaymentStatus.PENDENTE)
                .build();
    }

    private void assertUntouched(Appointment ag, LocalTime time, UUID professionalId) {
        assertThat(ag.getAppointmentTime()).isEqualTo(time);
        assertThat(ag.getProfessionalId()).isEqualTo(professionalId);
        verify(appointmentRepository, never()).save(any());
        verify(taskService, never()).cancelarTarefasDoAgendamento(any());
        verify(whatsAppService, never()).sendTextMessage(anyString(), anyString());
    }

    /** Proxima ocorrencia do dia da semana, sempre a pelo menos 2 dias de hoje. */
    private static LocalDate nextOpen(DayOfWeek dow) {
        LocalDate d = AppointmentDates.today().plusDays(2);
        while (d.getDayOfWeek() != dow) d = d.plusDays(1);
        return d;
    }

    // ------------------------------------------------------------------ revisao 2026-11-18

    @Test
    @DisplayName("cancelar atendimento já realizado é recusado")
    void cancel_attended_isRefused() {
        Appointment ag = appointment(1L, "Corte", rafael.getId(), AppointmentDates.today().minusDays(1), LocalTime.of(10, 0));
        ag.setAttendanceStatus(com.razor.api.model.AttendanceStatus.ATTENDED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(ag));

        assertThatThrownBy(() -> service.cancel(1L, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("já foi realizado");
        verify(bookingService, never()).cancelarAgendamentoExato(any());
    }

    @Test
    @DisplayName("cancelar agendamento com comanda fechada é recusado")
    void cancel_closedComanda_isRefused() {
        Appointment ag = appointment(1L, "Corte", rafael.getId(), day, LocalTime.of(10, 0));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(ag));
        when(comandaRepository.findByBarbershopIdAndAppointmentIdIn(any(), any())).thenReturn(List.of(
                com.razor.api.model.Comanda.builder().appointmentId(1L).status(com.razor.api.model.Comanda.Status.FECHADA).build()));

        assertThatThrownBy(() -> service.cancel(1L, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("comanda fechada");
        verify(bookingService, never()).cancelarAgendamentoExato(any());
    }

    @Test
    @DisplayName("barbeiro que não faz o serviço é recusado na validação do encaixe")
    void validateSlot_professionalWithoutService_isRefused() {
        UUID corteId = UUID.randomUUID();
        UUID barbaId = UUID.randomUUID();
        when(serviceOfferingRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(
                ServiceOffering.builder().id(corteId).barbershopId(shop).name("Corte").active(true).build(),
                ServiceOffering.builder().id(barbaId).barbershopId(shop).name("Barba").active(true).build()));
        rafael.setServiceIds(new java.util.HashSet<>(java.util.Set.of(corteId)));

        assertThatThrownBy(() -> service.validateSlot(day, LocalTime.of(10, 0), 30, rafael.getId(), null, "Barba"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não faz Barba");
        service.validateSlot(day, LocalTime.of(10, 0), 30, rafael.getId(), null, "Corte"); // faz: passa
    }

    @Test
    @DisplayName("telefone do painel sai no formato canônico (igual ao site)")
    void normalizePhone_canonical() {
        assertThat(AgendaPanelService.normalizePhone("011 98888-7777")).isEqualTo("5511988887777");
        assertThatThrownBy(() -> AgendaPanelService.normalizePhone("(00) 98888-7777"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
