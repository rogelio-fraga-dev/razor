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

    private static final Map<String, LocalDateTime> processedMessageIds = new ConcurrentHashMap<>();
    private static final Map<String, String> adminStates = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> adminRetomarOptions = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> adminApagarOptions = new ConcurrentHashMap<>();
    private static final Map<String, String> customerStates = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private void sendMsg(String phone, String text, String role) {
        log.info("[{} OUT] Enviando: '{}'", role, text.replace("\n", " "));
        whatsAppService.sendTextMessage(phone, text);
    }

    @Scheduled(fixedRate = 600000)
    public void clearProcessedCache() {
        LocalDateTime limit = LocalDateTime.now().minusMinutes(20);
        processedMessageIds.entrySet().removeIf(entry -> entry.getValue().isBefore(limit));
    }

    @Scheduled(cron = "0 30 7 * * *", zone = "America/Sao_Paulo")
    public void dispararLembretesMatinais() {
        String adminPhone = properties.getAdmin().getPhone();
        sendMsg(adminPhone, "☀️ Bom dia, Chefe! O expediente vai começar. 🤖\n\n📅 *Agenda de Hoje:*\n" + agendaService.getAgendaSalva() + "\n\nEu cuidarei de lembrar todos eles para não faltarem!", "ADMIN");
    }

    @Scheduled(cron = "0 30 22 * * *", zone = "America/Sao_Paulo")
    public void fechamentoDiario() {
        String adminPhone = properties.getAdmin().getPhone();
        String resumo = agendaService.getResumoDoDia();
        sendMsg(adminPhone, "🌙 Boa noite, Chefe! Expediente finalizado.\n\n" + resumo + "\n\nBom descanso! 🤖", "ADMIN");
    }

    @Async
    public void processWebhook(EvolutionWebhookDTO webhook) {
        try {
            if (shouldIgnoreMessage(webhook)) return;
            if (isDuplicateMessage(webhook)) return;

            String phoneNumber = webhook.getPhoneNumber();
            log.info("=====================================================");
            log.info("[WEBHOOK] Msg recebida de: {}", phoneNumber);

            if (isAdminNumber(phoneNumber)) {
                log.info("[SISTEMA] Identificado como ADMINISTRADOR.");
                processAdminMessage(webhook, phoneNumber);
            } else {
                if (customerService.isCustomerPaused(phoneNumber)) {
                    log.info("[SISTEMA] Cliente {} está PAUSADO. Bot silenciado.", phoneNumber);
                    return;
                }
                log.info("[SISTEMA] Identificado como CLIENTE.");
                processCustomerMessage(webhook, phoneNumber);
            }
            log.info("=====================================================");
        } catch (Exception e) {
            log.error("[ERRO FATAL ORCHESTRATOR] {}", e.getMessage(), e);
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

    private boolean isAdminNumber(String phoneNumber) {
        String adminEnv = properties.getAdmin().getPhone().replaceAll("[^0-9]", "");
        String phoneIn = phoneNumber.replaceAll("[^0-9]", "");
        if (adminEnv.isEmpty()) return false;
        String adminBase = adminEnv.startsWith("55") ? adminEnv.substring(2) : adminEnv;
        String phoneBase = phoneIn.startsWith("55") ? phoneIn.substring(2) : phoneIn;
        return adminBase.equals(phoneBase);
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
                sendMsg(phoneNumber, "⏳ Lendo a base CSV...", "ADMIN");
                try {
                    int salvos = customerService.importCustomersFromCsvBase64(base64);
                    sendMsg(phoneNumber, "✅ Importação Concluída! *" + salvos + "* clientes na base.", "ADMIN");
                } catch (Exception e) { sendMsg(phoneNumber, "❌ Erro no CSV.", "ADMIN"); }
            } else {
                sendMsg(phoneNumber, "❌ O arquivo chegou corrompido.", "ADMIN");
            }
            return;
        }

        if (webhook.hasImage()) {
            adminStates.remove(phoneNumber);
            String base64 = webhook.getBase64() != null ? webhook.getBase64() : fetchBase64FromEvolution(webhook.getData().getKey().getId());
            if (base64 != null) {
                sendMsg(phoneNumber, "⏳ Lendo imagem da agenda com IA...", "ADMIN");
                try {
                    String agendaJson = openAIService.extractAgendaFromImage(base64, webhook.getMimeType());
                    int salvos = agendaService.processAgenda(agendaJson);
                    sendMsg(phoneNumber, "✅ " + salvos + " clientes identificados na imagem!", "ADMIN");
                } catch (Exception e) { sendMsg(phoneNumber, "❌ Erro na Visão IA.", "ADMIN"); }
            } else {
                sendMsg(phoneNumber, "❌ A imagem não pôde ser decodificada.", "ADMIN");
            }
            return;
        }

        if (webhook.hasAudio()) {
            String base64 = webhook.getBase64() != null ? webhook.getBase64() : fetchBase64FromEvolution(webhook.getData().getKey().getId());
            if (base64 == null) return;
            sendMsg(phoneNumber, "🎧 Ouvindo áudio...", "ADMIN");
            command = openAIService.transcribeAudio(base64, webhook.getMimeType());
            log.info("[ADMIN IN] Áudio transcrito: '{}'", command);
            
            if (!adminStates.containsKey(phoneNumber) && command != null && !isSystemCommand(command)) {
                sendMsg(phoneNumber, "📝 *Transcrição Livre:*\n" + command, "ADMIN");
                return;
            }
        } else {
            command = webhook.getMessageText();
            log.info("[ADMIN IN] Mensagem de texto: '{}'", command);
        }

        if (command != null) {
            String originalCommand = command.trim();
            String normalized = Normalizer.normalize(originalCommand.toLowerCase(), Normalizer.Form.NFD)
                    .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
            String cmdLower = normalized.replaceAll("[^a-z0-9 ]", "").trim();
            
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
            }

            if (cmdLower.contains("comando") || cmdLower.contains("menu") || cmdLower.contains("ajuda")) {
                sendFullMenu(phoneNumber);
            } else if (cmdLower.equals("1") || cmdLower.contains("dashboard") || cmdLower.contains("resumo")) {
                long total = customerRepository.count();
                String resumoCaixa = agendaService.getResumoDoDia();
                sendMsg(phoneNumber, "📊 *Painel de Controle BarberBot*\n\n👥 Clientes na Base: " + total + "\n🤖 Status IA: Online 🟢\n\n" + resumoCaixa, "ADMIN");
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
            } else if (cmdLower.equals("6") || cmdLower.contains("agenda")) {
                sendMsg(phoneNumber, "📅 *Agenda Atual*\n\n" + agendaService.getAgendaSalva(), "ADMIN");
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
            } else {
                if (!webhook.hasAudio()) {
                    sendMsg(phoneNumber, "Fala, Chefe! 💈 Digite *menu* para ver as opções.", "ADMIN");
                }
            }
        }
    }

    private void sendFullMenu(String phoneNumber) {
        String fullMenu = """
                🛠️ *PAINEL DA BARBEARIA*
                
                *1* - 📊 Dashboard e Caixa
                *2* - 📢 Avisos (Geral)
                *3* - 🎯 Promoções do dia
                *4* - ⏸️ Pausar bot (Cliente)
                *5* - ▶️ Retomar bot (Cliente)
                *6* - 📅 Ver Agenda Completa
                *9* - 📋 Listar Clientes
                *10* - 🗑️ Apagar um Cliente
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
                String header = isProspecting ? "💈 *Barbearia Vintage - Promoção* 💈\n\n" : "📢 *Aviso da Barbearia Vintage*\n\n";
                sendMsg(customer.getPhoneNumber(), header + message, "DISPARO");
                contatosSucesso.add("✅ " + customer.getName());
                Thread.sleep(3000);
            } catch (Exception e) {}
        }
        sendMsg(adminPhone, "📊 *Relatório de Disparo*\nTotal alcançado: " + contatosSucesso.size() + "\n\n" + String.join("\n", contatosSucesso), "ADMIN");
    }

    // ==========================================
    // FLUXO DO CLIENTE (COM MENU VISUAL + IA)
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

        log.info("[CLIENTE IN] Mensagem: '{}'", contentToSave);
        interactionRepository.save(Interaction.builder().customer(customer).type(Interaction.InteractionType.USER).content(contentToSave).messageId(webhook.getData().getKey().getId()).build());

        String msgLower = contentToSave.toLowerCase().trim();
        String cState = customerStates.get(phoneNumber);
        
        if ("ESPERANDO_MOTIVO".equals(cState)) {
            customerStates.remove(phoneNumber);
            customerService.pauseCustomer(phoneNumber, 15);
            sendMsg(properties.getAdmin().getPhone(), "🚨 *ATENDIMENTO*\n👤 " + customer.getName() + " (" + phoneNumber + ")\n💬 Motivo: " + contentToSave, "ADMIN");
            saveAndSend(customer, "✅ Entendido! O responsável já foi avisado e vai te responder aqui em breve.\n_(Meu atendimento automático foi pausado por 15 minutos)_", phoneNumber);
            return;
        }

        if (msgLower.equals("menu") || msgLower.matches("^(oi|olá|ola|bom dia|boa tarde|boa noite|tudo bem)$")) {
            String menuCli = """
                    💈 *Bem-vindo à Barbearia Vintage!* 💈
                    
                    Eu sou o assistente virtual. Digite o número da opção desejada para eu te ajudar rapidinho:
                    
                    *1* - ✂️ Agendar um Horário
                    *2* - 📅 Meus Agendamentos (Ver/Cancelar)
                    *3* - 🕒 Ver Horários Disponíveis
                    *4* - 📍 Localização e Endereço
                    *5* - 📱 Nossas Redes Sociais
                    *6* - ℹ️ Informações Úteis (Preços e Horários)
                    *7* - 👤 Falar com Atendente Humano
                    """;
            saveAndSend(customer, menuCli, phoneNumber);
            return;
        }

        if (msgLower.equals("2") || msgLower.contains("meus agendamentos") || msgLower.contains("meu agendamento")) {
            AgendaService.Agendamento ag = agendaService.getAgendamentoCliente(phoneNumber);
            if (ag != null) {
                saveAndSend(customer, "📅 *Seu Agendamento:*\n\nServiço: " + ag.getService() + "\nDia: " + ag.getDate() + " às " + ag.getTime() + "\n\n❌ Para desmarcar, digite exatamente:\n*cancelar meu horário*", phoneNumber);
            } else {
                saveAndSend(customer, "Você não possui nenhum agendamento ativo conosco no momento! Digite *1* para marcar um horário.", phoneNumber);
            }
            return;
        }

        if (msgLower.equals("cancelar meu horário") || msgLower.equals("cancelar meu horario")) {
            if (agendaService.cancelarAgendamentoCliente(phoneNumber)) {
                saveAndSend(customer, "✅ Seu agendamento foi cancelado com sucesso!", phoneNumber);
            } else {
                saveAndSend(customer, "❌ Não encontrei nenhum agendamento seu ativo para cancelar.", phoneNumber);
            }
            return;
        }

        if (msgLower.equals("4") || msgLower.contains("localização") || msgLower.contains("onde fica")) {
            saveAndSend(customer, "📍 *Nossa Localização*\n\nEstamos na Av. Fictícia, 1000 - Bairro Nobre, Araguari, MG.\n🔗 Veja a rota no Maps: https://maps.app.goo.gl/link-ficticio", phoneNumber);
            return;
        }

        if (msgLower.equals("5") || msgLower.contains("instagram") || msgLower.contains("redes")) {
            saveAndSend(customer, "📱 *Nossas Redes*\n\nAcompanhe os melhores cortes no nosso Instagram!\n👉 @barbeariavintage_oficial (https://instagram.com/barbearia)", phoneNumber);
            return;
        }

        if (msgLower.equals("6") || msgLower.contains("informações") || msgLower.contains("preço")) {
            saveAndSend(customer, "ℹ️ *Informações Úteis*\n\n*Horário de Funcionamento:*\nSegunda a Sábado: 09h às 19h\n\n*Serviços:*\n- Corte: R$ 35,00\n- Barba: R$ 30,00\n- Combo: R$ 60,00\n\nDigite *1* para agendar um horário com a gente!", phoneNumber);
            return;
        }

        if (msgLower.equals("7") || msgLower.contains("falar com") || msgLower.contains("atendente")) {
            customerStates.put(phoneNumber, "ESPERANDO_MOTIVO");
            saveAndSend(customer, "Poxa, vi que você quer um atendimento humano! 🧔‍♂️\nPor favor, me explique brevemente a sua dúvida para eu já adiantar o assunto para o barbeiro:", phoneNumber);
            return;
        }

        // --- GATILHOS DA IA CORRIGIDOS ---
        if (msgLower.equals("1")) {
            contentToSave = "Quero agendar um horário. O que você precisa saber?";
        } else if (msgLower.equals("3")) {
            contentToSave = "Quais os horários livres para agendar?";
        }

        log.info("[IA PROCESSING] Chamando o GPT-4o...");
        String agendaContext = agendaService.getHorariosOcupadosParaIA();
        String aiResponse = openAIService.processCustomerMessage(contentToSave, getRecentHistory(customer.getId()), agendaContext);

        if (aiResponse.contains("[NOVO_AGENDAMENTO|")) {
            try {
                int startIndex = aiResponse.indexOf("[NOVO_AGENDAMENTO|");
                int endIndex = aiResponse.indexOf("]", startIndex);
                String tag = aiResponse.substring(startIndex, endIndex + 1);

                String[] parts = tag.replace("[", "").replace("]", "").split("\\|");
                agendaService.adicionarAgendamentoViaIA(parts[1], phoneNumber, parts[2], parts[3], parts[4]);

                aiResponse = aiResponse.replace(tag, "").trim();
            } catch (Exception e) { log.error("Falha ao ler TAG", e); }
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
}