package com.razor.api.service;

import com.razor.api.model.Appointment;
import com.razor.api.model.Customer;
import com.razor.api.model.ScheduledTask;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.ScheduledTaskRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Tarefas agendadas ({@link ScheduledTask}) que nascem da agenda: lembrete 1h antes e pedido de
 * avaliacao 1h depois de cada horario, cancelamento das pendentes quando o cliente desmarca,
 * disparo em massa programado pelo dono e o aviso de "bot voltou" apos uma pausa humana.
 *
 * <p>So cria/cancela linhas em {@code scheduled_tasks}; quem envia e o {@code RazorScheduler}.
 * Extraido do antigo {@link AgendaService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaTaskService {

    private static final ZoneId ZONE = AppointmentDates.ZONE;

    private final ScheduledTaskRepository taskRepository;
    private final CustomerRepository customerRepository;
    private final BarbershopSettingsService barbershopSettingsService;
    private final TenantAdminService tenantAdminService;

    /** Cria o LEMBRETE (1h antes) e a AVALIACAO (1h depois) de um agendamento futuro com telefone. */
    public void criarTarefasAgendadas(Appointment ag) {
        if (ag.getCustomerPhone() == null || ag.getCustomerPhone().trim().isEmpty() || ag.getCustomerPhone().equals("N/A")) return;
        if (ag.getAppointmentDate() == null || ag.getAppointmentTime() == null) return;

        try {
            LocalDateTime apptDateTime = LocalDateTime.of(ag.getAppointmentDate(), ag.getAppointmentTime());

            // Se já passou, não agenda lembrete
            if (apptDateTime.isBefore(LocalDateTime.now(ZONE))) {
                return;
            }

            String pNome = ag.getCustomerName().split(" ")[0];

            // 1. Tarefa de LEMBRETE (1 hora antes do horário)
            LocalDateTime reminderTime = apptDateTime.minusHours(1);
            // O nome vem do tenant: cravado, toda barbearia mandaria lembrete com o nome de outra.
            String reminderMsg = "Olá, " + pNome + "! 💈\nLembramos que você tem um agendamento hoje às *"
                    + AppointmentDates.formatTime(ag.getAppointmentTime()) + "* para *" + ag.getService()
                    + "* na " + barbershopSettingsService.resolve().displayName() + ".";

            ScheduledTask reminderTask = ScheduledTask.builder().barbershopId(TenantContext.getCurrent())
                    .customerPhone(ag.getCustomerPhone())
                    .executionTime(reminderTime)
                    .taskType("REMINDER")
                    .messageContent(reminderMsg)
                    .status(ScheduledTask.TaskStatus.PENDING)
                    .build();
            taskRepository.save(reminderTask);
            log.info("[AGENDA] Tarefa de LEMBRETE criada para {} às {}", ag.getCustomerPhone(), reminderTime);

            // 2. Tarefa de AVALIAÇÃO (1 hora depois do horário)
            LocalDateTime reviewTime = apptDateTime.plusHours(1);
            String reviewMsg = "E aí, " + pNome + ", curtiu o talento? 🧔‍♂️\nSua avaliação ajuda a barbearia a crescer! Leva só 1 minuto:\n⭐ " + barbershopSettingsService.resolve().addressMapsUrl() + "\n\nValeu demais! 👊";

            ScheduledTask reviewTask = ScheduledTask.builder().barbershopId(TenantContext.getCurrent())
                    .customerPhone(ag.getCustomerPhone())
                    .executionTime(reviewTime)
                    .taskType("REVIEW_REQUEST")
                    .messageContent(reviewMsg)
                    .status(ScheduledTask.TaskStatus.PENDING)
                    .build();
            taskRepository.save(reviewTask);
            log.info("[AGENDA] Tarefa de AVALIAÇÃO criada para {} às {}", ag.getCustomerPhone(), reviewTime);

        } catch (Exception e) {
            log.error("[AGENDA] Erro ao criar tarefas agendadas para {}: {}", ag.getCustomerPhone(), e.getMessage());
        }
    }

    /** Cancela todas as tarefas PENDING do telefone (inclusive expiradas), registrando a contagem. */
    public void cancelarTarefasBanco(String phone) {
        try {
            int canceladas = cancelarPendentes(phone);
            log.info("[AGENDA] Canceladas {} tarefas pendentes (incluindo expiradas) para o telefone {}", canceladas, phone);
        } catch (Exception e) {
            log.error("[AGENDA] Erro ao cancelar tarefas do banco para {}: {}", phone, e.getMessage());
        }
    }

    /** Mesmo efeito de {@link #cancelarTarefasBanco}, sem o log de contagem (fluxo do cancelamento exato). */
    public void cancelarTarefasClienteExato(String phone) {
        try {
            cancelarPendentes(phone);
        } catch (Exception e) {
            log.error("[AGENDA] Erro ao cancelar tarefas do banco para {}: {}", phone, e.getMessage());
        }
    }

    private int cancelarPendentes(String phone) {
        List<ScheduledTask> tasks = taskRepository.findByBarbershopIdAndCustomerPhone(TenantContext.getCurrent(), phone);
        List<ScheduledTask> pendingToCancel = tasks.stream()
                .filter(t -> t.getStatus() == ScheduledTask.TaskStatus.PENDING)
                .toList();

        for (ScheduledTask task : pendingToCancel) {
            task.setStatus(ScheduledTask.TaskStatus.CANCELLED);
            taskRepository.save(task);
        }
        return pendingToCancel.size();
    }

    /** Agenda uma mensagem para todos os clientes do tenant (exceto admins) no horario dado. */
    public void agendarDisparoMassa(String msg, LocalDateTime execTime) {
        List<Customer> customers = customerRepository.findByBarbershopId(TenantContext.getCurrent());
        for (Customer c : customers) {
            if (tenantAdminService.isAdmin(c.getPhoneNumber())) continue;
            ScheduledTask task = ScheduledTask.builder().barbershopId(TenantContext.getCurrent())
                    .customerPhone(c.getPhoneNumber())
                    .executionTime(execTime)
                    .taskType("BROADCAST_SCHEDULED")
                    .messageContent(msg)
                    .status(ScheduledTask.TaskStatus.PENDING)
                    .build();
            taskRepository.save(task);
        }
        log.info("[AGENDA] Disparo em massa agendado para {} contatos em {}", customers.size() - 1, execTime);
    }

    /** Avisa o cliente, apos {@code minutesDelay}, que o atendimento automatico voltou. */
    public void agendarNotificacaoRetorno(String phone, int minutesDelay) {
        ScheduledTask task = ScheduledTask.builder().barbershopId(TenantContext.getCurrent())
                .customerPhone(phone)
                .taskType("UNPAUSE_NOTIFY")
                .executionTime(LocalDateTime.now(ZONE).plusMinutes(minutesDelay))
                .messageContent("Voltamos! 🤖\n\nMeu atendimento automático foi retomado. Se precisar de algo, é só chamar!")
                .status(ScheduledTask.TaskStatus.PENDING)
                .build();
        taskRepository.save(task);
        log.info("[AGENDA] Notificação de retorno agendada para {} em {} minutos", phone, minutesDelay);
    }
}
