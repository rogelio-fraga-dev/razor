package com.razor.api.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.ScheduledTask;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.ScheduledTaskRepository;
import com.razor.api.service.AgendaService;
import com.razor.api.service.CustomerService;
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
    @Mock private RazorProperties properties;
    @Mock private AgendaService agendaService;

    @InjectMocks private RazorScheduler scheduler;

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
        when(taskRepository.findByStatusAndExecutionTimeBefore(any(), any())).thenReturn(List.of(t));

        AtomicReference<UUID> tenantDuring = new AtomicReference<>();
        doAnswer(inv -> {
            tenantDuring.set(TenantContext.getCurrent());
            return false;
        }).when(customerService).isCustomerPaused(PHONE);

        scheduler.processScheduledTasks();

        assertThat(tenantDuring.get()).isEqualTo(shopId);
        verify(whatsAppService).sendTextMessage(PHONE, "Lembrete!");
        assertThat(t.getStatus()).isEqualTo(ScheduledTask.TaskStatus.COMPLETED);
        verify(taskRepository).save(t);
        assertThat(TenantContext.isSet()).isFalse(); // limpo ao final
    }

    @Test
    @DisplayName("tarefa expirada (>2h no passado) é cancelada sem disparar")
    void expiredTask_cancelledNotSent() {
        ScheduledTask t = task(UUID.randomUUID(), LocalDateTime.now(BRT).minusHours(3));
        when(taskRepository.findByStatusAndExecutionTimeBefore(any(), any())).thenReturn(List.of(t));

        scheduler.processScheduledTasks();

        assertThat(t.getStatus()).isEqualTo(ScheduledTask.TaskStatus.CANCELLED);
        verify(whatsAppService, never()).sendTextMessage(any(), any());
    }

    @Test
    @DisplayName("cliente pausado adia a tarefa sem disparar nem concluir")
    void pausedCustomer_taskDeferred() {
        ScheduledTask t = task(UUID.randomUUID(), LocalDateTime.now(BRT).minusMinutes(5));
        when(taskRepository.findByStatusAndExecutionTimeBefore(any(), any())).thenReturn(List.of(t));
        when(customerService.isCustomerPaused(PHONE)).thenReturn(true);

        scheduler.processScheduledTasks();

        verify(whatsAppService, never()).sendTextMessage(eq(PHONE), any());
        assertThat(t.getStatus()).isEqualTo(ScheduledTask.TaskStatus.PENDING);
    }
}
