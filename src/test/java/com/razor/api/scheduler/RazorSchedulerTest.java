package com.razor.api.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Barbershop;
import com.razor.api.model.ScheduledTask;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.ScheduledTaskRepository;
import com.razor.api.service.AgendaService;
import com.razor.api.service.CustomerService;
import com.razor.api.service.TenantAdminService;
import com.razor.api.service.WhatsAppService;
import com.razor.api.tenant.TenantContext;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Testes do motor de disparos ({@link RazorScheduler#processScheduledTasks()}) —
 * Fase 1: cada tarefa e processada no contexto do seu proprio tenant.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RazorSchedulerTest {

    private static final String PHONE = "5511999990000";
    private static final ZoneId BRT = ZoneId.of("America/Sao_Paulo");

    @Mock private ScheduledTaskRepository taskRepository;
    @Mock private WhatsAppService whatsAppService;
    @Mock private CustomerService customerService;
    @Mock private CustomerRepository customerRepository;
    @Mock private BarbershopRepository barbershopRepository;
    @Mock private TenantAdminService tenantAdminService;
    @Mock private AgendaService agendaService;
    @Mock private com.razor.api.service.AppointmentConfirmationService confirmationService;

    @Mock private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @InjectMocks private RazorScheduler scheduler;

    /** Transacao de mentira: so executa o callback (o teste olha o status gravado por tarefa). */
    @org.junit.jupiter.api.BeforeEach
    void runCallbacks() {
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
                ((org.springframework.transaction.support.TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ScheduledTask task(UUID shopId, LocalDateTime executionTime) {
        return ScheduledTask.builder()
                .id(UUID.randomUUID())
                .barbershopId(shopId)
                .customerPhone(PHONE)
                .executionTime(executionTime)
                .taskType("REMINDER")
                .messageContent("Lembrete!")
                .status(ScheduledTask.TaskStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("tarefa no horário é enviada e concluída no contexto do tenant da tarefa")
    void dueTask_sentAndCompleted_underTaskTenant() {
        UUID shopId = UUID.randomUUID();
        ScheduledTask t = task(shopId, LocalDateTime.now(BRT).minusMinutes(5));
        when(taskRepository.findByStatusAndExecutionTimeBeforeOrderByExecutionTimeAsc(any(), any(), any())).thenReturn(List.of(t));

        AtomicReference<UUID> tenantDuring = new AtomicReference<>();
        doAnswer(inv -> {
            tenantDuring.set(TenantContext.getCurrent());
            return false;
        }).when(customerService).isCustomerPaused(PHONE);

        scheduler.processScheduledTasks();

        assertThat(tenantDuring.get()).isEqualTo(shopId);
        verify(whatsAppService).sendTextMessageSync(PHONE, "Lembrete!");
        assertThat(t.getStatus()).isEqualTo(ScheduledTask.TaskStatus.COMPLETED);
        verify(taskRepository).save(t);
        assertThat(TenantContext.isSet()).isFalse(); // limpo ao final
    }

    @Test
    @DisplayName("tarefa expirada (>2h no passado) é cancelada sem disparar")
    void expiredTask_cancelledNotSent() {
        ScheduledTask t = task(UUID.randomUUID(), LocalDateTime.now(BRT).minusHours(3));
        when(taskRepository.findByStatusAndExecutionTimeBeforeOrderByExecutionTimeAsc(any(), any(), any())).thenReturn(List.of(t));

        scheduler.processScheduledTasks();

        assertThat(t.getStatus()).isEqualTo(ScheduledTask.TaskStatus.CANCELLED);
        verify(whatsAppService, never()).sendTextMessageSync(any(), any());
    }

    @Test
    @DisplayName("cliente pausado adia a tarefa sem disparar nem concluir")
    void pausedCustomer_taskDeferred() {
        ScheduledTask t = task(UUID.randomUUID(), LocalDateTime.now(BRT).minusMinutes(5));
        when(taskRepository.findByStatusAndExecutionTimeBeforeOrderByExecutionTimeAsc(any(), any(), any())).thenReturn(List.of(t));
        when(customerService.isCustomerPaused(PHONE)).thenReturn(true);

        scheduler.processScheduledTasks();

        verify(whatsAppService, never()).sendTextMessageSync(eq(PHONE), any());
        assertThat(t.getStatus()).isEqualTo(ScheduledTask.TaskStatus.PENDING);
    }

    @Test
    @DisplayName("envio que falha marca a tarefa como FAILED (antes virava COMPLETED sempre)")
    void sendFails_taskMarkedFailed() {
        ScheduledTask t = task(UUID.randomUUID(), LocalDateTime.now(BRT).minusMinutes(5));
        when(taskRepository.findByStatusAndExecutionTimeBeforeOrderByExecutionTimeAsc(any(), any(), any())).thenReturn(List.of(t));
        org.mockito.Mockito.doThrow(new WhatsAppService.WhatsAppSendException("instancia desconectada", new RuntimeException()))
                .when(whatsAppService).sendTextMessageSync(PHONE, "Lembrete!");

        scheduler.processScheduledTasks();

        assertThat(t.getStatus()).isEqualTo(ScheduledTask.TaskStatus.FAILED);
        verify(taskRepository).save(t);
    }

    @Test
    @DisplayName("sextou: erro em um cliente nao interrompe os demais")
    void sextou_failureInOneCustomer_doesNotStopOthers() {
        var c1 = com.razor.api.model.Customer.builder().phoneNumber("5511111111111").name("Ana")
                .barbershopId(UUID.randomUUID()).build();
        var c2 = com.razor.api.model.Customer.builder().phoneNumber("5522222222222").name("Bia")
                .barbershopId(UUID.randomUUID()).build();
        when(customerRepository.findAll()).thenReturn(List.of(c1, c2));
        when(agendaService.hasAgendamentoFuturo("5511111111111")).thenThrow(new RuntimeException("boom"));
        when(agendaService.hasAgendamentoFuturo("5522222222222")).thenReturn(false);

        scheduler.processSextouReminders();

        verify(whatsAppService).sendTextMessageSync(eq("5522222222222"), argThat(m -> m.contains("Bia")));
        assertThat(TenantContext.isSet()).isFalse();
    }

    // ---------- relatórios administrativos por tenant ----------

    @Test
    @DisplayName("relatório matinal roda por tenant ativo, no contexto do tenant, e envia aos admins dele")
    void dailyReport_perActiveTenant() {
        UUID shopId = UUID.randomUUID();
        Barbershop shop = Barbershop.builder().id(shopId).name("Loja X").build();
        when(barbershopRepository.findByActiveTrue()).thenReturn(List.of(shop));
        when(customerRepository.countByBarbershopId(shopId)).thenReturn(7L);
        when(agendaService.getAgendamentosHojeCount()).thenReturn(3);
        when(agendaService.getAgendaHojeText()).thenReturn("agenda de hoje");

        AtomicReference<UUID> tenantDuring = new AtomicReference<>();
        when(tenantAdminService.adminPhones()).thenAnswer(inv -> {
            tenantDuring.set(TenantContext.getCurrent());
            return List.of("5511000000000");
        });

        scheduler.sendDailyAdminReport();

        assertThat(tenantDuring.get()).isEqualTo(shopId); // resolvido no contexto do tenant
        verify(whatsAppService).sendTextMessage(
                eq("5511000000000"),
                argThat(m -> m.contains("Loja X") && m.contains("7")));
        assertThat(TenantContext.isSet()).isFalse(); // limpo ao final
    }

    @Test
    @DisplayName("pergunta de confirmacao (V42) vai pelo servico de confirmacao, nao pelo envio generico")
    void confirmationRequest_delegatesToConfirmationService() {
        ScheduledTask t = task(UUID.randomUUID(), LocalDateTime.now(BRT).minusMinutes(1));
        t.setTaskType(com.razor.api.service.AgendaTaskService.CONFIRMATION_REQUEST);
        t.setAppointmentId(7L);
        when(taskRepository.findByStatusAndExecutionTimeBeforeOrderByExecutionTimeAsc(any(), any(), any())).thenReturn(List.of(t));

        scheduler.processScheduledTasks();

        verify(confirmationService).sendRequest(t);
        verify(whatsAppService, never()).sendTextMessageSync(any(), any());
        assertThat(t.getStatus()).isEqualTo(ScheduledTask.TaskStatus.COMPLETED);
    }

    @Test
    @DisplayName("prazo da confirmacao (V42) e tratado mesmo com o cliente pausado e nao manda o texto da tarefa")
    void confirmationDeadline_handledEvenIfPaused() {
        ScheduledTask t = task(UUID.randomUUID(), LocalDateTime.now(BRT).minusMinutes(1));
        t.setTaskType(com.razor.api.service.AgendaTaskService.CONFIRMATION_DEADLINE);
        t.setAppointmentId(7L);
        when(taskRepository.findByStatusAndExecutionTimeBeforeOrderByExecutionTimeAsc(any(), any(), any())).thenReturn(List.of(t));
        when(customerService.isCustomerPaused(PHONE)).thenReturn(true);

        scheduler.processScheduledTasks();

        verify(confirmationService).handleDeadline(t);
        verify(whatsAppService, never()).sendTextMessageSync(any(), any());
        assertThat(t.getStatus()).isEqualTo(ScheduledTask.TaskStatus.COMPLETED);
    }

    @Test
    @DisplayName("cada tarefa em transacao propria: falha de uma nao impede as outras de concluir")
    void eachTaskInOwnTransaction() {
        ScheduledTask ruim = task(UUID.randomUUID(), LocalDateTime.now(BRT).minusMinutes(5));
        ruim.setCustomerPhone("5511000000000");
        ScheduledTask boa = task(UUID.randomUUID(), LocalDateTime.now(BRT).minusMinutes(4));
        when(taskRepository.findByStatusAndExecutionTimeBeforeOrderByExecutionTimeAsc(any(), any(), any()))
                .thenReturn(List.of(ruim, boa));
        org.mockito.Mockito.doThrow(new RuntimeException("fora")).when(whatsAppService)
                .sendTextMessageSync(eq("5511000000000"), any());

        scheduler.processScheduledTasks();

        assertThat(ruim.getStatus()).isEqualTo(ScheduledTask.TaskStatus.FAILED);
        assertThat(boa.getStatus()).isEqualTo(ScheduledTask.TaskStatus.COMPLETED);
        // processamento das duas + o FAILED da ruim, cada um na sua transacao
        verify(transactionTemplate, org.mockito.Mockito.times(3)).execute(any());
    }

    @Test
    @DisplayName("disparo em massa: teto por rodada, e lembretes saem antes")
    void broadcastCappedPerRun() {
        List<ScheduledTask> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < RazorScheduler.MAX_BROADCASTS_PER_RUN + 5; i++) {
            ScheduledTask b = task(UUID.randomUUID(), LocalDateTime.now(BRT).minusMinutes(30));
            b.setTaskType(RazorScheduler.BROADCAST_TYPE);
            tasks.add(b);
        }
        ScheduledTask lembrete = task(UUID.randomUUID(), LocalDateTime.now(BRT).minusMinutes(1));
        tasks.add(lembrete);
        when(taskRepository.findByStatusAndExecutionTimeBeforeOrderByExecutionTimeAsc(any(), any(), any())).thenReturn(tasks);

        scheduler.processScheduledTasks();

        assertThat(lembrete.getStatus()).isEqualTo(ScheduledTask.TaskStatus.COMPLETED);
        long concluidos = tasks.stream().filter(t -> RazorScheduler.BROADCAST_TYPE.equals(t.getTaskType()))
                .filter(t -> t.getStatus() == ScheduledTask.TaskStatus.COMPLETED).count();
        assertThat(concluidos).isEqualTo(RazorScheduler.MAX_BROADCASTS_PER_RUN);
    }
}
