package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Appointment;
import com.razor.api.model.Customer;
import com.razor.api.model.PaymentStatus;
import com.razor.api.model.ScheduledTask;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.ScheduledTaskRepository;
import com.razor.api.util.AppointmentDates;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Testes do {@link AgendaService} e dos servicos para os quais ele delega
 * ({@link AgendaAvailabilityService}, {@link AgendaBookingService}, {@link AgendaTaskService},
 * {@link AgendaReportService}) (Fase 0.4): logica financeira, fidelidade,
 * disponibilidade de horarios, cancelamento e criacao de tarefas — tudo com o
 * repositorio mockado, sem depender de banco.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendaServiceTest {

    private static final String PHONE = "5511999990000";

    @Mock private CustomerService customerService;
    @Mock private WhatsAppService whatsAppService;
    @Mock private ScheduledTaskRepository taskRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private BarbershopSettingsService barbershopSettingsService;
    @Mock private ProfessionalAssignmentService professionalAssignmentService;
    @Mock private com.razor.api.repository.ProfessionalRepository professionalRepository;
    @Mock private PricingService pricingService;
    @Mock private TenantAdminService tenantAdminService;
    @Mock private ScheduleBlockService scheduleBlockService;
    @Mock private FidelityService fidelityService;
    @Mock private ReportService reportService;

    /**
     * A fachada montada sobre os servicos REAIS da agenda (so repositorios/colaboradores
     * externos sao mocks): estes testes cobrem o comportamento de ponta a ponta e servem de
     * rede de seguranca para a quebra do antigo God class em servicos menores.
     */
    private AgendaService service;
    private AgendaBookingService bookingService;

    @org.junit.jupiter.api.BeforeEach
    void buildService() {
        AgendaTaskService tasks = new AgendaTaskService(
                taskRepository, customerRepository, barbershopSettingsService, tenantAdminService);
        AgendaAvailabilityService availability = new AgendaAvailabilityService(appointmentRepository,
                professionalRepository, pricingService, barbershopSettingsService, scheduleBlockService);
        service = new AgendaService(
                availability,
                bookingService = new AgendaBookingService(appointmentRepository, customerService,
                        professionalAssignmentService, tasks, availability, barbershopSettingsService),
                tasks,
                new AgendaReportService(appointmentRepository, reportService));
        // Sem restricao de servico nos testes daqui: o filtro devolve os livres como vieram.
        org.mockito.Mockito.lenient()
                .when(professionalAssignmentService.qualifiedIds(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * Config padrao do tenant (expediente 09:00-19:00) para os testes de disponibilidade —
     * os que precisam de textos especificos sobrescrevem o stub localmente.
     */
    @org.junit.jupiter.api.BeforeEach
    void givenDefaultSettings() {
        when(barbershopSettingsService.resolve())
                .thenReturn(new BarbershopSettingsService.BotSettings("", "", "", null, ""));
    }

    @Test
    @DisplayName("dia em que a barbearia não abre não oferece horário nenhum (V32)")
    void horariosDisponiveis_closedDay_offersNothing() {
        // O bug que a V32 fecha: até aqui o expediente só tinha hora de abrir e fechar, e nada
        // olhava o DIA. Sem um bloqueio recorrente cadastrado à mão, o bot oferecia e marcava no
        // domingo enquanto dizia ao cliente que abre de segunda a sábado.
        LocalDate domingo = AppointmentDates.today().plusDays(1);
        while (domingo.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) domingo = domingo.plusDays(1);

        when(appointmentRepository.findByBarbershopId(any(UUID.class))).thenReturn(List.of());
        givenDurations();

        String out = service.getHorariosDisponiveisDoDia(AppointmentDates.formatDate(domingo));

        assertThat(out).isEqualTo("Nenhum horário disponível para este dia.");
    }

    @Test
    @DisplayName("tenant que abre domingo continua oferecendo domingo")
    void horariosDisponiveis_tenantOpenOnSunday_offersSunday() {
        LocalDate domingo = AppointmentDates.today().plusDays(1);
        while (domingo.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) domingo = domingo.plusDays(1);

        // O fechamento não é uma regra do sistema, é a configuração do tenant.
        when(barbershopSettingsService.resolve()).thenReturn(
                new BarbershopSettingsService.BotSettings(
                        "", "", "", null, "", "Barbearia", null, null, null,
                        "09:00", "19:00", java.util.EnumSet.allOf(java.time.DayOfWeek.class),
                        null, null, null, null, null, null));
        when(appointmentRepository.findByBarbershopId(any(UUID.class))).thenReturn(List.of());
        givenDurations();

        String out = service.getHorariosDisponiveisDoDia(AppointmentDates.formatDate(domingo));

        assertThat(out).contains("09:00");
    }

    /**
     * Uma data futura em que a barbearia ABRE.
     *
     * <p>Antes era `hoje + 5 dias`, que uma vez por semana caia num domingo. Ate a V32 isso nao
     * importava porque a agenda ignorava o dia da semana; agora domingo nao oferece horario, e o
     * teste quebraria em 1 de cada 7 execucoes — falha intermitente que custa caro para depurar.
     */
    private LocalDate proximoDiaAberto() {
        LocalDate d = AppointmentDates.today().plusDays(5);
        while (!com.razor.api.util.WorkingDays.DEFAULT.contains(d.getDayOfWeek())) {
            d = d.plusDays(1);
        }
        return d;
    }

    private com.razor.api.model.Professional prof() {
        return com.razor.api.model.Professional.builder().id(UUID.randomUUID()).active(true).build();
    }

    private Appointment appt(String service, PaymentStatus status, LocalDate date, LocalTime time) {
        return Appointment.builder()
                .customerName("Cliente")
                .customerPhone(PHONE)
                .service(service)
                .paymentStatus(status)
                .appointmentDate(date)
                .appointmentTime(time)
                .build();
    }

    // ---------- horários ocupados / disponíveis ----------

    @Test
    @DisplayName("agenda vazia informa que está totalmente livre")
    void horariosOcupados_empty() {
        when(appointmentRepository.findByBarbershopId(any(UUID.class))).thenReturn(List.of());
        assertThat(service.getHorariosOcupadosParaIA()).contains("Agenda totalmente livre");
    }

    @Test
    @DisplayName("com 1 cadeira, cada horário ocupado é indisponível (agrupado por data)")
    void horariosOcupados_groupedByDate() {
        LocalDate d1 = LocalDate.of(2026, 12, 20);
        LocalDate d2 = LocalDate.of(2026, 12, 21);
        when(professionalRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(prof())); // capacidade 1
        when(appointmentRepository.findByBarbershopId(any(UUID.class))).thenReturn(List.of(
                appt("Corte", PaymentStatus.PENDENTE, d1, LocalTime.of(10, 0)),
                appt("Corte", PaymentStatus.PENDENTE, d1, LocalTime.of(11, 0)),
                appt("Corte", PaymentStatus.PENDENTE, d2, LocalTime.of(14, 0))));

        String out = service.getHorariosOcupadosParaIA();

        assertThat(out).contains("20/12").contains("10:00").contains("11:00").contains("21/12").contains("14:00");
    }

    @Test
    @DisplayName("multiprofissional: horário só é indisponível quando TODAS as cadeiras estão ocupadas")
    void horariosOcupados_onlyFullWhenAllChairsBusy() {
        LocalDate dia = LocalDate.of(2026, 12, 20);
        when(professionalRepository.findByBarbershopIdAndActiveTrue(any())).thenReturn(List.of(prof(), prof())); // capacidade 2
        when(appointmentRepository.findByBarbershopId(any(UUID.class))).thenReturn(List.of(
                // 10:00 tem 1 agendamento (ainda há cadeira livre) -> disponível
                appt("Corte", PaymentStatus.PENDENTE, dia, LocalTime.of(10, 0)),
                // 11:00 tem 2 agendamentos (ambas as cadeiras) -> indisponível
                appt("Corte", PaymentStatus.PENDENTE, dia, LocalTime.of(11, 0)),
                appt("Barba", PaymentStatus.PENDENTE, dia, LocalTime.of(11, 0))));

        String out = service.getHorariosOcupadosParaIA();

        assertThat(out).contains("11:00").doesNotContain("10:00");
    }

    @Test
    @DisplayName("horários disponíveis excluem os ocupados do dia")
    void horariosDisponiveis_excludesOccupied() {
        LocalDate future = proximoDiaAberto();
        String dateStr = AppointmentDates.formatDate(future);
        when(appointmentRepository.findByBarbershopId(any(UUID.class))).thenReturn(List.of(
                appt("Corte", PaymentStatus.PENDENTE, future, LocalTime.of(9, 0))));
        givenDurations();
        when(pricingService.categorize("Corte")).thenReturn(PricingService.ServiceCategory.SIMPLES);

        String out = service.getHorariosDisponiveisDoDia(dateStr);

        assertThat(out).doesNotContain("09:00").contains("09:30");
    }

    @Test
    @DisplayName("horário de FOLGA/almoço não é oferecido (bloqueio da V16)")
    void horariosDisponiveis_excludesBlocked() {
        LocalDate future = proximoDiaAberto();
        String dateStr = AppointmentDates.formatDate(future);
        when(appointmentRepository.findByBarbershopId(any(UUID.class))).thenReturn(List.of()); // agenda vazia
        givenDurations();
        // almoço: 12:00 e 12:30 bloqueados
        when(scheduleBlockService.isBlocked(eq(future), eq(LocalTime.of(12, 0)), anyInt(), any()))
                .thenReturn(true);
        when(scheduleBlockService.isBlocked(eq(future), eq(LocalTime.of(12, 30)), anyInt(), any()))
                .thenReturn(true);

        String out = service.getHorariosDisponiveisDoDia(dateStr);

        assertThat(out).doesNotContain("12:00").doesNotContain("12:30");
    }

    @Test
    @DisplayName("dia inteiro bloqueado (feriado) não oferece nenhum horário")
    void horariosDisponiveis_fullDayBlocked() {
        LocalDate future = AppointmentDates.today().plusDays(6);
        String dateStr = AppointmentDates.formatDate(future);
        when(appointmentRepository.findByBarbershopId(any(UUID.class))).thenReturn(List.of());
        givenDurations();
        when(scheduleBlockService.isBlocked(eq(future), any(), anyInt(), any())).thenReturn(true);

        assertThat(service.getHorariosDisponiveisDoDia(dateStr))
                .isEqualTo("Nenhum horário disponível para este dia.");
    }

    /** Duracoes padrao (combo=60, barba=30, corte=30) para os testes de disponibilidade. */
    private void givenDurations() {
        when(pricingService.resolveDurations()).thenReturn(java.util.Map.of(
                PricingService.ServiceCategory.COMBO, 60,
                PricingService.ServiceCategory.BARBA, 30,
                PricingService.ServiceCategory.SIMPLES, 30));
    }

    @Test
    @DisplayName("serviço de 60min (combo) ocupa dois slots consecutivos na disponibilidade")
    void horariosDisponiveis_comboBlocksTwoSlots() {
        LocalDate future = proximoDiaAberto();
        String dateStr = AppointmentDates.formatDate(future);
        when(appointmentRepository.findByBarbershopId(any(UUID.class))).thenReturn(List.of(
                appt("Combo", PaymentStatus.PENDENTE, future, LocalTime.of(9, 0))));
        givenDurations();
        when(pricingService.categorize("Combo")).thenReturn(PricingService.ServiceCategory.COMBO);

        String out = service.getHorariosDisponiveisDoDia(dateStr);

        // 09:00 (início) e 09:30 (2º slot do combo) ficam ocupados; 10:00 volta a ficar livre.
        assertThat(out).doesNotContain("09:00").doesNotContain("09:30").contains("10:00");
    }

    @Test
    @DisplayName("disponibilidade por profissional considera só os agendamentos daquele barbeiro")
    void horariosDisponiveisProfissional_excludesThatProfessionalOccupied() {
        UUID prof = UUID.randomUUID();
        LocalDate future = proximoDiaAberto();
        String dateStr = AppointmentDates.formatDate(future);
        when(appointmentRepository.findByBarbershopIdAndProfessionalIdAndAppointmentDate(
                any(UUID.class), eq(prof), any(LocalDate.class)))
                .thenReturn(List.of(appt("Corte", PaymentStatus.PENDENTE, future, LocalTime.of(9, 0))));
        givenDurations();
        when(pricingService.categorize("Corte")).thenReturn(PricingService.ServiceCategory.SIMPLES);

        String out = service.getHorariosDisponiveisDoProfissional(prof, dateStr);

        assertThat(out).doesNotContain("09:00").contains("09:30");
    }

    // ---------- cancelamento ----------

    @Test
    @DisplayName("cancelarAgendamentoExato com null retorna false e não deleta")
    void cancelExato_null() {
        assertThat(service.cancelarAgendamentoExato(null)).isFalse();
        verify(appointmentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("cancelarAgendamentoExato deleta e cancela as tarefas pendentes DAQUELE agendamento")
    void cancelExato_deletes() {
        Appointment ag = appt("Corte", PaymentStatus.PENDENTE, LocalDate.of(2026, 12, 20), LocalTime.of(10, 0));
        ag.setId(7L);
        ScheduledTask pending = task(7L, "REMINDER", LocalDate.of(2026, 12, 20).atTime(9, 0));
        when(taskRepository.findByBarbershopIdAndAppointmentIdAndStatus(any(UUID.class), eq(7L),
                eq(ScheduledTask.TaskStatus.PENDING))).thenReturn(List.of(pending));
        when(taskRepository.findByBarbershopIdAndCustomerPhone(any(UUID.class), eq(PHONE))).thenReturn(List.of(pending));

        boolean ok = service.cancelarAgendamentoExato(ag);

        assertThat(ok).isTrue();
        verify(appointmentRepository).delete(ag);
        assertThat(pending.getStatus()).isEqualTo(ScheduledTask.TaskStatus.CANCELLED);
        verify(taskRepository).save(pending);
    }

    private void givenUpcoming(Appointment... apps) {
        when(appointmentRepository.findUpcomingByCustomer(any(UUID.class), eq(PHONE), any(LocalDate.class), any(LocalTime.class)))
                .thenReturn(List.of(apps));
    }

    @Test
    @DisplayName("cancelar UM agendamento não cancela os lembretes dos OUTROS horários do cliente")
    void cancelExato_keepsTasksOfOtherAppointments() {
        // O bug: a única chave da tarefa era o telefone. Quem tinha corte na terça e na sexta e
        // desmarcava a terça deixava de receber o lembrete da sexta.
        Appointment terca = appt("Corte", PaymentStatus.PENDENTE, LocalDate.of(2026, 12, 15), LocalTime.of(10, 0));
        terca.setId(1L);
        ScheduledTask lembreteTerca = task(1L, "REMINDER", LocalDate.of(2026, 12, 15).atTime(9, 0));
        ScheduledTask avaliacaoTerca = task(1L, "REVIEW_REQUEST", LocalDate.of(2026, 12, 15).atTime(11, 0));
        ScheduledTask lembreteSexta = task(2L, "REMINDER", LocalDate.of(2026, 12, 18).atTime(9, 0));
        ScheduledTask disparo = task(null, "BROADCAST_SCHEDULED", LocalDate.of(2026, 12, 16).atTime(8, 0));
        when(taskRepository.findByBarbershopIdAndAppointmentIdAndStatus(any(UUID.class), eq(1L),
                eq(ScheduledTask.TaskStatus.PENDING))).thenReturn(List.of(lembreteTerca, avaliacaoTerca));
        when(taskRepository.findByBarbershopIdAndCustomerPhone(any(UUID.class), eq(PHONE)))
                .thenReturn(List.of(lembreteTerca, avaliacaoTerca, lembreteSexta, disparo));

        assertThat(service.cancelarAgendamentoExato(terca)).isTrue();

        assertThat(lembreteTerca.getStatus()).isEqualTo(ScheduledTask.TaskStatus.CANCELLED);
        assertThat(avaliacaoTerca.getStatus()).isEqualTo(ScheduledTask.TaskStatus.CANCELLED);
        assertThat(lembreteSexta.getStatus()).isEqualTo(ScheduledTask.TaskStatus.PENDING);
        assertThat(disparo.getStatus()).isEqualTo(ScheduledTask.TaskStatus.PENDING);
        verify(taskRepository, never()).save(lembreteSexta);
        verify(taskRepository, never()).save(disparo);
    }

    @Test
    @DisplayName("tarefa antiga (sem vínculo, pré-V39) é reconhecida pelo horário exato do agendamento")
    void cancelExato_legacyTasksMatchedByExactTime() {
        Appointment terca = appt("Corte", PaymentStatus.PENDENTE, LocalDate.of(2026, 12, 15), LocalTime.of(10, 0));
        terca.setId(1L);
        ScheduledTask lembreteLegado = task(null, "REMINDER", LocalDate.of(2026, 12, 15).atTime(9, 0));
        ScheduledTask avaliacaoLegada = task(null, "REVIEW_REQUEST", LocalDate.of(2026, 12, 15).atTime(11, 0));
        ScheduledTask lembreteOutroDia = task(null, "REMINDER", LocalDate.of(2026, 12, 18).atTime(9, 0));
        when(taskRepository.findByBarbershopIdAndAppointmentIdAndStatus(any(UUID.class), eq(1L),
                eq(ScheduledTask.TaskStatus.PENDING))).thenReturn(List.of());
        when(taskRepository.findByBarbershopIdAndCustomerPhone(any(UUID.class), eq(PHONE)))
                .thenReturn(List.of(lembreteLegado, avaliacaoLegada, lembreteOutroDia));

        service.cancelarAgendamentoExato(terca);

        assertThat(lembreteLegado.getStatus()).isEqualTo(ScheduledTask.TaskStatus.CANCELLED);
        assertThat(avaliacaoLegada.getStatus()).isEqualTo(ScheduledTask.TaskStatus.CANCELLED);
        assertThat(lembreteOutroDia.getStatus()).isEqualTo(ScheduledTask.TaskStatus.PENDING);
    }

    @Test
    @DisplayName("tarefas novas nascem presas ao agendamento (V39)")
    void criarTarefas_linksAppointment() {
        when(barbershopSettingsService.resolve()).thenReturn(
                new BarbershopSettingsService.BotSettings("", "http://maps", "", null, ""));
        Appointment ag = appt("Corte", PaymentStatus.PENDENTE, AppointmentDates.today().plusDays(3), LocalTime.of(15, 0));
        ag.setId(42L);

        new AgendaTaskService(taskRepository, customerRepository, barbershopSettingsService, tenantAdminService)
                .criarTarefasAgendadas(ag);

        ArgumentCaptor<ScheduledTask> captor = ArgumentCaptor.forClass(ScheduledTask.class);
        verify(taskRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(t -> assertThat(t.getAppointmentId()).isEqualTo(42L));
    }

    private ScheduledTask task(Long appointmentId, String type, java.time.LocalDateTime when) {
        return ScheduledTask.builder()
                .customerPhone(PHONE)
                .appointmentId(appointmentId)
                .taskType(type)
                .executionTime(when)
                .status(ScheduledTask.TaskStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("cancelarAgendamentoMaisRecente sem horario futuro retorna false")
    void cancelMaisRecente_none() {
        givenUpcoming();
        assertThat(service.cancelarAgendamentoMaisRecente(PHONE)).isFalse();
        verify(appointmentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("cancelarAgendamentoMaisRecente apaga o horario futuro criado por ultimo, nao o primeiro achado")
    void cancelMaisRecente_deletesLastCreated() {
        Appointment antigo = appt("Corte", PaymentStatus.PENDENTE, LocalDate.of(2026, 12, 20), LocalTime.of(10, 0));
        antigo.setCreatedAt(java.time.LocalDateTime.of(2026, 11, 1, 9, 0));
        Appointment recente = appt("Barba", PaymentStatus.PENDENTE, LocalDate.of(2026, 12, 18), LocalTime.of(15, 0));
        recente.setCreatedAt(java.time.LocalDateTime.of(2026, 11, 10, 9, 0));
        givenUpcoming(recente, antigo); // ordenado por data, como o banco devolve
        when(taskRepository.findByBarbershopIdAndCustomerPhone(any(UUID.class), eq(PHONE))).thenReturn(List.of());

        assertThat(service.cancelarAgendamentoMaisRecente(PHONE)).isTrue();
        verify(appointmentRepository).delete(recente);
        verify(appointmentRepository, never()).delete(antigo);
    }

    @Test
    @DisplayName("agendamentos do cliente = so os futuros: o historico nunca e consultado")
    void agendamentosFuturos_useUpcomingQuery() {
        Appointment futuro = appt("Corte", PaymentStatus.PENDENTE, LocalDate.of(2026, 12, 20), LocalTime.of(10, 0));
        givenUpcoming(futuro);

        assertThat(service.getAgendamentosFuturos(PHONE)).containsExactly(futuro);
        verify(appointmentRepository, never()).findByBarbershopIdAndCustomerPhone(any(), any());
    }

    // ---------- limite de agendamentos por cliente (V36) ----------

    @Test
    @DisplayName("limite: abaixo do maximo libera o agendamento")
    void limit_belowMax_allows() {
        when(barbershopSettingsService.getMaxFutureBookingsPerCustomer(any(UUID.class))).thenReturn(2);
        givenUpcoming(appt("Corte", PaymentStatus.PENDENTE, LocalDate.of(2026, 12, 20), LocalTime.of(10, 0)));

        assertThat(service.checkBookingLimit(PHONE)).isEmpty();
    }

    @Test
    @DisplayName("limite: no maximo recusa e devolve os horarios que o cliente ja tem")
    void limit_atMax_refusesWithUpcoming() {
        Appointment a = appt("Corte", PaymentStatus.PENDENTE, LocalDate.of(2026, 12, 20), LocalTime.of(10, 0));
        Appointment b = appt("Barba", PaymentStatus.PENDENTE, LocalDate.of(2026, 12, 27), LocalTime.of(11, 0));
        when(barbershopSettingsService.getMaxFutureBookingsPerCustomer(any(UUID.class))).thenReturn(2);
        givenUpcoming(a, b);

        var out = service.checkBookingLimit(PHONE);

        assertThat(out).isPresent();
        assertThat(out.get().limit()).isEqualTo(2);
        assertThat(out.get().upcoming()).containsExactly(a, b);
        String msg = out.get().customerMessage("João");
        assertThat(msg).contains("João").contains("2 horários").contains("20/12").contains("27/12")
                .contains("cancelar meu horário");
    }

    @Test
    @DisplayName("limite nulo = sem limite, nem consulta a agenda")
    void limit_null_isUnlimited() {
        when(barbershopSettingsService.getMaxFutureBookingsPerCustomer(any(UUID.class))).thenReturn(null);

        assertThat(service.checkBookingLimit(PHONE)).isEmpty();
        verify(appointmentRepository, never()).findUpcomingByCustomer(any(), any(), any(), any());
    }

    @Test
    @DisplayName("adicionarAgendamentoViaIA no limite lanca e NAO salva (trava para quem nao conferiu)")
    void adicionar_atLimit_throwsAndDoesNotSave() {
        when(barbershopSettingsService.getMaxFutureBookingsPerCustomer(any(UUID.class))).thenReturn(1);
        givenUpcoming(appt("Corte", PaymentStatus.PENDENTE, LocalDate.of(2026, 12, 20), LocalTime.of(10, 0)));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.adicionarAgendamentoViaIA("João", PHONE, "21/12", "10:00", "Corte"))
                .isInstanceOf(AgendaBookingService.BookingLimitExceededException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("limite com barbearia explicita (agendamento online, sem tenant no contexto)")
    void limit_explicitBarbershop() {
        UUID outra = UUID.randomUUID();
        when(barbershopSettingsService.getMaxFutureBookingsPerCustomer(outra)).thenReturn(1);
        when(appointmentRepository.findUpcomingByCustomer(eq(outra), eq(PHONE), any(LocalDate.class), any(LocalTime.class)))
                .thenReturn(List.of(appt("Corte", PaymentStatus.PENDENTE, LocalDate.of(2026, 12, 20), LocalTime.of(10, 0))));

        assertThat(bookingService.checkBookingLimit(outra, PHONE)).isPresent();
    }

    // ---------- agendamento via IA ----------

    @Test
    @DisplayName("adicionarAgendamentoViaIA salva e devolve o agendamento, SEM dar ponto de fidelidade")
    void adicionar_savesAndDoesNotTouchFidelity() {
        var salvo = service.adicionarAgendamentoViaIA("João", PHONE, "20/12", "10:00", "Corte");

        assertThat(salvo).isPresent();
        verify(appointmentRepository).save(any(Appointment.class));
        // Agendar nao e ser atendido: o ponto (e o lastServiceDate) so vem no fechamento da comanda.
        verify(fidelityService, never()).registerVisit(any());
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("data inválida não cria agendamento (nunca grava lixo)")
    void adicionar_invalidDate_doesNotSave() {
        assertThat(service.adicionarAgendamentoViaIA("João", PHONE, "quando der", "sei la", "Corte")).isEmpty();

        verify(appointmentRepository, never()).save(any());
        verify(whatsAppService, never()).sendTextMessage(any(), any());
    }

    // ---------- resumo financeiro ----------

    @Test
    @DisplayName("resumo financeiro do dia formata o que o ReportService apurou")
    void resumoFinanceiro_formatsReportServiceResult() {
        LocalDate hoje = AppointmentDates.today();
        // O calculo vive no ReportService (mesma fonte do painel). Aqui so se verifica que o
        // texto do WhatsApp reflete fielmente o que foi apurado.
        when(reportService.financialSummary(eq(hoje), eq(hoje))).thenReturn(
                new com.razor.api.dto.FinancialSummaryResponse(
                        hoje, hoje, 3,
                        new com.razor.api.dto.FinancialSummaryResponse.CategoryBreakdown(1, 1, 1),
                        new com.razor.api.dto.FinancialSummaryResponse.PaymentBreakdown(
                                new com.razor.api.dto.FinancialSummaryResponse.Bucket(1, new java.math.BigDecimal("60.00")),
                                new com.razor.api.dto.FinancialSummaryResponse.Bucket(1, new java.math.BigDecimal("30.00")),
                                new com.razor.api.dto.FinancialSummaryResponse.Bucket(1, new java.math.BigDecimal("35.00"))),
                        new java.math.BigDecimal("125.00"), 0, java.math.BigDecimal.ZERO,
                        new java.math.BigDecimal("125.00"), new java.math.BigDecimal("41.67")));

        String out = service.getResumoFinanceiroDetalhado();

        assertThat(out)
                .contains("Total Atendidos: *3*")
                .contains("Combos (Cabelo+Barba): 1")
                .contains("Apenas Barba: 1")
                .contains("Simples / Outros: 1")
                // Moeda sempre em pt-BR, independente do locale da JVM.
                .contains("125,00")
                .contains("41,67");
    }

    // ---------- tarefas agendadas ----------

    @Test
    @DisplayName("agendarNotificacaoRetorno cria tarefa UNPAUSE_NOTIFY pendente")
    void agendarNotificacaoRetorno_createsTask() {
        service.agendarNotificacaoRetorno(PHONE, 15);

        ArgumentCaptor<ScheduledTask> cap = ArgumentCaptor.forClass(ScheduledTask.class);
        verify(taskRepository).save(cap.capture());
        ScheduledTask t = cap.getValue();
        assertThat(t.getTaskType()).isEqualTo("UNPAUSE_NOTIFY");
        assertThat(t.getCustomerPhone()).isEqualTo(PHONE);
        assertThat(t.getStatus()).isEqualTo(ScheduledTask.TaskStatus.PENDING);
    }

    @Test
    @DisplayName("agendarDisparoMassa agenda para clientes e pula o admin")
    void agendarDisparoMassa_skipsAdmin() {
        String adminPhone = "5511000000000";
        Customer admin = Customer.builder().id(UUID.randomUUID()).phoneNumber(adminPhone).name("Chefe").build();
        Customer cliente = Customer.builder().id(UUID.randomUUID()).phoneNumber(PHONE).name("Cliente").build();
        when(customerRepository.findByBarbershopId(any(UUID.class))).thenReturn(List.of(admin, cliente));
        when(tenantAdminService.isAdmin(adminPhone)).thenReturn(true);
        when(tenantAdminService.isAdmin(PHONE)).thenReturn(false);

        service.agendarDisparoMassa("Promo!", java.time.LocalDateTime.now().plusDays(1));

        ArgumentCaptor<ScheduledTask> cap = ArgumentCaptor.forClass(ScheduledTask.class);
        verify(taskRepository, times(1)).save(cap.capture());
        assertThat(cap.getValue().getCustomerPhone()).isEqualTo(PHONE);
        assertThat(cap.getValue().getTaskType()).isEqualTo("BROADCAST_SCHEDULED");
    }

    // ---------- processamento de agenda (imagem -> JSON) ----------

    @Test
    @DisplayName("processAgenda salva itens e conta apenas os clientes conhecidos")
    void processAgenda_countsKnownCustomers() {
        when(barbershopSettingsService.resolve()).thenReturn(
                new BarbershopSettingsService.BotSettings("", "http://maps", "", null, ""));
        Customer ana = Customer.builder().id(UUID.randomUUID()).phoneNumber(PHONE).name("Ana").build();
        when(customerService.findByName("Ana")).thenReturn(ana);
        when(customerService.findByName("Bob")).thenReturn(null);

        String json = "{\"items\":[" +
                "{\"name\":\"Ana\",\"time\":\"10:00\",\"date\":\"20/12\",\"service\":\"Corte\"}," +
                "{\"name\":\"Bob\",\"time\":\"11:00\",\"date\":\"20/12\",\"service\":\"Barba\"}]}";

        int count = service.processAgenda(json);

        assertThat(count).isEqualTo(1);
        verify(appointmentRepository, times(2)).save(any(Appointment.class));
    }

    @Test
    @DisplayName("processAgenda com JSON invalido LANCA (antes devolvia 0 e o admin lia '✅ 0 clientes')")
    void processAgenda_invalidJson_throws() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.processAgenda("isto nao e json {"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("processAgenda propaga falha de banco no meio do lote")
    void processAgenda_saveFails_throws() {
        when(customerService.findByName(any())).thenReturn(null);
        when(appointmentRepository.save(any(Appointment.class))).thenThrow(new RuntimeException("db fora"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.processAgenda(
                "{\"items\":[{\"name\":\"Ana\",\"time\":\"10:00\",\"date\":\"20/12\"}]}"))
                .hasMessageContaining("db fora");
    }
}
