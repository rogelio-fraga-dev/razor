package com.razor.api.scheduler;

import com.razor.api.model.Barbershop;
import com.razor.api.model.ScheduledTask;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.ScheduledTaskRepository;
import com.razor.api.service.CustomerService;
import com.razor.api.service.TenantAdminService;
import com.razor.api.service.WhatsAppService;
import com.razor.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class RazorScheduler {

    private final ScheduledTaskRepository taskRepository;
    private final WhatsAppService whatsAppService;
    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final BarbershopRepository barbershopRepository;
    private final TenantAdminService tenantAdminService;
    private final com.razor.api.service.AgendaService agendaService;
    private final com.razor.api.service.BarbershopSettingsService barbershopSettingsService;
    private final com.razor.api.service.AppointmentConfirmationService confirmationService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    /** Batimento do motor para o monitor de saude (V50). Nulo so em teste unitario. */
    private final com.razor.api.service.HealthSignals healthSignals;

    /**
     * Executa a acao para cada barbearia ativa com o {@link TenantContext} escopado no tenant e
     * limpo ao final — base dos relatorios administrativos por tenant. Um erro em um tenant e
     * logado e nao interrompe os demais.
     */
    private void forEachActiveTenant(Consumer<Barbershop> action) {
        for (Barbershop shop : barbershopRepository.findByActiveTrue()) {
            try {
                TenantContext.set(shop.getId());
                action.accept(shop);
            } catch (Exception e) {
                log.error("[SCHEDULER] Erro ao processar o tenant {}: {}", shop.getId(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    /** Teto de tarefas lidas por rodada: um disparo de 5 mil contatos nao vira uma rodada de horas. */
    static final int MAX_TASKS_PER_RUN = 200;
    /** Teto de mensagens de disparo em massa por rodada — elas esperam a pausa anti-bloqueio. */
    static final int MAX_BROADCASTS_PER_RUN = 20;
    static final String BROADCAST_TYPE = "BROADCAST_SCHEDULED";

    /** Pausa entre mensagens de disparo em massa (a mesma do BroadcastService). */
    @org.springframework.beans.factory.annotation.Value("${razor.broadcast.delay-ms:3000}")
    private long broadcastDelayMillis;

    /**
     * MOTOR DE DISPAROS (Roda a cada 60 segundos)
     * Verifica o banco e envia as mensagens na hora certa.
     *
     * <p><b>Uma transacao por tarefa.</b> Antes o metodo inteiro era {@code @Transactional}: uma
     * conexao presa durante todos os envios sincronos (3s, 10s cada), e um erro de banco no meio
     * desfazia o COMPLETED das tarefas ja enviadas — que saiam de novo na rodada seguinte. Agora
     * cada tarefa grava o proprio status ao terminar, e a falha de uma nao toca nas outras.
     *
     * <p>Por rodada: no maximo {@link #MAX_TASKS_PER_RUN} tarefas vencidas (as mais antigas
     * primeiro); lembretes e avisos saem antes, e o disparo em massa vai no fim, ate
     * {@link #MAX_BROADCASTS_PER_RUN} mensagens com a pausa entre elas. O resto fica para a
     * proxima rodada (a cada 60s).
     */
    @Scheduled(fixedRate = 60000)
    public void processScheduledTasks() {
        // IMPORTANTE: usar timezone de São Paulo pois os agendamentos são armazenados
        // em horário de Brasília. O servidor EC2 roda em UTC e sem ZoneId o scheduler
        // dispararia tarefas 3 horas antes do previsto.
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"));
        log.debug("[SCHEDULER] Rodando motor de tarefas. Hora BRT: {}", now);
        if (healthSignals != null) healthSignals.schedulerBeat();

        List<ScheduledTask> pendingTasks = taskRepository.findByStatusAndExecutionTimeBeforeOrderByExecutionTimeAsc(
                ScheduledTask.TaskStatus.PENDING, now,
                org.springframework.data.domain.PageRequest.of(0, MAX_TASKS_PER_RUN));
        if (pendingTasks == null || pendingTasks.isEmpty()) return;

        log.info("Processando {} tarefas pendentes...", pendingTasks.size());

        List<ScheduledTask> broadcasts = new java.util.ArrayList<>();
        for (ScheduledTask task : pendingTasks) {
            if (BROADCAST_TYPE.equals(task.getTaskType())) {
                broadcasts.add(task);
            } else {
                processInOwnTransaction(task, now);
            }
        }
        int enviados = 0;
        for (ScheduledTask task : broadcasts) {
            if (enviados >= MAX_BROADCASTS_PER_RUN) break;
            boolean enviou = processInOwnTransaction(task, now);
            enviados++;
            if (enviou && broadcastDelayMillis > 0) {
                try {
                    Thread.sleep(broadcastDelayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Processa UMA tarefa na sua propria transacao e grava o status dela. Falha (envio ou banco)
     * marca FAILED numa transacao separada — a de processamento ja foi desfeita.
     *
     * @return {@code true} se uma mensagem foi enviada ao cliente
     */
    private boolean processInOwnTransaction(ScheduledTask task, LocalDateTime now) {
        try {
            TenantContext.set(task.getBarbershopId());
            Boolean enviou = transactionTemplate.execute(status -> processOne(task, now));
            return Boolean.TRUE.equals(enviou);
        } catch (Exception e) {
            log.error("[SCHEDULER] Falha na tarefa {} ({}) — marcada como FAILED", task.getId(), task.getTaskType(), e);
            try {
                transactionTemplate.execute(status -> {
                    task.setStatus(ScheduledTask.TaskStatus.FAILED);
                    return taskRepository.save(task);
                });
            } catch (Exception again) {
                log.error("[SCHEDULER] Nao consegui marcar a tarefa {} como FAILED", task.getId(), again);
            }
            return false;
        } finally {
            TenantContext.clear();
        }
    }

    private boolean processOne(ScheduledTask task, LocalDateTime now) {
        String phone = task.getCustomerPhone();

        // Se a task expirou (está mais de 2 horas no passado), descarta sem disparar
        if (task.getExecutionTime().isBefore(now.minusHours(2))) {
            log.warn("[SCHEDULER] Task {} expirada (era para {}). Descartando sem disparar.", task.getId(), task.getExecutionTime());
            task.setStatus(ScheduledTask.TaskStatus.CANCELLED);
            taskRepository.save(task);
            return false;
        }

        // Prazo da confirmacao (V42): nao manda nada ao cliente, entao a pausa nao adia.
        if (com.razor.api.service.AgendaTaskService.CONFIRMATION_DEADLINE.equals(task.getTaskType())) {
            confirmationService.handleDeadline(task);
            task.setStatus(ScheduledTask.TaskStatus.COMPLETED);
            taskRepository.save(task);
            return false;
        }

        // Se o cliente estiver pausado (falando com o atendimento humano), adia o lembrete por 1 hora
        if (customerService.isCustomerPaused(phone)) {
            log.info("Cliente {} em pausa. Adiando tarefa.", phone);
            task.setExecutionTime(LocalDateTime.now().plusHours(1));
            taskRepository.save(task);
            return false;
        }

        // Sincrono de proposito: so marca COMPLETED se a Evolution aceitou o envio. Com o
        // envio assincrono toda tarefa virava COMPLETED, mesmo com o WhatsApp fora do ar.
        if (com.razor.api.service.AgendaTaskService.CONFIRMATION_REQUEST.equals(task.getTaskType())) {
            // Pergunta "1 confirma / 2 desmarca": alem de enviar, marca o agendamento como
            // PENDING e arma a chave da conversa. Agendamento que sumiu nao e perguntado.
            confirmationService.sendRequest(task);
        } else {
            whatsAppService.sendTextMessageSync(phone, task.getMessageContent());
        }

        task.setStatus(ScheduledTask.TaskStatus.COMPLETED);
        taskRepository.save(task);
        return true;
    }

    /**
     * RETENÇÃO INTELIGENTE (11:00)
     * Envia um lembrete amigável a cada 15 dias de inatividade.
     */
    @Scheduled(cron = "0 0 11 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void processCustomerRetentionReminders() {
        log.info("[RETENÇÃO] Rodando motor de lembrete de cabelo crescido...");
        List<com.razor.api.model.Customer> customers = customerRepository.findAll();
        LocalDate today = LocalDate.now();
        int falhas = 0;

        // try/catch POR CLIENTE: antes um unico try envolvia o laco inteiro (todos os tenants),
        // e o primeiro erro interrompia os lembretes de todo mundo que vinha depois.
        for (com.razor.api.model.Customer c : customers) {
            try {
                // Cada cliente pertence a uma barbearia: escopa o contexto para que
                // hasAgendamentoFuturo e o isAdmin consultem o tenant certo.
                TenantContext.set(c.getBarbershopId());

                if (c.isPaused() || tenantAdminService.isAdmin(c.getPhoneNumber())) {
                    continue;
                }
                
                // Determina a data de referência (último serviço ou criação do cadastro)
                LocalDateTime referenceDateTime = c.getLastServiceDate() != null ? c.getLastServiceDate() : c.getCreatedAt();
                if (referenceDateTime == null) continue;
                
                LocalDate referenceDate = referenceDateTime.toLocalDate();
                long daysSince = java.time.temporal.ChronoUnit.DAYS.between(referenceDate, today);
                
                // Envia a cada 15 dias de inatividade (15, 30, 45...)
                if (daysSince > 0 && daysSince % 15 == 0) {
                    if (!agendaService.hasAgendamentoFuturo(c.getPhoneNumber())) {
                        String first = c.getName() != null ? c.getName().split(" ")[0] : "amigo";
                        // Resolvido so aqui dentro: a base inteira passa pelo laco, mas poucos
                        // clientes caem na janela de 15 dias e recebem mensagem de fato.
                        String barbershopName = barbershopSettingsService.resolve().displayName();
                        String reminderMsg = "Fala, " + first + "! 💈\n\n" +
                                "Já faz " + daysSince + " dias desde o seu último corte na " + barbershopName +
                                ". O cabelo já está pedindo aquele trato, hein? ✂️\n\n" +
                                "Quer garantir seu horário para essa semana? Digite **Menu** para ver as opções!";
                        
                        whatsAppService.sendTextMessageSync(c.getPhoneNumber(), reminderMsg);
                        log.info("[RETENÇÃO] Mensagem enviada para {} após {} dias de inatividade.", c.getPhoneNumber(), daysSince);
                    }
                }
            } catch (Exception e) {
                falhas++;
                log.error("[RETENÇÃO] Falha no lembrete do cliente {} (tenant {})", c.getPhoneNumber(), c.getBarbershopId(), e);
            } finally {
                TenantContext.clear();
            }
        }
        if (falhas > 0) {
            log.warn("[RETENÇÃO] {} de {} clientes falharam no lembrete de retenção", falhas, customers.size());
        }
    }

    /**
     * SEXTOU (Sexta-feira 09:00)
     * Lembra clientes ativos de agendar para o final de semana.
     */
    @Scheduled(cron = "0 0 9 * * FRI", zone = "America/Sao_Paulo")
    @Transactional
    public void processSextouReminders() {
        log.info("[SEXTOU] Rodando motor de lembrete de fim de semana...");
        List<com.razor.api.model.Customer> customers = customerRepository.findAll();
        int falhas = 0;
        // try/catch POR CLIENTE (mesmo motivo da retencao): um erro nao derruba a base toda.
        for (com.razor.api.model.Customer c : customers) {
            try {
                TenantContext.set(c.getBarbershopId());

                // Ignora pausados ou administradores (do tenant do cliente)
                if (c.isPaused() || tenantAdminService.isAdmin(c.getPhoneNumber())) {
                    continue;
                }

                if (!agendaService.hasAgendamentoFuturo(c.getPhoneNumber())) {
                    String first = c.getName() != null ? c.getName().split(" ")[0] : "amigo";
                    String msg = "Fala, " + first + "! Sextou! 🍻💈\n\n" +
                            "O final de semana tá aí, já garantiu seu horário pra dar aquele trato pro rolê? ✂️\n\n" +
                            "Ainda temos algumas vagas! Digite *Menu* para agendar ou ver os horários.";
                    
                    whatsAppService.sendTextMessageSync(c.getPhoneNumber(), msg);
                    log.info("[SEXTOU] Mensagem de Sextou enviada para {}.", c.getPhoneNumber());
                }
            } catch (Exception e) {
                falhas++;
                log.error("[SEXTOU] Falha no lembrete do cliente {} (tenant {})", c.getPhoneNumber(), c.getBarbershopId(), e);
            } finally {
                TenantContext.clear();
            }
        }
        if (falhas > 0) {
            log.warn("[SEXTOU] {} de {} clientes falharam no lembrete de sextou", falhas, customers.size());
        }
    }

    /**
     * RELATÓRIO MATINAL (08:00) - Bom dia e Status do Sistema
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    public void sendDailyAdminReport() {
        forEachActiveTenant(shop -> {
            long totalCustomers = customerRepository.countByBarbershopId(shop.getId());
            int totalHoje = agendaService.getAgendamentosHojeCount();
            String agendaHoje = agendaService.getAgendaHojeText();

            String report = String.format("""
                    ☀️ *Bom dia, Administrador!*
                    _%s_

                    Razor online. 🤖✅
                    Base de Clientes: %d

                    📅 *Hoje temos %d agendamento(s):*

                    %s

                    Tenha um excelente dia de trabalho! 💈
                    """, shop.getName(), totalCustomers, totalHoje, agendaHoje);

            for (String adminPhone : new java.util.HashSet<>(tenantAdminService.adminPhones())) {
                whatsAppService.sendTextMessage(adminPhone, report);
            }
        });
    }

    /**
     * RELATÓRIO NOTURNO DA AGENDA (21:00)
     * Envia o fechamento com os cortes e serviços feitos no dia.
     */
    @Scheduled(cron = "0 0 21 * * *", zone = "America/Sao_Paulo")
    public void sendNightlyAgendaRequest() {
        forEachActiveTenant(shop -> {
            String resumoDetalhado = agendaService.getResumoDetelhadoDoDia();
            for (String adminPhone : new java.util.HashSet<>(tenantAdminService.adminPhones())) {
                whatsAppService.sendTextMessage(adminPhone, resumoDetalhado);
            }
        });
        log.info("Relatório noturno de serviços enviado para os administradores dos tenants ativos.");
    }

    /**
     * RELATÓRIO DA SEMANA (Sábado 18:00)
     */
    @Scheduled(cron = "0 0 18 * * SAT", zone = "America/Sao_Paulo")
    public void sendWeeklySummaryReminder() {
        String msg = "📊 *Fechamento da Semana Disponível!*\n\n" +
                     "A semana está quase acabando! Você já pode conferir o seu *Resumo Financeiro da Semana* completo.\n\n" +
                     "Acesse o painel enviando o comando *menu* (opção Admin) e confira o quanto a barbearia faturou essa semana!";
        forEachActiveTenant(shop -> {
            for (String adminPhone : new java.util.HashSet<>(tenantAdminService.adminPhones())) {
                whatsAppService.sendTextMessage(adminPhone, msg);
            }
        });
        log.info("Lembrete de Resumo Semanal enviado para os administradores dos tenants ativos.");
    }
}