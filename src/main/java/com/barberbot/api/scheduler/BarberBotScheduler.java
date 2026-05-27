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


                // Regra de Cartão Fidelidade ao concluir REVIEW_REQUEST
                if ("REVIEW_REQUEST".equals(task.getTaskType())) {
                    try {
                        com.barberbot.api.model.Customer customer = customerRepository.findByPhoneNumber(phone).orElse(null);
                        if (customer != null) {
                            int currentPoints = customer.getFidelityPoints() != null ? customer.getFidelityPoints() : 0;
                            int newPoints = currentPoints + 1;
                            customer.setFidelityPoints(newPoints);
                            customer.setLastServiceDate(LocalDateTime.now());
                            customerRepository.save(customer);
                            log.info("[FIDELIDADE] Adicionado 1 ponto para {}. Total: {}", phone, newPoints);
                            
                            if (newPoints >= 10) {
                                customer.setFidelityPoints(0);
                                customerRepository.save(customer);
                                
                                String coupon = "FIDELIDADE10-" + java.util.UUID.randomUUID().toString().substring(0, 5).toUpperCase();
                                String fidelityRewardMsg = "🎉 *PARABÉNS!* Você completou 10 pontos no seu Cartão Fidelidade da Barbearia Ficticia! 🥳✂️\n\n" +
                                        "Como recompensa, você ganhou *10% de desconto* no seu próximo corte! Apresente o código abaixo no caixa:\n\n" +
                                        "🎟️ *" + coupon + "*\n\n" +
                                        "Obrigado pela preferência e por fazer parte da nossa história! 👊💈";
                                whatsAppService.sendTextMessage(phone, fidelityRewardMsg);
                                log.info("[FIDELIDADE] Cliente {} atingiu 10 pontos e ganhou cupom: {}", phone, coupon);
                            }
                        }
                    } catch (Exception ex) {
                        log.error("[FIDELIDADE] Erro ao atualizar pontos para {}: {}", phone, ex.getMessage());
                    }
                }

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
                if (c.isPaused() || c.getPhoneNumber().equals(properties.getAdmin().getPhone())) {
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
     * RELATÓRIO MATINAL (08:00) - Bom dia e Status do Sistema
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    public void sendDailyAdminReport() {
        try {
            String adminPhone = properties.getAdmin().getPhone();
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
                    """, totalCustomers, totalHoje, agendaHoje);

            whatsAppService.sendTextMessage(adminPhone, report);
            
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
            String adminPhone = properties.getAdmin().getPhone();
            String resumoDetalhado = agendaService.getResumoDetelhadoDoDia();
            whatsAppService.sendTextMessage(adminPhone, resumoDetalhado);
            log.info("Relatório noturno de serviços enviado para o admin.");
        } catch (Exception e) {
            log.error("Erro ao enviar relatório noturno: {}", e.getMessage());
        }
    }
}