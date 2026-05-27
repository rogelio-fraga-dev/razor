package com.barberbot.api.scheduler;

import com.barberbot.api.config.BarberBotProperties;
import com.barberbot.api.model.ScheduledTask;
import com.barberbot.api.repository.CustomerRepository;
import com.barberbot.api.repository.ScheduledTaskRepository;
import com.barberbot.api.service.CustomerService;
import com.barberbot.api.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BarberBotScheduler {

    private final ScheduledTaskRepository taskRepository;
    private final WhatsAppService whatsAppService;
    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final BarberBotProperties properties;
    private final com.barberbot.api.service.AgendaService agendaService;

    /**
     * MOTOR DE DISPAROS (Roda a cada 60 segundos)
     * Verifica o banco e envia as mensagens na hora certa.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void processScheduledTasks() {
        // IMPORTANTE: usar timezone de São Paulo pois os agendamentos são armazenados
        // em horário de Brasília. O servidor EC2 roda em UTC e sem ZoneId o scheduler
        // dispararia tarefas 3 horas antes do previsto.
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"));
        log.debug("[SCHEDULER] Rodando motor de tarefas. Hora BRT: {}", now);
        
        // Busca tarefas que já deram o horário e ainda não foram enviadas
        List<ScheduledTask> pendingTasks = taskRepository.findByStatusAndExecutionTimeBefore(
                ScheduledTask.TaskStatus.PENDING, now
        );

        if (pendingTasks.isEmpty()) return;

        log.info("Processando {} tarefas pendentes...", pendingTasks.size());

        for (ScheduledTask task : pendingTasks) {
            try {
                String phone = task.getCustomerPhone();

                // Se a task expirou (está mais de 2 horas no passado), descarta sem disparar
                if (task.getExecutionTime().isBefore(now.minusHours(2))) {
                    log.warn("[SCHEDULER] Task {} expirada (era para {}). Descartando sem disparar.", task.getId(), task.getExecutionTime());
                    task.setStatus(ScheduledTask.TaskStatus.CANCELLED);
                    taskRepository.save(task);
                    continue;
                }

                // Se o cliente estiver pausado (falando com o atendimento humano), adia o lembrete por 1 hora
                if (customerService.isCustomerPaused(phone)) {
                    log.info("Cliente {} em pausa. Adiando tarefa.", phone);
                    task.setExecutionTime(LocalDateTime.now().plusHours(1));
                    taskRepository.save(task);
                    continue;
                }

                whatsAppService.sendTextMessage(phone, task.getMessageContent());

                task.setStatus(ScheduledTask.TaskStatus.COMPLETED);
                taskRepository.save(task);


            } catch (Exception e) {
                log.error("Erro na tarefa {}: {}", task.getId(), e.getMessage());
                task.setStatus(ScheduledTask.TaskStatus.FAILED);
                taskRepository.save(task);
            }
        }
    }

    /**
     * RETENÇÃO INTELIGENTE (11:00)
     * Envia um lembrete amigável a cada 15 dias de inatividade.
     */
    @Scheduled(cron = "0 0 11 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void processCustomerRetentionReminders() {
        log.info("[RETENÇÃO] Rodando motor de lembrete de cabelo crescido...");
        try {
            List<com.barberbot.api.model.Customer> customers = customerRepository.findAll();
            LocalDate today = LocalDate.now();
            
            for (com.barberbot.api.model.Customer c : customers) {
                // Ignorar administradores
                boolean isAdmin = false;
                for (String admPhone : properties.getAdmin().getPhones()) {
                    if (c.getPhoneNumber().equals(admPhone)) {
                        isAdmin = true;
                        break;
                    }
                }
                
                if (c.isPaused() || isAdmin) {
                    continue;
                }
                
                // Determina a data de referência (último serviço ou criação do cadastro)
                LocalDateTime referenceDateTime = c.getLastServiceDate() != null ? c.getLastServiceDate() : c.getCreatedAt();
                if (referenceDateTime == null) continue;
                
                LocalDate referenceDate = referenceDateTime.toLocalDate();
                long daysSince = java.time.temporal.ChronoUnit.DAYS.between(referenceDate, today);
                
                // Envia a cada 15 dias de inatividade (15, 30, 45...)
                if (daysSince > 0 && daysSince % 15 == 0) {
                    if (agendaService.getAgendamentoCliente(c.getPhoneNumber()) == null) {
                        String first = c.getName() != null ? c.getName().split(" ")[0] : "amigo";
                        String reminderMsg = "Fala, " + first + "! 💈\n\n" +
                                "Já faz " + daysSince + " dias desde o seu último corte na Barbearia Ficticia. O cabelo já está pedindo aquele trato, hein? ✂️\n\n" +
                                "Quer garantir seu horário para essa semana? Digite **Menu** para ver as opções!";
                        
                        whatsAppService.sendTextMessage(c.getPhoneNumber(), reminderMsg);
                        log.info("[RETENÇÃO] Mensagem enviada para {} após {} dias de inatividade.", c.getPhoneNumber(), daysSince);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[RETENÇÃO] Erro no scheduler de retenção: {}", e.getMessage(), e);
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
        try {
            List<com.barberbot.api.model.Customer> customers = customerRepository.findAll();
            for (com.barberbot.api.model.Customer c : customers) {
                // Ignora pausados, sem interação, ou se for Admin
                boolean isAdmin = false;
                for (String admPhone : properties.getAdmin().getPhones()) {
                    if (c.getPhoneNumber().equals(admPhone)) {
                        isAdmin = true;
                        break;
                    }
                }
                
                if (c.isPaused() || isAdmin) {
                    continue;
                }
                
                if (agendaService.getAgendamentoCliente(c.getPhoneNumber()) == null) {
                    String first = c.getName() != null ? c.getName().split(" ")[0] : "amigo";
                    String msg = "Fala, " + first + "! Sextou! 🍻💈\n\n" +
                            "O final de semana tá aí, já garantiu seu horário pra dar aquele trato pro rolê? ✂️\n\n" +
                            "Ainda temos algumas vagas! Digite *Menu* para agendar ou ver os horários.";
                    
                    whatsAppService.sendTextMessage(c.getPhoneNumber(), msg);
                    log.info("[SEXTOU] Mensagem de Sextou enviada para {}.", c.getPhoneNumber());
                }
            }
        } catch (Exception e) {
            log.error("[SEXTOU] Erro no scheduler de sextou: {}", e.getMessage(), e);
        }
    }

    /**
     * RELATÓRIO MATINAL (08:00) - Bom dia e Status do Sistema
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    public void sendDailyAdminReport() {
        try {
            long totalCustomers = customerRepository.count();
            int totalHoje = agendaService.getAgendamentosHojeCount();
            String agendaHoje = agendaService.getAgendaHojeText();
            
            String report = String.format("""
                    ☀️ *Bom dia, Administrador!*
                    
                    BarberBot online. 🤖✅
                    Base de Clientes: %d
                    
                    📅 *Hoje temos %d agendamento(s):*
                    
                    %s
                    
                    Tenha um excelente dia de trabalho! 💈
                    
                    ---
                    *Recado do Dev:*
                    Fala meu querido professor, subi algumas atualizações!
                    O botão de ajuda (11) lista o que o sistema faz.
                    Teste o disparo de mensagens (opção 2): mande um áudio e veja se ele entende e dispara pra base de clientes.
                    Qualquer dúvida você me fala!
                    """, totalCustomers, totalHoje, agendaHoje);

            for (String adminPhone : properties.getAdmin().getPhones()) {
                whatsAppService.sendTextMessage(adminPhone, report);
            }
            
        } catch (Exception e) {
            log.error("Erro ao enviar relatório matinal: {}", e.getMessage());
        }
    }

    /**
     * RELATÓRIO NOTURNO DA AGENDA (21:00)
     * Envia o fechamento com os cortes e serviços feitos no dia.
     */
    @Scheduled(cron = "0 0 21 * * *", zone = "America/Sao_Paulo")
    public void sendNightlyAgendaRequest() {
        try {
            String resumoDetalhado = agendaService.getResumoDetelhadoDoDia();
            for (String adminPhone : properties.getAdmin().getPhones()) {
                whatsAppService.sendTextMessage(adminPhone, resumoDetalhado);
            }
            log.info("Relatório noturno de serviços enviado para os administradores.");
        } catch (Exception e) {
            log.error("Erro ao enviar relatório noturno: {}", e.getMessage());
        }
    }
}