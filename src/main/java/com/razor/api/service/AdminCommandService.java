package com.razor.api.service;

import com.razor.api.config.RazorProperties;
import com.razor.api.dto.EvolutionWebhookDTO;
import com.razor.api.model.Customer;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.InteractionRepository;
import com.razor.api.repository.WaitlistRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.ConsoleText;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Fluxo do ADMINISTRADOR: menu de gestao, dashboards, disparos (imediatos e
 * agendados), pausa/retomada de clientes, importacao de CSV/agenda e a maquina
 * de estados do admin. Extraido do {@code OrchestratorService} (Fase 0.5).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCommandService {

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final InteractionRepository interactionRepository;
    private final OpenAIService openAIService;
    private final AgendaService agendaService;
    private final RazorProperties properties;
    private final WaitlistRepository waitlistRepository;
    private final WhatsAppMessenger messenger;
    private final EvolutionMediaService evolutionMediaService;

    // Estado por-admin (chaveado por telefone). Instancia, nao static: servico singleton
    // (mesmo comportamento em runtime) e assim cada teste tem estado isolado.
    private final Map<String, String> adminStates = new ConcurrentHashMap<>();
    private final Map<String, List<String>> adminRetomarOptions = new ConcurrentHashMap<>();
    private final Map<String, List<String>> adminApagarOptions = new ConcurrentHashMap<>();
    private final Map<String, String> adminScheduledMsg = new ConcurrentHashMap<>();

    private void sendMsg(String phone, String text, String role) {
        messenger.send(phone, text, role);
    }

    private String sanitizeForConsole(String text) {
        return ConsoleText.sanitize(text);
    }

    private String fetchBase64FromEvolution(String messageId) {
        return evolutionMediaService.fetchBase64(messageId);
    }

    private boolean isSystemCommand(String text) {
        if (text == null) return false;
        String t = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .replaceAll("[^a-z0-9 ]", "");
        return t.contains("comando") || t.contains("menu") || t.contains("ajuda") ||
               t.contains("resumo") || t.contains("dashboard") ||
               t.contains("aviso") || t.contains("prospec") || t.contains("promocao") || t.contains("promo") ||
               t.contains("pausar") || t.contains("pausa") || t.contains("retomar") || t.contains("retoma") ||
               t.contains("agenda") || t.contains("listar") || t.contains("lista") ||
               t.contains("apagar") || t.contains("apaga") || t.contains("excluir") || t.contains("exclui");
    }

    // ==========================================
    // FLUXO DO ADMINISTRADOR
    // ==========================================
    public void processAdminMessage(EvolutionWebhookDTO webhook, String phoneNumber) {
        String command = null;

        if (webhook.hasDocument()) {
            adminStates.remove(phoneNumber);
            String base64 = webhook.getBase64() != null ? webhook.getBase64() : fetchBase64FromEvolution(webhook.getData().getKey().getId());
            if (base64 != null) {
                log.info("[ADMIN] CSV de contatos recebido do Admin {}.", phoneNumber);
                sendMsg(phoneNumber, "⏳ Lendo a base CSV...", "ADMIN");
                try {
                    int salvos = customerService.importCustomersFromCsvBase64(base64);
                    log.info("[ADMIN] CSV processado com sucesso. {} contatos salvos.", salvos);
                    sendMsg(phoneNumber, "✅ Importação Concluída! *" + salvos + "* clientes na base.", "ADMIN");
                } catch (Exception e) {
                    log.error("[ADMIN] Falha ao processar CSV: {}", e.getMessage(), e);
                    sendMsg(phoneNumber, "❌ Erro no CSV.", "ADMIN");
                }
            } else {
                log.warn("[ADMIN] Recebido CSV corrompido ou sem base64.");
                sendMsg(phoneNumber, "❌ O arquivo chegou corrompido.", "ADMIN");
            }
            return;
        }

        if (webhook.hasImage()) {
            adminStates.remove(phoneNumber);
            String base64 = webhook.getBase64() != null ? webhook.getBase64() : fetchBase64FromEvolution(webhook.getData().getKey().getId());
            if (base64 != null) {
                log.info("[ADMIN] Imagem de agenda recebida do Admin {} para processamento via GPT-4o Vision.", phoneNumber);
                sendMsg(phoneNumber, "⏳ Lendo imagem da agenda com IA...", "ADMIN");
                try {
                    String agendaJson = openAIService.extractAgendaFromImage(base64, webhook.getMimeType());
                    log.info("[ADMIN] JSON extraido da imagem de agenda: {}", sanitizeForConsole(agendaJson));
                    int salvos = agendaService.processAgenda(agendaJson);
                    log.info("[ADMIN] Agenda atualizada. {} agendamentos importados.", salvos);
                    sendMsg(phoneNumber, "✅ " + salvos + " clientes identificados na imagem!", "ADMIN");
                } catch (Exception e) {
                    log.error("[ADMIN] Falha ao ler imagem com Vision: {}", e.getMessage(), e);
                    sendMsg(phoneNumber, "❌ Erro na Visão IA.", "ADMIN");
                }
            } else {
                log.warn("[ADMIN] Recebida imagem da agenda corrompida.");
                sendMsg(phoneNumber, "❌ A imagem não pôde ser decodificada.", "ADMIN");
            }
            return;
        }

        if (webhook.hasAudio()) {
            String base64 = webhook.getBase64() != null ? webhook.getBase64() : fetchBase64FromEvolution(webhook.getData().getKey().getId());
            if (base64 == null) {
                log.warn("[ADMIN] Audio recebido do Admin, mas sem base64 disponível.");
                return;
            }
            log.info("[ADMIN] Mensagem de voz recebida do Admin {}, transcrevendo com Whisper...", phoneNumber);
            sendMsg(phoneNumber, "🎧 Ouvindo áudio...", "ADMIN");
            command = openAIService.transcribeAudio(base64, webhook.getMimeType());
            log.info("[ADMIN] Audio transcrito: '{}'", sanitizeForConsole(command));

            if (!adminStates.containsKey(phoneNumber) && command != null && !isSystemCommand(command)) {
                sendMsg(phoneNumber, "📝 *Transcrição Livre:*\n" + command, "ADMIN");
                return;
            }
        } else {
            command = webhook.getMessageText();
            log.info("[ADMIN] Comando de texto recebido do Admin: '{}'", sanitizeForConsole(command));
        }

        if (command != null) {
            String originalCommand = command.trim();
            String normalized = Normalizer.normalize(originalCommand.toLowerCase(), Normalizer.Form.NFD)
                    .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
            String cmdLower = normalized.replaceAll("[^a-z0-9 ]", "").trim();

            String adminRowId = MenuOptions.resolveAdminMenuOptionId(originalCommand);
            if (adminRowId != null) {
                if (MenuOptions.ROW_ID_ADMIN_DASHBOARD.equals(adminRowId)) cmdLower = "1";
                else if (MenuOptions.ROW_ID_ADMIN_AVISOS.equals(adminRowId)) cmdLower = "2";
                else if (MenuOptions.ROW_ID_ADMIN_PROMO.equals(adminRowId)) cmdLower = "3";
                else if (MenuOptions.ROW_ID_ADMIN_PAUSAR.equals(adminRowId)) cmdLower = "4";
                else if (MenuOptions.ROW_ID_ADMIN_RETOMAR.equals(adminRowId)) cmdLower = "5";
                else if (MenuOptions.ROW_ID_ADMIN_AGENDA.equals(adminRowId)) cmdLower = "6";
                else if (MenuOptions.ROW_ID_ADMIN_REL_MATINAL.equals(adminRowId)) cmdLower = "7";
                else if (MenuOptions.ROW_ID_ADMIN_REL_NOTURNO.equals(adminRowId)) cmdLower = "8";
                else if (MenuOptions.ROW_ID_ADMIN_LISTAR.equals(adminRowId)) cmdLower = "9";
                else if (MenuOptions.ROW_ID_ADMIN_APAGAR.equals(adminRowId)) cmdLower = "10";
                else if (MenuOptions.ROW_ID_ADMIN_RESUMO_SEMANA.equals(adminRowId)) cmdLower = "12";
                else if (MenuOptions.ROW_ID_ADMIN_AGENDA_SEMANA.equals(adminRowId)) cmdLower = "13";
            }

            String currentState = adminStates.get(phoneNumber);

            if (currentState != null) {
                if (cmdLower.equals("cancelar")) {
                    adminStates.remove(phoneNumber);
                    adminRetomarOptions.remove(phoneNumber);
                    adminApagarOptions.remove(phoneNumber);
                    adminScheduledMsg.remove(phoneNumber);
                    sendFullMenu(phoneNumber);
                    return;
                }
                if (currentState.equals("AVISO_BASE")) {
                    adminStates.remove(phoneNumber);
                    performBroadcast(phoneNumber, originalCommand, false);
                    return;
                }
                if (currentState.equals("AVISO_PROSPEC")) {
                    adminStates.remove(phoneNumber);
                    performBroadcast(phoneNumber, originalCommand, true);
                    return;
                }
                if (currentState.equals("PAUSAR")) {
                    adminStates.remove(phoneNumber);
                    String target = cmdLower.replaceAll("[^0-9]", "");
                    customerService.pauseCustomer(target, 60);
                    sendMsg(phoneNumber, "⏸️ Robô silenciado com sucesso para esse cliente.", "ADMIN");
                    return;
                }
                if (currentState.equals("RETOMAR")) {
                    adminStates.remove(phoneNumber);
                    List<String> options = adminRetomarOptions.remove(phoneNumber);
                    try {
                        int index = Integer.parseInt(cmdLower.replaceAll("[^0-9]", "")) - 1;
                        if (options != null && index >= 0 && index < options.size()) {
                            String targetPhone = options.get(index);
                            customerService.resumeCustomer(targetPhone);
                            sendMsg(phoneNumber, "▶️ Robô religado com sucesso.", "ADMIN");
                        } else {
                            sendMsg(phoneNumber, "❌ Opção inválida.", "ADMIN");
                        }
                    } catch (Exception e) {
                        sendMsg(phoneNumber, "❌ Formato inválido.", "ADMIN");
                    }
                    return;
                }
                if (currentState.equals("APAGAR_CONTATO")) {
                    adminStates.remove(phoneNumber);
                    List<String> options = adminApagarOptions.remove(phoneNumber);
                    try {
                        int index = Integer.parseInt(cmdLower.replaceAll("[^0-9]", "")) - 1;
                        if (options != null && index >= 0 && index < options.size()) {
                            String targetPhone = options.get(index);
                            Customer c = customerRepository
                                    .findByBarbershopIdAndPhoneNumber(TenantContext.getCurrent(), targetPhone).orElse(null);
                            if (c != null) {
                                interactionRepository.deleteByCustomerId(c.getId());
                                customerRepository.delete(c);
                                sendMsg(phoneNumber, "✅ Cliente apagado da base!", "ADMIN");
                            } else {
                                sendMsg(phoneNumber, "❌ Cliente não encontrado.", "ADMIN");
                            }
                        } else {
                            sendMsg(phoneNumber, "❌ Opção inválida.", "ADMIN");
                        }
                    } catch (Exception e) {
                        sendMsg(phoneNumber, "❌ Erro ao apagar.", "ADMIN");
                    }
                    return;
                }
                if (currentState.equals("AGENDAR_DISPARO_MSG")) {
                    adminStates.remove(phoneNumber);
                    adminScheduledMsg.put(phoneNumber, originalCommand);
                    adminStates.put(phoneNumber, "AGENDAR_DISPARO_DATA");
                    sendMsg(phoneNumber, "🗓️ *Data e Hora:*\nDigite o horário do disparo no formato numérico: *DD/MM HH:MM* (Ex: 28/05 10:30)", "ADMIN");
                    return;
                }
                if (currentState.equals("AGENDAR_DISPARO_DATA")) {
                    adminStates.remove(phoneNumber);
                    String msg = adminScheduledMsg.remove(phoneNumber);
                    try {
                        String[] parts = cmdLower.replaceAll("[^0-9/:]", " ").trim().split("\\s+");
                        String datePart = parts[0];
                        String timePart = parts[1];
                        int day = Integer.parseInt(datePart.split("/")[0]);
                        int month = Integer.parseInt(datePart.split("/")[1]);
                        int hour = Integer.parseInt(timePart.split(":")[0]);
                        int minute = Integer.parseInt(timePart.split(":")[1]);
                        LocalDateTime execTime = LocalDateTime.of(LocalDateTime.now().getYear(), month, day, hour, minute);
                        if (execTime.isBefore(LocalDateTime.now())) execTime = execTime.plusYears(1);

                        agendaService.agendarDisparoMassa(msg, execTime);
                        sendMsg(phoneNumber, "✅ Disparo agendado com sucesso para " + datePart + " às " + timePart + "!", "ADMIN");
                    } catch (Exception e) {
                        sendMsg(phoneNumber, "❌ Formato inválido. Use *DD/MM HH:MM* (Ex: 28/05 10:30).", "ADMIN");
                    }
                    return;
                }
            }

            if (cmdLower.contains("comando") || cmdLower.contains("menu")) {
                sendFullMenu(phoneNumber);
            } else if (cmdLower.equals("1") || cmdLower.contains("dashboard") || cmdLower.contains("resumo") || cmdLower.contains("financeiro") || cmdLower.contains("caixa")) {
                String resumoFinanceiro = agendaService.getResumoFinanceiroDetalhado();
                sendMsg(phoneNumber, resumoFinanceiro, "ADMIN");
            } else if (cmdLower.equals("2") || cmdLower.contains("agenda do dia") || cmdLower.contains("painel")) {
                long total = customerRepository.countByBarbershopId(TenantContext.getCurrent());
                long waitlistCount = waitlistRepository.count();
                long pausedCount = customerService.getPausedPhones().size();
                String painelAgendamentos = agendaService.getPainelAgendamentosDetalhado(total, waitlistCount, pausedCount);
                sendMsg(phoneNumber, painelAgendamentos, "ADMIN");
            } else if (cmdLower.equals("3") || cmdLower.contains("agenda da semana")) {
                String agendaSemana = agendaService.getAgendaSemanaText();
                sendMsg(phoneNumber, agendaSemana, "ADMIN");
            } else if (cmdLower.equals("4") || cmdLower.contains("resumo da semana")) {
                String resumo = agendaService.getResumoFinanceiroDaSemana();
                sendMsg(phoneNumber, resumo, "ADMIN");
            } else if (cmdLower.equals("5") || (cmdLower.contains("aviso") && !cmdLower.contains("promocao"))) {
                adminStates.put(phoneNumber, "AVISO_BASE");
                sendMsg(phoneNumber, "📢 *Disparo de Avisos*\nEnvie a mensagem agora. _(Ou digite 'cancelar')_", "ADMIN");
            } else if (cmdLower.equals("6") || cmdLower.contains("promocao") || cmdLower.contains("prospec")) {
                adminStates.put(phoneNumber, "AVISO_PROSPEC");
                sendMsg(phoneNumber, "🎯 *Promoções do dia*\nEnvie a oferta que deseja disparar para a base toda.", "ADMIN");
            } else if (cmdLower.equals("7") || cmdLower.contains("agendar disparo")) {
                adminStates.put(phoneNumber, "AGENDAR_DISPARO_MSG");
                sendMsg(phoneNumber, "📝 *Agendar Disparo*\nQual é a mensagem que deseja enviar para a base? (Digite 'cancelar' para abortar)", "ADMIN");
            } else if (cmdLower.equals("8") || cmdLower.contains("pausa")) {
                adminStates.put(phoneNumber, "PAUSAR");
                sendMsg(phoneNumber, "⏸️ *Pausar Robô*\nDigite o telefone com DDD do cliente. _(Ou digite 'cancelar')_", "ADMIN");
            } else if (cmdLower.equals("9") || cmdLower.contains("retoma")) {
                List<String> pausedPhones = customerService.getPausedPhones();
                if (pausedPhones.isEmpty()) {
                    sendMsg(phoneNumber, "▶️ *Retomar*\nNenhum cliente pausado no momento.", "ADMIN");
                } else {
                    adminStates.put(phoneNumber, "RETOMAR");
                    adminRetomarOptions.put(phoneNumber, pausedPhones);
                    StringBuilder sb = new StringBuilder("▶️ *Retomar Robô - Clientes Pausados*\n\n");
                    for (int i = 0; i < pausedPhones.size(); i++) {
                        sb.append("*").append(i + 1).append("* - ").append(pausedPhones.get(i)).append("\n");
                    }
                    sb.append("\nDigite o *NÚMERO DA OPÇÃO* _(Ou digite 'cancelar')_.");
                    sendMsg(phoneNumber, sb.toString(), "ADMIN");
                }
            } else if (cmdLower.equals("10") || cmdLower.contains("lista")) {
                List<Customer> customers = customerRepository.findByBarbershopId(TenantContext.getCurrent());
                if (customers.isEmpty()) {
                    sendMsg(phoneNumber, "📭 Nenhum cliente salvo na base.", "ADMIN");
                } else {
                    StringBuilder sb = new StringBuilder("📋 *Base (" + customers.size() + ")*\n\n");
                    for (Customer c : customers) sb.append("👤 ").append(c.getName()).append(" - ").append(c.getPhoneNumber()).append("\n");
                    sendMsg(phoneNumber, sb.toString(), "ADMIN");
                }
            } else if (cmdLower.equals("11") || cmdLower.contains("apaga") || cmdLower.contains("exclui")) {
                List<Customer> customers = customerRepository.findByBarbershopId(TenantContext.getCurrent());
                if (customers.isEmpty()) {
                    sendMsg(phoneNumber, "📭 Nenhum cliente salvo.", "ADMIN");
                } else {
                    adminStates.put(phoneNumber, "APAGAR_CONTATO");
                    List<String> phoneList = new ArrayList<>();
                    StringBuilder sb = new StringBuilder("🗑️ *Apagar Cliente*\n\n");
                    for (int i = 0; i < customers.size(); i++) {
                        phoneList.add(customers.get(i).getPhoneNumber());
                        sb.append("*").append(i + 1).append("* - ").append(customers.get(i).getName()).append("\n");
                    }
                    adminApagarOptions.put(phoneNumber, phoneList);
                    sb.append("\nDigite o *NÚMERO DA OPÇÃO* para apagar _(Ou digite 'cancelar')_.");
                    sendMsg(phoneNumber, sb.toString(), "ADMIN");
                }
            } else if (cmdLower.equals("12") || cmdLower.contains("ajuda") || cmdLower.contains("manual")) {
                String manual = """
                        📖 *MANUAL DO ADMINISTRADOR - RAZOR*

                        Aqui estão todas as automações que o sistema realiza nos bastidores:

                        🤖 *Atendimento e IA:*
                        - A IA agenda, tira dúvidas e negocia horários sozinha.
                        - Protege a agenda (não marca 2 clientes na mesma hora).
                        - Lê fotos de agenda em papel se você enviar para cá.

                        ⏰ *Rotinas Automáticas (Horário de Brasília):*
                        - *08:00* ➡️ Relatório Matinal com os clientes de hoje.
                        - *11:00* ➡️ Cobra clientes sumidos (mais de 15 dias sem vir).
                        - *21:00* ➡️ Fechamento de Caixa com faturamento.
                        - *1 Hora Antes* ➡️ Lembrete de agendamento pro cliente.
                        - *1 Hora Depois* ➡️ Pesquisa de satisfação pro cliente.

                        ⭐ *Fidelidade e Retenção:*
                        - O cliente ganha 1 ponto a cada novo agendamento realizado.
                        - Aos 10 pontos, ganha cupom automático de 10% OFF.
                        - *Sextou:* Envia lembrete toda sexta-feira às 09h para TODOS os usuários sem agendamento no fim de semana.

                        🛠️ *Disparos e Controle:*
                        - O botão de 'Avisos' (5) e 'Promoções' (6) dispara mensagem IMEDIATA para todos.
                        - O botão 'Agendar Disparo' (7) permite escrever um texto para disparar num dia e hora futuros.
                        - Pressione 12 ou diga 'manual' para ver esta mensagem novamente.
                        """;
                sendMsg(phoneNumber, manual, "ADMIN");
            } else {
                if (!webhook.hasAudio()) {
                    sendMsg(phoneNumber, "Fala, Chefe! 💈 Digite *menu* para ver as opções.", "ADMIN");
                }
            }
        }
    }

    private void sendFullMenu(String phoneNumber) {
        log.info("[SISTEMA] Enviando Menu Textual de Gestao para o Administrador: {}", phoneNumber);
        String fullMenu = """
                🛠️ *PAINEL DA BARBEARIA*

                *1* - 💰 Resumo Financeiro do Dia
                *2* - 📅 Agenda do Dia
                *3* - 📆 Agenda da Semana
                *4* - 📊 Resumo Financeiro da Semana
                *5* - 📢 Avisos (Geral)
                *6* - 🎯 Promoções do dia
                *7* - 📅 Agendar Disparo
                *8* - ⏸️ Pausar bot (Cliente)
                *9* - ▶️ Retomar bot (Cliente)
                *10* - 📋 Listar Clientes
                *11* - 🗑️ Apagar um Cliente
                *12* - ℹ️ Ajuda / Manual do Sistema

                _Digite o número ou comando desejado._
                """;
        sendMsg(phoneNumber, fullMenu, "ADMIN");
    }

    @Async
    public void performBroadcast(String adminPhone, String message, boolean isProspecting) {
        List<Customer> allCustomers = customerRepository.findByBarbershopId(TenantContext.getCurrent());
        sendMsg(adminPhone, "🚀 Iniciando disparo para " + allCustomers.size() + " contatos...", "ADMIN");

        List<String> contatosSucesso = new ArrayList<>();
        for (Customer customer : allCustomers) {
            try {
                if (customer.getPhoneNumber().contains(adminPhone)) continue;
                String header = isProspecting ? "💈 *Barbearia Ficticia - Promoção* 💈\n\n" : "📢 *Aviso da Barbearia Ficticia*\n\n";
                sendMsg(customer.getPhoneNumber(), header + message, "DISPARO");
                contatosSucesso.add("✅ " + customer.getName());
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[DISPARO] Interrompido durante o envio para {}", customer.getPhoneNumber());
                break;
            } catch (Exception e) {
                log.warn("[DISPARO] Falha ao enviar para {}: {}", customer.getPhoneNumber(), e.getMessage());
            }
        }
        sendMsg(adminPhone, "📊 *Relatório de Disparo*\nTotal alcançado: " + contatosSucesso.size() + "\n\n" + String.join("\n", contatosSucesso), "ADMIN");
    }
}
