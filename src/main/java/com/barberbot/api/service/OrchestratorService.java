package com.barberbot.api.service;

import com.barberbot.api.config.BarberBotProperties;
import com.barberbot.api.dto.EvolutionWebhookDTO;
import com.barberbot.api.model.Customer;
import com.barberbot.api.model.Interaction;
import com.barberbot.api.repository.CustomerRepository;
import com.barberbot.api.repository.InteractionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.barberbot.api.model.WaitlistEntry;
import com.barberbot.api.repository.WaitlistRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class OrchestratorService {

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final InteractionRepository interactionRepository;
    private final OpenAIService openAIService;
    private final WhatsAppService whatsAppService;
    private final AgendaService agendaService;
    private final BarberBotProperties properties;
    private final WaitlistRepository waitlistRepository;

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class WaitlistOffer {
        private String phone;
        private String name;
        private String date;
        private String time;
        private String service;
    }

    private static final Map<String, LocalDateTime> processedMessageIds = new ConcurrentHashMap<>();
    private static final Map<String, String> adminStates = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> adminRetomarOptions = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> adminApagarOptions = new ConcurrentHashMap<>();
    private static final Map<String, String> customerStates = new ConcurrentHashMap<>();
    private static final Map<String, List<AgendaService.Agendamento>> customerCancelOptions = new ConcurrentHashMap<>();
    private static final Map<String, WaitlistOffer> waitlistOffers = new ConcurrentHashMap<>();
    private static final Map<String, String> adminScheduledMsg = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private void sendMsg(String phone, String text, String role) {
        log.info("[{} OUT] Enviando: '{}'", role, sanitizeForConsole(text.replace("\n", " ")));
        whatsAppService.sendTextMessage(phone, text);
    }

    @Scheduled(fixedRate = 600000)
    public void clearProcessedCache() {
        LocalDateTime limit = LocalDateTime.now().minusMinutes(20);
        processedMessageIds.entrySet().removeIf(entry -> entry.getValue().isBefore(limit));
    }



    @Async
    public void processWebhook(EvolutionWebhookDTO webhook) {
        try {
            if (shouldIgnoreMessage(webhook)) {
                log.debug("[IGNORED] Mensagem ignorada (do proprio bot, grupo ou sem numero valido).");
                return;
            }
            if (isDuplicateMessage(webhook)) {
                log.info("[DUP] Mensagem duplicada ignorada (ID: {}).", webhook.getData().getKey().getId());
                return;
            }

            String phoneNumber = webhook.getPhoneNumber();
            String pushName = webhook.getData() != null ? webhook.getData().getPushName() : "Desconhecido";
            String msgType = webhook.hasAudio() ? "AUDIO" :
                             webhook.hasImage() ? "IMAGEM" :
                             webhook.hasDocument() ? "DOCUMENTO" :
                             webhook.hasSticker() ? "STICKER" : "TEXTO";

            log.info("=======================================================================");
            log.info("[RECEBIDO] Nova mensagem de: {} ({}) | Tipo: {}", pushName, phoneNumber, msgType);

            if (isAdminNumber(phoneNumber)) {
                log.info("[SISTEMA] Remetente identificado como ADMINISTRADOR.");
                processAdminMessage(webhook, phoneNumber);
            } else {
                if (customerService.isCustomerPaused(phoneNumber)) {
                    log.info("[PAUSADO] O cliente {} esta pausado. Atendimento automatico suspenso.", phoneNumber);
                    log.info("=======================================================================");
                    return;
                }
                log.info("[CLIENTE] Remetente identificado como CLIENTE.");
                processCustomerMessage(webhook, phoneNumber);
            }
            log.info("=======================================================================");
        } catch (Exception e) {
            log.error("[ERRO FATAL ORCHESTRATOR] Falha critica ao processar webhook: {}", e.getMessage(), e);
        }
    }

    private boolean shouldIgnoreMessage(EvolutionWebhookDTO webhook) {
        if (webhook.getData() == null || webhook.getData().getKey() == null) return true;
        if (Boolean.TRUE.equals(webhook.getData().getKey().getFromMe())) return true;
        return webhook.isGroupChat() || webhook.getPhoneNumber() == null;
    }

    private boolean isDuplicateMessage(EvolutionWebhookDTO webhook) {
        String messageId = webhook.getData().getKey().getId();
        if (messageId != null) {
            if (processedMessageIds.putIfAbsent(messageId, LocalDateTime.now()) != null) return true;
            return interactionRepository.existsByMessageId(messageId);
        }
        return false;
    }

    private String normalizeBrazilianNumber(String rawNumber) {
        if (rawNumber == null) return "";
        String num = rawNumber.replaceAll("[^0-9]", "");
        if (num.startsWith("55") && num.length() >= 12) {
            num = num.substring(2);
        }
        if (num.length() == 11 && num.charAt(2) == '9') {
            num = num.substring(0, 2) + num.substring(3);
        }
        return num;
    }

    private String sanitizeForConsole(String text) {
        if (text == null) return "";
        String temp = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        temp = temp.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        temp = temp.replaceAll("[^\\p{ASCII}]", "");
        return temp.trim();
    }

    private boolean isAdminNumber(String phoneNumber) {
        String phoneNormalized = normalizeBrazilianNumber(phoneNumber);
        for (String adminPhone : properties.getAdmin().getPhones()) {
            if (normalizeBrazilianNumber(adminPhone).equals(phoneNormalized)) {
                return true;
            }
        }
        return false;
    }

    private String fetchBase64FromEvolution(String messageId) {
        try {
            String baseUrl = properties.getEvolution().getBaseUrl();
            if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            String url = baseUrl + "/chat/getBase64FromMediaMessage/" + properties.getEvolution().getInstanceName();
            String jsonPayload = String.format("{\"message\":{\"key\":{\"id\":\"%s\"}}}", messageId);

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("apikey", properties.getEvolution().getApiKey())
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            java.net.http.HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response.body());
                if (root.has("base64") && !root.get("base64").isNull()) return root.get("base64").asText();
            }
        } catch (Exception e) {}
        return null;
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
    private void processAdminMessage(EvolutionWebhookDTO webhook, String phoneNumber) {
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
            }
            
            String currentState = adminStates.get(phoneNumber);

            if (currentState != null) {
                if (cmdLower.contains("cancelar")) {
                    adminStates.remove(phoneNumber);
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
                            Customer c = customerRepository.findByPhoneNumber(targetPhone).orElse(null);
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
            } else if (cmdLower.equals("2") || (cmdLower.contains("aviso") && !cmdLower.contains("promocao"))) {
                adminStates.put(phoneNumber, "AVISO_BASE");
                sendMsg(phoneNumber, "📢 *Disparo de Avisos*\nEnvie a mensagem agora. _(Ou digite 'cancelar')_", "ADMIN");
            } else if (cmdLower.equals("3") || cmdLower.contains("promocao") || cmdLower.contains("prospec")) {
                adminStates.put(phoneNumber, "AVISO_PROSPEC");
                sendMsg(phoneNumber, "🎯 *Promoções do dia*\nEnvie a oferta que deseja disparar para a base toda.", "ADMIN");
            } else if (cmdLower.equals("4") || cmdLower.contains("pausa")) {
                adminStates.put(phoneNumber, "PAUSAR");
                sendMsg(phoneNumber, "⏸️ *Pausar Robô*\nDigite o telefone com DDD do cliente.", "ADMIN");
            } else if (cmdLower.equals("5") || cmdLower.contains("retoma")) {
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
                    sb.append("\nDigite o *NÚMERO DA OPÇÃO*.");
                    sendMsg(phoneNumber, sb.toString(), "ADMIN");
                }
            } else if (cmdLower.equals("6") || cmdLower.contains("agenda") || cmdLower.contains("painel")) {
                long total = customerRepository.count();
                long waitlistCount = waitlistRepository.count();
                long pausedCount = customerService.getPausedPhones().size();
                String painelAgendamentos = agendaService.getPainelAgendamentosDetalhado(total, waitlistCount, pausedCount);
                sendMsg(phoneNumber, painelAgendamentos, "ADMIN");
            } else if (cmdLower.equals("7") || cmdLower.contains("matinal") || cmdLower.contains("bom dia")) {
                int totalHoje = agendaService.getAgendamentosHojeCount();
                String agendaHoje = agendaService.getAgendaHojeText();
                String report = String.format("""
                        ☀️ *Relatório Matinal BarberBot*
                        
                        📅 *Hoje temos %d agendamento(s):*
                        
                        %s
                        
                        Eu cuidarei de lembrar todos eles para não faltarem! 🤖💈
                        """, totalHoje, agendaHoje);
                sendMsg(phoneNumber, report, "ADMIN");
            } else if (cmdLower.equals("8") || cmdLower.contains("noturno") || cmdLower.contains("fechamento")) {
                String resumoDetalhado = agendaService.getResumoDetelhadoDoDia();
                sendMsg(phoneNumber, resumoDetalhado, "ADMIN");
            } else if (cmdLower.equals("9") || cmdLower.contains("lista")) {
                List<Customer> customers = customerRepository.findAll();
                if (customers.isEmpty()) {
                    sendMsg(phoneNumber, "📭 Nenhum cliente salvo na base.", "ADMIN");
                } else {
                    StringBuilder sb = new StringBuilder("📋 *Base (" + customers.size() + ")*\n\n");
                    for (Customer c : customers) sb.append("👤 ").append(c.getName()).append(" - ").append(c.getPhoneNumber()).append("\n");
                    sendMsg(phoneNumber, sb.toString(), "ADMIN");
                }
            } else if (cmdLower.equals("10") || cmdLower.contains("apaga") || cmdLower.contains("exclui")) {
                List<Customer> customers = customerRepository.findAll();
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
                    sb.append("\nDigite o *NÚMERO DA OPÇÃO* para apagar.");
                    sendMsg(phoneNumber, sb.toString(), "ADMIN");
                }
            } else if (cmdLower.equals("11") || cmdLower.contains("ajuda") || cmdLower.contains("manual")) {
                String manual = """
                        📖 *MANUAL DO ADMINISTRADOR - BARBERBOT*
                        
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
                        - O botão de 'Avisos' (2) e 'Promoções' (3) dispara mensagem IMEDIATA para todos.
                        - O botão 'Agendar Disparo' (12) permite escrever um texto para disparar num dia e hora futuros.
                        - Pausar Robô (4) desliga a IA por 1 hora naquele número.
                        """;
                sendMsg(phoneNumber, manual, "ADMIN");
            } else if (cmdLower.equals("12") || cmdLower.contains("agendar disparo")) {
                adminStates.put(phoneNumber, "AGENDAR_DISPARO_MSG");
                sendMsg(phoneNumber, "📝 *Agendar Disparo*\nQual é a mensagem que deseja enviar para a base? (Digite 'cancelar' para abortar)", "ADMIN");
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
                
                *1* - 💰 Resumo Financeiro (Caixa)
                *2* - 📢 Avisos (Geral)
                *3* - 🎯 Promoções do dia
                *4* - ⏸️ Pausar bot (Cliente)
                *5* - ▶️ Retomar bot (Cliente)
                *6* - 📅 Painel de Agendamentos (Hoje)
                *7* - ☀️ Relatório Matinal (Hoje)
                *8* - 🌙 Relatório Noturno (Fechamento)
                *9* - 📋 Listar Clientes
                *10* - 🗑️ Apagar um Cliente
                *11* - ℹ️ Ajuda / Manual do Sistema
                *12* - 📅 Agendar Disparo
                
                _Digite o número ou comando desejado._
                """;
        sendMsg(phoneNumber, fullMenu, "ADMIN");
    }

    @Async
    public void performBroadcast(String adminPhone, String message, boolean isProspecting) {
        List<Customer> allCustomers = customerRepository.findAll();
        sendMsg(adminPhone, "🚀 Iniciando disparo para " + allCustomers.size() + " contatos...", "ADMIN");

        List<String> contatosSucesso = new ArrayList<>();
        for (Customer customer : allCustomers) {
            try {
                if (customer.getPhoneNumber().contains(adminPhone)) continue;
                String header = isProspecting ? "💈 *Barbearia Ficticia - Promoção* 💈\n\n" : "📢 *Aviso da Barbearia Ficticia*\n\n";
                sendMsg(customer.getPhoneNumber(), header + message, "DISPARO");
                contatosSucesso.add("✅ " + customer.getName());
                Thread.sleep(3000);
            } catch (Exception e) {}
        }
        sendMsg(adminPhone, "📊 *Relatório de Disparo*\nTotal alcançado: " + contatosSucesso.size() + "\n\n" + String.join("\n", contatosSucesso), "ADMIN");
    }

    // ==========================================
    // FLUXO DO CLIENTE
    // ==========================================
    private void processCustomerMessage(EvolutionWebhookDTO webhook, String phoneNumber) {
        if (webhook.hasSticker()) return;

        Customer customer = customerService.findOrCreateCustomer(phoneNumber, webhook.getData().getPushName());
        String contentToSave = webhook.getMessageText();

        if (webhook.hasAudio()) {
            String base64 = webhook.getBase64() != null ? webhook.getBase64() : fetchBase64FromEvolution(webhook.getData().getKey().getId());
            contentToSave = (base64 != null) ? openAIService.transcribeAudio(base64, webhook.getMimeType()) : "[Áudio]";
        } else if (contentToSave == null) {
            contentToSave = "[Arquivo]";
        }

        log.info("[CLIENTE IN] Mensagem: '{}'", sanitizeForConsole(contentToSave));
        interactionRepository.save(Interaction.builder().customer(customer).type(Interaction.InteractionType.USER).content(contentToSave).messageId(webhook.getData().getKey().getId()).build());

        String msgLower = contentToSave.toLowerCase().trim();
        String cState = customerStates.get(phoneNumber);

        // 1. Confirmação de Oferta da Lista de Espera
        if (waitlistOffers.containsKey(phoneNumber) && (msgLower.equals("sim") || msgLower.equals("s") || msgLower.contains("quero"))) {
            WaitlistOffer offer = waitlistOffers.remove(phoneNumber);
            agendaService.adicionarAgendamentoViaIA(offer.getName(), phoneNumber, offer.getDate(), offer.getTime(), offer.getService());
            
            String successMsg = "Perfeito, " + offer.getName().split(" ")[0] + "! 🥳\n\n" +
                                 "Seu horário foi agendado para *" + offer.getDate() + "* às *" + offer.getTime() + "* para *" + offer.getService() + "*! 🎉\n\n" +
                                 "*Como deseja pagar?*\n\n" +
                                 "1️⃣ PIX 💳\n" +
                                 "2️⃣ Presencialmente 💵\n\n" +
                                 "_Responda 1 ou 2. Ou digite *cancelar* para desistir._";
                                 
            customerStates.put(phoneNumber, "ESPERANDO_PAGAMENTO");
            saveAndSend(customer, successMsg, phoneNumber);
            return;
        }

        // 2. Estado: Esperando motivo de falar com atendente
        if ("ESPERANDO_MOTIVO".equals(cState)) {
            customerStates.remove(phoneNumber);
            customerService.pauseCustomer(phoneNumber, 15);
            for (String adm : properties.getAdmin().getPhones()) {
                sendMsg(adm, "🚨 *ATENDIMENTO*\n👤 " + customer.getName() + " (" + phoneNumber + ")\n💬 Motivo: " + contentToSave, "ADMIN");
            }
            saveAndSend(customer, "✅ Entendido! O responsável já foi avisado e vai te responder aqui em breve.\n_(Meu atendimento automático foi pausado por 15 minutos)_", phoneNumber);
            return;
        }

        // Tratamento do estado de escolha múltipla para cancelamento
        if ("ESPERANDO_CANCELAMENTO_MULTIPLO".equals(cState)) {
            List<AgendaService.Agendamento> apps = customerCancelOptions.get(phoneNumber);
            if (apps == null || apps.isEmpty()) {
                customerStates.remove(phoneNumber);
                saveAndSend(customer, "Sua sessão de cancelamento expirou. Tente novamente.", phoneNumber);
                return;
            }
            
            if (msgLower.contains("cancelar") || msgLower.contains("desistir") || msgLower.contains("sair") || msgLower.equals("0")) {
                customerStates.remove(phoneNumber);
                customerCancelOptions.remove(phoneNumber);
                saveAndSend(customer, "Operação cancelada! Nenhum agendamento foi excluído.", phoneNumber);
                return;
            }
            
            try {
                int index = Integer.parseInt(msgLower.trim()) - 1;
                if (index >= 0 && index < apps.size()) {
                    AgendaService.Agendamento ag = apps.get(index);
                    String diaSemana = null;
                    try {
                        String[] parts = ag.getDate().split("/");
                        int day = Integer.parseInt(parts[0].trim());
                        int month = Integer.parseInt(parts[1].trim());
                        int year = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).getYear();
                        java.time.LocalDate dateObj = java.time.LocalDate.of(year, month, day);
                        diaSemana = dateObj.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("pt", "BR"));
                    } catch (Exception e) {}
                    
                    String apptTime = ag.getTime();
                    String apptDate = ag.getDate();
                    String apptService = ag.getService();
                    
                    if (agendaService.cancelarAgendamentoExato(ag)) {
                        saveAndSend(customer, "✅ Seu agendamento de *" + apptDate + " às " + apptTime + "* foi cancelado com sucesso!", phoneNumber);
                        if (diaSemana != null) {
                            verificarListaEsperaECanalizar(diaSemana, apptDate, apptTime, apptService);
                        }
                    } else {
                        saveAndSend(customer, "❌ Não foi possível cancelar esse agendamento.", phoneNumber);
                    }
                    customerStates.remove(phoneNumber);
                    customerCancelOptions.remove(phoneNumber);
                    return;
                }
            } catch (NumberFormatException e) {
                // Ignore and fall through to ask again
            }
            
            if (msgLower.equals("menu") || msgLower.matches(".*(oi|ola|olá|bom dia|boa tarde|boa noite|tudo bem|fala|opa|salve|hey|hello|hi).*")) {
                customerStates.remove(phoneNumber);
                customerCancelOptions.remove(phoneNumber);
                // Não damos return! A mensagem flui normalmente para exibir o menu ou cumprimentar.
            } else {
                saveAndSend(customer, "Por favor, digite apenas o *NÚMERO* da opção que deseja cancelar, ou *0* para sair.", phoneNumber);
                return;
            }
        }

        // 3. Estado: Esperando escolha de pagamento (PIX / Presencial)
        //    Agora com escape: "cancelar" aborta o fluxo e cancela a pré-reserva.
        //    Só aceita "1" (PIX) e "2" (Presencial) explicitamente.
        //    Qualquer outra coisa re-pergunta em vez de forçar presencial.
        if ("ESPERANDO_PAGAMENTO".equals(cState)) {
            AgendaService.Agendamento ag = agendaService.getAgendamentoCliente(phoneNumber);
            
            // Escape: cancelar a pré-reserva
            if (msgLower.contains("cancelar") || msgLower.contains("desistir") || msgLower.contains("não quero") || msgLower.contains("nao quero")) {
                customerStates.remove(phoneNumber);
                agendaService.cancelarAgendamentoCliente(phoneNumber);
                interactionRepository.deleteByCustomerId(customer.getId());
                saveAndSend(customer, "❌ Agendamento cancelado! Se mudar de ideia, é só digitar *menu* para recomeçar. 😊", phoneNumber);
                return;
            }
            
            // PIX
            if (msgLower.equals("1") || msgLower.contains("pix")) {
                customerStates.remove(phoneNumber);
                interactionRepository.deleteByCustomerId(customer.getId());
                if (ag != null) {
                    ag.setPaymentStatus("PIX");
                    agendaService.salvarAgendaEmArquivo();
                }
                String pixMsg = "🔑 *CHAVE PIX (CNPJ):* `12.345.678/0001-90`\n\n" +
                                "📌 *PIX Copia e Cola:*\n" +
                                "`00020101021126580014br.gov.bcb.pix013612345678000190520400005303986540535.005802BR5918Barbearia Ficticia6009Sao Paulo62070503***63041A2B`\n\n" +
                                "💡 _Realize o pagamento e traga o comprovante no dia do atendimento. Seu horário está 100% garantido! Te esperamos lá!_ 💈👊\n\n" +
                                "_Para voltar ao início ou ver outras opções, digite *Menu*._";
                saveAndSend(customer, pixMsg, phoneNumber);
                return;
            }
            
            // 2. Presencial
            if (msgLower.equals("2") || msgLower.contains("presencial")) {
                customerStates.remove(phoneNumber);
                interactionRepository.deleteByCustomerId(customer.getId());
                if (ag != null) {
                    ag.setPaymentStatus("PRESENCIAL");
                    agendaService.salvarAgendaEmArquivo();
                }
                saveAndSend(customer, "Perfeito! Seu agendamento está confirmado para *pagamento presencial* direto no caixa da barbearia. Te esperamos lá! 💈👊\n\n_Para voltar ao início ou ver outras opções, digite *Menu*._", phoneNumber);
                return;
            }
            
            // Qualquer outra coisa: o cliente ignorou a pergunta (ex: mandou "obrigado").
            // Assumimos Presencial por padrão e removemos a trava para ele não ficar preso!
            customerStates.remove(phoneNumber);
            interactionRepository.deleteByCustomerId(customer.getId());
            if (ag != null) {
                ag.setPaymentStatus("PRESENCIAL");
                agendaService.salvarAgendaEmArquivo();
            }
            // NAO envia mensagem de erro e NAO da return! 
            // A mensagem continua o fluxo (ex: se ele disse 'obrigado', a IA vai responder cordialmente).
        }

        // 4. Comando "menu" explícito — SEMPRE funciona
        if (msgLower.equals("menu")) {
            log.info("[SISTEMA] Enviando Menu Textual para o Cliente: {}", phoneNumber);
            int points = customer.getFidelityPoints() != null ? customer.getFidelityPoints() : 0;
            saveAndSend(customer, MenuOptions.getMenuAsText(points), phoneNumber);
            return;
        }

        // 5. Saudações — exibe menu APENAS se não há conversa recente com a IA
        //    Isso evita que "Oi, me chamo João" quebre o fluxo de agendamento
        if (cState == null && !waitlistOffers.containsKey(phoneNumber) && !hasRecentIAConversation(customer.getId())) {
            if (msgLower.matches(".*(oi|ola|olá|bom dia|boa tarde|boa noite|tudo bem|fala|opa|salve|hey|hello|hi).*")) {
                log.info("[SISTEMA] Saudação detectada sem conversa ativa, enviando Menu para: {}", phoneNumber);
                int points = customer.getFidelityPoints() != null ? customer.getFidelityPoints() : 0;
                saveAndSend(customer, MenuOptions.getMenuAsText(points), phoneNumber);
                return;
            }
        }

        // 6. Comandos textuais explícitos por palavra-chave (NÃO por número isolado)
        if (msgLower.contains("meus agendamentos") || msgLower.contains("meu agendamento") || msgLower.contains("meu horário") || msgLower.contains("meu horario")) {
            trataVisualizarAgendamentos(customer, phoneNumber);
            return;
        }

        if (msgLower.equals("cancelar meu horário") || msgLower.equals("cancelar meu horario")) {
            trataCancelarAgendamento(customer, phoneNumber);
            return;
        }

        if (msgLower.equals("8") || msgLower.contains("falar com") || msgLower.contains("atendente")) {
            customerStates.put(phoneNumber, "ESPERANDO_MOTIVO");
            saveAndSend(customer, "Poxa, vi que você quer um atendimento humano! 🧔‍♂️\nPor favor, me explique brevemente a sua dúvida para eu já adiantar o assunto para o barbeiro:", phoneNumber);
            return;
        }

        // 7. Tudo o resto vai para a IA (incluindo números, nomes, horários, etc.)
        //    A IA tem o contexto completo da conversa e decide o que fazer.
        chamaIAETrataMensagem(customer, contentToSave, phoneNumber);
    }

    private void trataVisualizarAgendamentos(Customer customer, String phoneNumber) {
        List<AgendaService.Agendamento> apps = agendaService.getAgendamentosClienteList(phoneNumber);
        if (!apps.isEmpty()) {
            StringBuilder sb = new StringBuilder("📅 *Seus Agendamentos:*\n\n");
            for (AgendaService.Agendamento ag : apps) {
                sb.append("🔹 ").append(ag.getService()).append("\nDia: ").append(ag.getDate()).append(" às ").append(ag.getTime()).append("\n\n");
            }
            sb.append("❌ Para desmarcar, digite exatamente:\n*cancelar meu horário*");
            saveAndSend(customer, sb.toString(), phoneNumber);
        } else {
            saveAndSend(customer, "Você não possui nenhum agendamento ativo conosco no momento! Digite *1* para marcar um horário.", phoneNumber);
        }
    }

    private void trataExibirFidelidade(Customer customer, String phoneNumber) {
        int points = customer.getFidelityPoints() != null ? customer.getFidelityPoints() : 0;
        String resp = "🎫 *Seu Cartão Fidelidade - Barbearia Ficticia*\n\n" +
                      "Você possui *" + points + "* de *10* pontos! 🌟\n\n" +
                      "A cada corte você ganha 1 ponto. Ao completar 10 pontos, você ganha *10% de desconto* no seu próximo corte! ✂️🔥";
        saveAndSend(customer, resp, phoneNumber);
    }

    private void trataCancelarAgendamento(Customer customer, String phoneNumber) {
        List<AgendaService.Agendamento> apps = agendaService.getAgendamentosClienteList(phoneNumber);
        
        if (apps.isEmpty()) {
            saveAndSend(customer, "❌ Não encontrei nenhum agendamento seu ativo para cancelar.", phoneNumber);
            return;
        }
        
        if (apps.size() == 1) {
            AgendaService.Agendamento ag = apps.get(0);
            String diaSemana = null;
            try {
                String[] parts = ag.getDate().split("/");
                int day = Integer.parseInt(parts[0].trim());
                int month = Integer.parseInt(parts[1].trim());
                int year = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).getYear();
                java.time.LocalDate dateObj = java.time.LocalDate.of(year, month, day);
                diaSemana = dateObj.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("pt", "BR"));
            } catch (Exception e) {}

            String apptTime = ag.getTime();
            String apptDate = ag.getDate();
            String apptService = ag.getService();

            if (agendaService.cancelarAgendamentoExato(ag)) {
                saveAndSend(customer, "✅ Seu agendamento foi cancelado com sucesso!", phoneNumber);
                
                if (diaSemana != null) {
                    verificarListaEsperaECanalizar(diaSemana, apptDate, apptTime, apptService);
                }
            }
        } else {
            customerStates.put(phoneNumber, "ESPERANDO_CANCELAMENTO_MULTIPLO");
            customerCancelOptions.put(phoneNumber, apps);
            StringBuilder sb = new StringBuilder("Você tem mais de um agendamento ativo. Qual deles deseja cancelar?\n\n");
            for (int i = 0; i < apps.size(); i++) {
                sb.append("*").append(i + 1).append("* - ").append(apps.get(i).getDate()).append(" às ").append(apps.get(i).getTime()).append("\n");
            }
            sb.append("\nDigite o *NÚMERO* do agendamento que deseja cancelar (ou *0* para sair).");
            saveAndSend(customer, sb.toString(), phoneNumber);
        }
    }

    private void chamaIAETrataMensagem(Customer customer, String prompt, String phoneNumber) {
        log.info("[IA] Chamando o GPT-4o para processar a intencao do cliente.");
        String agendaContext = agendaService.getHorariosOcupadosParaIA();
        int activeAppointments = agendaService.getAgendamentosClienteList(phoneNumber).size();
        String aiResponse = openAIService.processCustomerMessage(prompt, getRecentHistory(customer.getId()), agendaContext, activeAppointments);

        if (aiResponse.contains("[NOVO_AGENDAMENTO|")) {
            try {
                int startIndex = aiResponse.indexOf("[NOVO_AGENDAMENTO|");
                int endIndex = aiResponse.indexOf("]", startIndex);
                String tag = aiResponse.substring(startIndex, endIndex + 1);

                String[] parts = tag.replace("[", "").replace("]", "").split("\\|");
                String name = parts[1];
                String date = parts[2];
                String time = parts[3];
                String service = parts[4];
                
                log.info("[IA] Tag de agendamento identificada! Cliente: {}, Data: {}, Hora: {}, Servico: {}", name, date, time, service);
                
                AgendaService.Agendamento existente = agendaService.getAgendamentoCliente(phoneNumber);
                boolean isDuplicado = existente != null && existente.getDate().equals(date) && existente.getTime().equals(time);

                // Remove a tag técnica da resposta antes de enviar
                aiResponse = aiResponse.replace(tag, "").trim();
                
                if (!isDuplicado) {
                    agendaService.adicionarAgendamentoViaIA(name, phoneNumber, date, time, service);

                    // Verifica se a IA já incluiu a pergunta de pagamento (novo prompt instrui isso).
                    // Se não incluiu, adiciona como fallback para garantir o fluxo.
                    if (!aiResponse.contains("PIX") && !aiResponse.contains("presencial") && !aiResponse.contains("pagamento")) {
                        aiResponse += "\n\n*Como deseja realizar o pagamento?*\n\n" +
                                      "1️⃣ Pagar agora via PIX 💳\n" +
                                      "2️⃣ Pagar presencialmente na barbearia 💵\n\n" +
                                      "_Responda com 1 ou 2._";
                    }
                    customerStates.put(phoneNumber, "ESPERANDO_PAGAMENTO");
                    log.info("[AGENDA] Estado ESPERANDO_PAGAMENTO definido para {}", phoneNumber);
                } else {
                    log.info("[AGENDA] Ignorando tag duplicada gerada pela IA, pois o cliente já possui este agendamento exato.");
                }
            } catch (Exception e) { log.error("[IA] Falha ao extrair ou criar agendamento da TAG NOVO_AGENDAMENTO: {}", e.getMessage(), e); }
        } else if (aiResponse.contains("[CANCELAR_AGENDAMENTO]")) {
            // IA solicitou cancelamento via conversa natural
            aiResponse = aiResponse.replace("[CANCELAR_AGENDAMENTO]", "").trim();
            boolean cancelled = agendaService.cancelarAgendamentoCliente(phoneNumber);
            if (cancelled) {
                log.info("[AGENDA] Cancelamento via IA executado para {}", phoneNumber);
            } else {
                log.info("[AGENDA] IA tentou cancelar mas nao havia agendamento para {}", phoneNumber);
            }
        } else if (aiResponse.contains("[LISTA_DE_ESPERA|")) {
            try {
                int startIndex = aiResponse.indexOf("[LISTA_DE_ESPERA|");
                int endIndex = aiResponse.indexOf("]", startIndex);
                String tag = aiResponse.substring(startIndex, endIndex + 1);

                String[] parts = tag.replace("[", "").replace("]", "").split("\\|");
                String name = parts[1];
                String preferredDay = parts[2];

                if (!waitlistRepository.existsByCustomerPhone(phoneNumber)) {
                    WaitlistEntry entry = WaitlistEntry.builder()
                            .customerPhone(phoneNumber)
                            .customerName(name)
                            .preferredDay(preferredDay)
                            .build();
                    waitlistRepository.save(entry);
                    log.info("[LISTA ESPERA] {} adicionado para o dia {}", phoneNumber, preferredDay);
                } else {
                    log.info("[LISTA ESPERA] {} ja estava cadastrado na lista", phoneNumber);
                }

                aiResponse = aiResponse.replace(tag, "").trim();
            } catch (Exception e) { log.error("[IA] Falha ao ler TAG LISTA_DE_ESPERA: {}", e.getMessage(), e); }
        }

        saveAndSend(customer, aiResponse, phoneNumber);
    }

    private void saveAndSend(Customer customer, String content, String phone) {
        interactionRepository.save(Interaction.builder().customer(customer).type(Interaction.InteractionType.BOT).content(content).build());
        sendMsg(phone, content, "CLIENTE");
    }

    private List<String> getRecentHistory(java.util.UUID customerId) {
        return interactionRepository.findRecentInteractionsByCustomerId(customerId).stream()
                .map(Interaction::getContent).collect(Collectors.toList());
    }

    /**
     * Verifica se o cliente tem uma conversa ativa recente com a IA (últimas 4+ interações nos últimos 5 min).
     * Isso evita que saudações como "Oi, me chamo João" disparem o menu no meio de um agendamento.
     */
    private boolean hasRecentIAConversation(java.util.UUID customerId) {
        List<Interaction> recent = interactionRepository.findRecentInteractionsByCustomerId(customerId);
        if (recent.size() < 4) return false;
        // Se temos pelo menos 4 interações recentes, checa se a mais recente do bot é de <5min
        Interaction lastBotMsg = recent.stream()
                .filter(i -> i.getType() == Interaction.InteractionType.BOT)
                .findFirst().orElse(null);
        if (lastBotMsg == null || lastBotMsg.getTimestamp() == null) return false;
        long diffMinutes = java.time.Duration.between(lastBotMsg.getTimestamp(), java.time.LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"))).toMinutes();
        return Math.abs(diffMinutes) <= 5;
    }

    private void verificarListaEsperaECanalizar(String diaSemana, String date, String time, String service) {
        try {
            List<WaitlistEntry> allEntries = waitlistRepository.findAll();
            WaitlistEntry matched = null;
            
            String cleanedDia = Normalizer.normalize(diaSemana.toLowerCase(), Normalizer.Form.NFD)
                    .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                    .replaceAll("[^a-z0-9]", "");

            for (WaitlistEntry entry : allEntries) {
                String preferred = entry.getPreferredDay().toLowerCase();
                String cleanedPref = Normalizer.normalize(preferred, Normalizer.Form.NFD)
                        .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                        .replaceAll("[^a-z0-9]", "");
                
                if (cleanedPref.contains(cleanedDia) || cleanedDia.contains(cleanedPref)) {
                    matched = entry;
                    break;
                }
            }
            
            if (matched != null) {
                WaitlistOffer offer = new WaitlistOffer(matched.getCustomerPhone(), matched.getCustomerName(), date, time, service);
                waitlistOffers.put(matched.getCustomerPhone(), offer);
                
                String capitalizeDia = diaSemana.substring(0, 1).toUpperCase() + diaSemana.substring(1);
                String notificationMsg = "Opa, " + matched.getCustomerName().split(" ")[0] + "! 💈\n\n" +
                                         "Um horário acaba de vagar para este *" + capitalizeDia + "* (" + date + ") às *" + time + "* para *" + service + "*! 🎉\n\n" +
                                         "Deseja reservar este horário para você?\n\n" +
                                         "Responda apenas **SIM** para confirmar o agendamento automático!";
                
                waitlistRepository.delete(matched);
                sendMsg(matched.getCustomerPhone(), notificationMsg, "SISTEMA");
                log.info("[LISTA ESPERA] Oferta enviada para {} para o dia {} às {}", matched.getCustomerPhone(), date, time);
            }
        } catch (Exception e) {
            log.error("[LISTA ESPERA] Erro ao verificar lista de espera: {}", e.getMessage(), e);
        }
    }
}