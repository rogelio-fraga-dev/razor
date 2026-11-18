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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Tarefas agendadas ({@link ScheduledTask}) que nascem da agenda: lembrete 1h antes e pedido de
 * avaliacao 1h depois de cada horario (presas ao agendamento, V39), cancelamento das pendentes
 * daquele horario quando o cliente desmarca ou o painel remarca,
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
                    .appointmentId(ag.getId())
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
                    .appointmentId(ag.getId())
                    .executionTime(reviewTime)
                    .taskType("REVIEW_REQUEST")
                    .messageContent(reviewMsg)
                    .status(ScheduledTask.TaskStatus.PENDING)
                    .build();
            taskRepository.save(reviewTask);
            log.info("[AGENDA] Tarefa de AVALIAÇÃO criada para {} às {}", ag.getCustomerPhone(), reviewTime);

            criarTarefasDeConfirmacao(ag, apptDateTime, pNome);

        } catch (Exception e) {
            log.error("[AGENDA] Erro ao criar tarefas agendadas para {}: {}", ag.getCustomerPhone(), e.getMessage());
        }
    }

    /** Tipo da pergunta "1 confirma / 2 desmarca" da vespera (V42). */
    public static final String CONFIRMATION_REQUEST = "CONFIRMATION_REQUEST";
    /** Prazo da confirmacao: nao envia nada ao cliente; avisa o dono (e libera, se configurado). */
    public static final String CONFIRMATION_DEADLINE = "CONFIRMATION_DEADLINE";

    /** Quando sai a pergunta e quando vence o prazo de resposta de um horario. */
    public record ConfirmationPlan(LocalDateTime askAt, LocalDateTime deadline) {
    }

    /**
     * Plano da confirmacao de um horario, ou vazio quando ela nao se aplica.
     *
     * <p>A pergunta sai na <b>vespera</b>, na hora configurada (padrao 18:00). Horario marcado
     * depois desse momento (ex.: na vespera a noite, ou para hoje mesmo) <b>nao</b> recebe
     * confirmacao: quem acabou de marcar nao precisa ser perguntado se vem, e o lembrete de 1h
     * antes continua saindo. Tambem nao se aplica quando o prazo de resposta cairia antes da
     * propria pergunta (horario de madrugada com pergunta tarde da noite).
     */
    public static java.util.Optional<ConfirmationPlan> planConfirmation(LocalDateTime appointment, LocalDateTime now,
                                                                        int hour, int deadlineHours) {
        LocalDateTime askAt = appointment.toLocalDate().minusDays(1).atTime(hour, 0);
        LocalDateTime deadline = appointment.minusHours(deadlineHours);
        if (!askAt.isAfter(now) || !deadline.isAfter(askAt)) return java.util.Optional.empty();
        return java.util.Optional.of(new ConfirmationPlan(askAt, deadline));
    }

    private void criarTarefasDeConfirmacao(Appointment ag, LocalDateTime apptDateTime, String pNome) {
        BarbershopSettingsService.NoShowSettings cfg = barbershopSettingsService.getNoShowSettings();
        if (cfg == null || !cfg.confirmationEnabled() || ag.getId() == null) return;
        var plan = planConfirmation(apptDateTime, LocalDateTime.now(ZONE), cfg.confirmationHour(), cfg.deadlineHours());
        if (plan.isEmpty()) {
            log.info("[ANTI-FALTA] Agendamento {} marcado depois da hora da confirmacao: sem pergunta", ag.getId());
            return;
        }
        String dia = ag.getAppointmentDate().getDayOfWeek()
                .getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("pt", "BR"));
        String msg = "Oi, " + pNome + "! 💈 Passando pra confirmar: amanhã (" + dia + ", "
                + AppointmentDates.formatDate(ag.getAppointmentDate()) + ") às *"
                + AppointmentDates.formatTime(ag.getAppointmentTime()) + "* você tem *" + ag.getService()
                + "* na " + barbershopSettingsService.resolve().displayName() + ".\n\n"
                + "Responde *1* pra confirmar ou *2* pra desmarcar.";
        taskRepository.save(ScheduledTask.builder().barbershopId(TenantContext.getCurrent())
                .customerPhone(ag.getCustomerPhone())
                .appointmentId(ag.getId())
                .executionTime(plan.get().askAt())
                .taskType(CONFIRMATION_REQUEST)
                .messageContent(msg)
                .status(ScheduledTask.TaskStatus.PENDING)
                .build());
        taskRepository.save(ScheduledTask.builder().barbershopId(TenantContext.getCurrent())
                .customerPhone(ag.getCustomerPhone())
                .appointmentId(ag.getId())
                .executionTime(plan.get().deadline())
                .taskType(CONFIRMATION_DEADLINE)
                .status(ScheduledTask.TaskStatus.PENDING)
                .build());
        log.info("[ANTI-FALTA] Confirmacao do agendamento {} programada para {} (prazo {})",
                ag.getId(), plan.get().askAt(), plan.get().deadline());
    }

    /**
     * Cancela as tarefas PENDING de <b>um</b> agendamento (lembrete e avaliacao) e devolve quantas.
     *
     * <p>Antes o cancelamento era por telefone: desmarcar UM horario apagava os lembretes de todos
     * os outros horarios do mesmo cliente. Agora a tarefa nasce presa ao agendamento (V39).
     * Tarefas criadas antes da V39 nao tem o vinculo; essas sao reconhecidas pelo telefone + o
     * horario exato em que foram programadas (lembrete = 1h antes, avaliacao = 1h depois), que e
     * unico por agendamento.
     *
     * <p>Precisa ser chamado com o agendamento ainda no horario ANTIGO (antes de remarcar), senao
     * o reconhecimento das tarefas legadas olharia o horario novo.
     */
    public int cancelarTarefasDoAgendamento(Appointment ag) {
        if (ag == null) return 0;
        try {
            UUID shop = TenantContext.getCurrent();
            List<ScheduledTask> alvo = new ArrayList<>();
            if (ag.getId() != null) {
                alvo.addAll(taskRepository.findByBarbershopIdAndAppointmentIdAndStatus(
                        shop, ag.getId(), ScheduledTask.TaskStatus.PENDING));
            }
            String phone = ag.getCustomerPhone();
            if (phone != null && ag.getAppointmentDate() != null && ag.getAppointmentTime() != null) {
                LocalDateTime horario = LocalDateTime.of(ag.getAppointmentDate(), ag.getAppointmentTime());
                taskRepository.findByBarbershopIdAndCustomerPhone(shop, phone).stream()
                        .filter(t -> t.getStatus() == ScheduledTask.TaskStatus.PENDING)
                        .filter(t -> t.getAppointmentId() == null)
                        .filter(t -> ("REMINDER".equals(t.getTaskType()) && horario.minusHours(1).equals(t.getExecutionTime()))
                                || ("REVIEW_REQUEST".equals(t.getTaskType()) && horario.plusHours(1).equals(t.getExecutionTime())))
                        .forEach(alvo::add);
            }
            for (ScheduledTask task : alvo) {
                task.setStatus(ScheduledTask.TaskStatus.CANCELLED);
                taskRepository.save(task);
            }
            log.info("[AGENDA] {} tarefa(s) pendente(s) cancelada(s) do agendamento {}", alvo.size(), ag.getId());
            return alvo.size();
        } catch (Exception e) {
            log.error("[AGENDA] Erro ao cancelar tarefas do agendamento {}: {}", ag.getId(), e.getMessage());
            return 0;
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
