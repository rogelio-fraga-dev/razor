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
                // A tarefa carrega seu tenant; escopamos o contexto para que checagens
                // (ex.: pausa do cliente) usem a barbearia certa, e nao a padrao.
                TenantContext.set(task.getBarbershopId());
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
            } finally {
                TenantContext.clear();
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
            List<com.razor.api.model.Customer> customers = customerRepository.findAll();
            LocalDate today = LocalDate.now();

            for (com.razor.api.model.Customer c : customers) {
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
        } finally {
            TenantContext.clear();
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
            List<com.razor.api.model.Customer> customers = customerRepository.findAll();
            for (com.razor.api.model.Customer c : customers) {
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
                    
                    whatsAppService.sendTextMessage(c.getPhoneNumber(), msg);
                    log.info("[SEXTOU] Mensagem de Sextou enviada para {}.", c.getPhoneNumber());
                }
            }
        } catch (Exception e) {
            log.error("[SEXTOU] Erro no scheduler de sextou: {}", e.getMessage(), e);
        } finally {
            TenantContext.clear();
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