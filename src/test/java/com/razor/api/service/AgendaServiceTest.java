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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Testes do {@link AgendaService} (Fase 0.4): logica financeira, fidelidade,
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

    @InjectMocks private AgendaService service;

    /**
     * Config padrao do tenant (expediente 09:00-19:00) para os testes de disponibilidade —
     * os que precisam de textos especificos sobrescrevem o stub localmente.
     */
    @org.junit.jupiter.api.BeforeEach
    void givenDefaultSettings() {
        when(barbershopSettingsService.resolve())
                .thenReturn(new BarbershopSettingsService.BotSettings("", "", "", null, ""));
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
        LocalDate future = AppointmentDates.today().plusDays(5);
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
        LocalDate future = AppointmentDates.today().plusDays(5);
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
        LocalDate future = AppointmentDates.today().plusDays(5);
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
        LocalDate future = AppointmentDates.today().plusDays(5);
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
    @DisplayName("cancelarAgendamentoExato deleta e cancela tarefas pendentes")
    void cancelExato_deletes() {
        Appointment ag = appt("Corte", PaymentStatus.PENDENTE, LocalDate.of(2026, 12, 20), LocalTime.of(10, 0));
        ScheduledTask pending = ScheduledTask.builder().customerPhone(PHONE).status(ScheduledTask.TaskStatus.PENDING).build();
        when(taskRepository.findByBarbershopIdAndCustomerPhone(any(UUID.class), eq(PHONE))).thenReturn(List.of(pending));

        boolean ok = service.cancelarAgendamentoExato(ag);

        assertThat(ok).isTrue();
        verify(appointmentRepository).delete(ag);
        assertThat(pending.getStatus()).isEqualTo(ScheduledTask.TaskStatus.CANCELLED);
        verify(taskRepository).save(pending);
    }

    @Test
    @DisplayName("cancelarAgendamentoCliente sem agendamento retorna false")
    void cancelCliente_none() {
        when(appointmentRepository.findByBarbershopIdAndCustomerPhone(any(UUID.class), eq(PHONE))).thenReturn(List.of());
        assertThat(service.cancelarAgendamentoCliente(PHONE)).isFalse();
        verify(appointmentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("cancelarAgendamentoCliente com agendamento deleta e retorna true")
    void cancelCliente_deletes() {
        Appointment ag = appt("Corte", PaymentStatus.PENDENTE, LocalDate.of(2026, 12, 20), LocalTime.of(10, 0));
        when(appointmentRepository.findByBarbershopIdAndCustomerPhone(any(UUID.class), eq(PHONE))).thenReturn(List.of(ag));
        when(taskRepository.findByBarbershopIdAndCustomerPhone(any(UUID.class), eq(PHONE))).thenReturn(List.of());

        assertThat(service.cancelarAgendamentoCliente(PHONE)).isTrue();
        verify(appointmentRepository).delete(ag);
    }

    // ---------- agendamento via IA + fidelidade ----------

    @Test
    @DisplayName("adicionarAgendamentoViaIA salva o agendamento e registra a visita na fidelidade")
    void adicionar_savesAndRegistersVisit() {
        when(barbershopSettingsService.resolve()).thenReturn(
                new BarbershopSettingsService.BotSettings("", "http://maps", "", null, ""));
        // Sem cartela fechada, nada e enviado ao cliente.
        when(fidelityService.registerVisit(eq(PHONE))).thenReturn(Optional.empty());

        service.adicionarAgendamentoViaIA("João", PHONE, "20/12", "10:00", "Corte");

        verify(appointmentRepository).save(any(Appointment.class));
        verify(fidelityService).registerVisit(eq(PHONE));
        verify(whatsAppService, never()).sendTextMessage(eq(PHONE), contains("PARABÉNS"));
    }

    @Test
    @DisplayName("cartela fechada: envia o cupom emitido ao cliente")
    void adicionar_rewardIssued_sendsCoupon() {
        when(barbershopSettingsService.resolve()).thenReturn(
                new BarbershopSettingsService.BotSettings("", "http://maps", "", null, ""));
        when(fidelityService.registerVisit(eq(PHONE))).thenReturn(Optional.of(
                com.razor.api.model.Coupon.builder()
                        .id(UUID.randomUUID()).code("FIDELIDADE-ABC12")
                        .discountType(com.razor.api.model.Coupon.DiscountType.PERCENT)
                        .discountValue(new java.math.BigDecimal("10"))
                        .validUntil(java.time.LocalDate.now().plusDays(90))
                        .build()));

        service.adicionarAgendamentoViaIA("João", PHONE, "20/12", "10:00", "Corte");

        verify(whatsAppService).sendTextMessage(eq(PHONE), contains("FIDELIDADE-ABC12"));
    }

    @Test
    @DisplayName("falha na fidelidade nao derruba o agendamento ja salvo")
    void adicionar_fidelityFails_appointmentSurvives() {
        when(barbershopSettingsService.resolve()).thenReturn(
                new BarbershopSettingsService.BotSettings("", "http://maps", "", null, ""));
        when(fidelityService.registerVisit(eq(PHONE)))
                .thenThrow(new RuntimeException("banco fora do ar"));

        service.adicionarAgendamentoViaIA("João", PHONE, "20/12", "10:00", "Corte");

        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    @DisplayName("data inválida não cria agendamento (nunca grava lixo)")
    void adicionar_invalidDate_doesNotSave() {
        service.adicionarAgendamentoViaIA("João", PHONE, "quando der", "sei la", "Corte");

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
}
