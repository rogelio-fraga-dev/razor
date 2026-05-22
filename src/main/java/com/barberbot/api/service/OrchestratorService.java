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
    private static final Map<String, WaitlistOffer> waitlistOffers = new ConcurrentHashMap<>();
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
        int totalHoje = agendaService.getAgendamentosHojeCount();
        String agendaHoje = agendaService.getAgendaHojeText();
        String report = String.format("""
                ☀️ *Bom dia, Chefe! O expediente vai começar.* 🤖💈
                
                📅 *Hoje temos %d agendamento(s):*
                
                %s
                
                Eu cuidarei de lembrar todos eles para não faltarem!
                """, totalHoje, agendaHoje);
        sendMsg(adminPhone, report, "ADMIN");
    }

    @Scheduled(cron = "0 30 22 * * *", zone = "America/Sao_Paulo")
    public void fechamentoDiario() {
        String adminPhone = properties.getAdmin().getPhone();
        String resumoDetalhado = agendaService.getResumoDetelhadoDoDia();
        sendMsg(adminPhone, resumoDetalhado, "ADMIN");
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
            } else {
                if (!webhook.hasAudio()) {
                    sendMsg(phoneNumber, "Fala, Chefe! 💈 Digite *menu* para ver as opções.", "ADMIN");
                }
            }
        }
    }

    private void sendFullMenu(String phoneNumber) {
        try {
            log.info("[SISTEMA] Enviando Lista Interativa de Gestão para o Administrador: {}", phoneNumber);
            whatsAppService.sendListMessage(
                phoneNumber,
                "🛠️ Painel Administrativo",
                "Olá, Administrador! Escolha uma das ações ou relatórios abaixo:",
                "Ver Opções 📋",
                "Barbearia Vintage - Gestão",
                MenuOptions.buildAdminListSections()
            );
        } catch (Exception e) {
            log.warn("[SISTEMA] Falha ao enviar lista interativa para admin, usando fallback em texto: {}", e.getMessage());
            String fullMenu = """
                    🛠️ *PAINEL DA BARBEARIA*
                    
                    *1* - 📊 Dashboard e Caixa
                    *2* - 📢 Avisos (Geral)
                    *3* - 🎯 Promoções do dia
                    *4* - ⏸️ Pausar bot (Cliente)
                    *5* - ▶️ Retomar bot (Cliente)
                    *6* - 📅 Ver Agenda Completa
                    *7* - ☀️ Relatório Matinal (Hoje)
                    *8* - 🌙 Relatório Noturno (Fechamento)
                    *9* - 📋 Listar Clientes
                    *10* - 🗑️ Apagar um Cliente
                    
                    _Digite o número ou comando desejado._
                    """;
            sendMsg(phoneNumber, fullMenu, "ADMIN");
        }
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

        // 1. Confirmação de Oferta da Lista de Espera
        if (waitlistOffers.containsKey(phoneNumber) && (msgLower.equals("sim") || msgLower.equals("s") || msgLower.contains("quero"))) {
            WaitlistOffer offer = waitlistOffers.remove(phoneNumber);
            agendaService.adicionarAgendamentoViaIA(offer.getName(), phoneNumber, offer.getDate(), offer.getTime(), offer.getService());
            
            String successMsg = "Perfeito, " + offer.getName().split(" ")[0] + "! 🥳\n\n" +
                                 "Seu horário foi agendado automaticamente para *" + offer.getDate() + "* às *" + offer.getTime() + "* para *" + offer.getService() + "*! 🎉\n\n" +
                                 "*Como deseja realizar o pagamento?*\n\n" +
                                 "1️⃣ **Pagar agora via PIX** 💳\n" +
                                 "2️⃣ **Pagar presencialmente** na barbearia 💵\n\n" +
                                 "_Responda com o número da opção desejada (1 ou 2)._";
                                 
            customerStates.put(phoneNumber, "ESPERANDO_PAGAMENTO");
            saveAndSend(customer, successMsg, phoneNumber);
            return;
        }

        // 2. Estado: Esperando motivo de falar com atendente
        if ("ESPERANDO_MOTIVO".equals(cState)) {
            customerStates.remove(phoneNumber);
            customerService.pauseCustomer(phoneNumber, 15);
            sendMsg(properties.getAdmin().getPhone(), "🚨 *ATENDIMENTO*\n👤 " + customer.getName() + " (" + phoneNumber + ")\n💬 Motivo: " + contentToSave, "ADMIN");
            saveAndSend(customer, "✅ Entendido! O responsável já foi avisado e vai te responder aqui em breve.\n_(Meu atendimento automático foi pausado por 15 minutos)_", phoneNumber);
            return;
        }

        // 3. Estado: Esperando escolha de pagamento (PIX / Presencial)
        if ("ESPERANDO_PAGAMENTO".equals(cState)) {
            customerStates.remove(phoneNumber);
            AgendaService.Agendamento ag = agendaService.getAgendamentoCliente(phoneNumber);
            
            if (msgLower.equals("1") || msgLower.contains("pix") || msgLower.contains("agora") || msgLower.contains("pagar")) {
                if (ag != null) {
                    ag.setPaymentStatus("PIX");
                    agendaService.salvarAgendaEmArquivo();
                }
                String pixMsg = "🔑 *CHAVE PIX (CNPJ):* `12.345.678/0001-90`\n\n" +
                                "📌 *PIX Copia e Cola:*\n" +
                                "`00020101021126580014br.gov.bcb.pix013612345678000190520400005303986540535.005802BR5918Barbearia Vintage6009Sao Paulo62070503***63041A2B`\n\n" +
                                "💡 _Realize o pagamento e traga o comprovante no dia do atendimento. Seu horário está 100% garantido! Te esperamos lá!_ 💈👊";
                saveAndSend(customer, pixMsg, phoneNumber);
                return;
            } else {
                if (ag != null) {
                    ag.setPaymentStatus("PRESENCIAL");
                    agendaService.salvarAgendaEmArquivo();
                }
                String presMsg = "Perfeito! Seu agendamento foi configurado para **pagamento presencial** direto no caixa da barbearia. Te esperamos lá! 💈👊";
                saveAndSend(customer, presMsg, phoneNumber);
                return;
            }
        }

        // 4. Interceptação de Menus Interativos (Botões WhatsApp)
        String rowId = MenuOptions.resolveMenuOptionId(contentToSave);
        if (rowId != null) {
            log.info("[SISTEMA] Menu Interativo ativado para cliente {}: {}", phoneNumber, rowId);
            
            if (MenuOptions.ROW_ID_ATENDENTE.equals(rowId)) {
                customerStates.put(phoneNumber, "ESPERANDO_MOTIVO");
                saveAndSend(customer, "Poxa, vi que você quer um atendimento humano! 🧔‍♂️\nPor favor, me explique brevemente a sua dúvida para eu já adiantar o assunto para o barbeiro:", phoneNumber);
            } else if (MenuOptions.ROW_ID_AGENDAR.equals(rowId)) {
                String promptParaIA = "Quero agendar um horário. O que você precisa saber?";
                chamaIAETrataMensagem(customer, promptParaIA, phoneNumber);
            } else if (MenuOptions.ROW_ID_MEUS_AGENDAMENTOS.equals(rowId)) {
                trataVisualizarAgendamentos(customer, phoneNumber);
            } else if (MenuOptions.ROW_ID_HORARIOS_LIVRES.equals(rowId)) {
                String promptParaIA = "Quais os horários livres para agendar?";
                chamaIAETrataMensagem(customer, promptParaIA, phoneNumber);
            } else if (MenuOptions.ROW_ID_FIDELIDADE.equals(rowId)) {
                trataExibirFidelidade(customer, phoneNumber);
            } else {
                String response = MenuOptions.getResponseForOption(rowId, properties);
                saveAndSend(customer, response, phoneNumber);
            }
            return;
        }

        // 5. Exibição de Menu Principal Textual
        if (msgLower.equals("menu") || msgLower.matches("^(oi|olá|ola|bom dia|boa tarde|boa noite|tudo bem)$")) {
            try {
                log.info("[SISTEMA] Enviando Lista Interativa Oficial para: {}", phoneNumber);
                whatsAppService.sendListMessage(
                    phoneNumber,
                    "💈 Barbearia Vintage 💈",
                    "Olá! Sou o assistente virtual da Barbearia Vintage. Como posso te ajudar hoje?\n\nEscolha uma das opções abaixo no botão:",
                    "Ver Opções 📋",
                    "Barbearia Vintage - Centro",
                    MenuOptions.buildListSections()
                );
            } catch (Exception e) {
                log.warn("[SISTEMA] Falha ao enviar lista interativa, usando fallback em texto: {}", e.getMessage());
                saveAndSend(customer, MenuOptions.getMenuAsText(), phoneNumber);
            }
            return;
        }

        // 6. Tratamento de Comandos Textuais / Números Fallback
        if (msgLower.equals("2") || msgLower.contains("meus agendamentos") || msgLower.contains("meu agendamento")) {
            trataVisualizarAgendamentos(customer, phoneNumber);
            return;
        }

        if (msgLower.equals("cancelar meu horário") || msgLower.equals("cancelar meu horario")) {
            trataCancelarAgendamento(customer, phoneNumber);
            return;
        }

        if (msgLower.equals("4") || msgLower.contains("localização") || msgLower.contains("onde fica")) {
            String resp = properties.getMenu().getAddressText() + "\n\n🗺️ *Abrir no Maps:* " + properties.getMenu().getAddressMapsUrl();
            saveAndSend(customer, resp, phoneNumber);
            return;
        }

        if (msgLower.equals("5") || msgLower.contains("preço") || msgLower.contains("preços") || msgLower.contains("serviços")) {
            String resp = properties.getMenu().getServicesText() + "\n👉 " + properties.getMenu().getScheduleUrl();
            saveAndSend(customer, resp, phoneNumber);
            return;
        }

        if (msgLower.equals("6") || msgLower.contains("fidelidade") || msgLower.contains("cartão") || msgLower.contains("pontos")) {
            trataExibirFidelidade(customer, phoneNumber);
            return;
        }

        if (msgLower.equals("7") || msgLower.contains("instagram") || msgLower.contains("redes")) {
            String resp = "📸 *Siga nossa Barbearia!*\n\nFique por dentro dos cortes e novidades:\n" + properties.getMenu().getInstagramUrl();
            saveAndSend(customer, resp, phoneNumber);
            return;
        }

        if (msgLower.equals("8") || msgLower.contains("falar com") || msgLower.contains("atendente")) {
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

        chamaIAETrataMensagem(customer, contentToSave, phoneNumber);
    }

    private void trataVisualizarAgendamentos(Customer customer, String phoneNumber) {
        AgendaService.Agendamento ag = agendaService.getAgendamentoCliente(phoneNumber);
        if (ag != null) {
            saveAndSend(customer, "📅 *Seu Agendamento:*\n\nServiço: " + ag.getService() + "\nDia: " + ag.getDate() + " às " + ag.getTime() + "\n\n❌ Para desmarcar, digite exatamente:\n*cancelar meu horário*", phoneNumber);
        } else {
            saveAndSend(customer, "Você não possui nenhum agendamento ativo conosco no momento! Digite *1* para marcar um horário.", phoneNumber);
        }
    }

    private void trataExibirFidelidade(Customer customer, String phoneNumber) {
        int points = customer.getFidelityPoints() != null ? customer.getFidelityPoints() : 0;
        String resp = "🎫 *Seu Cartão Fidelidade - Barbearia Vintage*\n\n" +
                      "Você possui *" + points + "* de *10* pontos! 🌟\n\n" +
                      "A cada corte você ganha 1 ponto. Ao completar 10 pontos, você ganha *10% de desconto* no seu próximo corte! ✂️🔥";
        saveAndSend(customer, resp, phoneNumber);
    }

    private void trataCancelarAgendamento(Customer customer, String phoneNumber) {
        AgendaService.Agendamento ag = agendaService.getAgendamentoCliente(phoneNumber);
        if (ag != null) {
            String diaSemana = null;
            try {
                String[] parts = ag.getDate().split("/");
                int day = Integer.parseInt(parts[0].trim());
                int month = Integer.parseInt(parts[1].trim());
                int year = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).getYear();
                java.time.LocalDate dateObj = java.time.LocalDate.of(year, month, day);
                diaSemana = dateObj.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("pt", "BR"));
            } catch (Exception e) {
                log.warn("Erro ao deduzir dia da semana para cancelamento: {}", e.getMessage());
            }

            String apptTime = ag.getTime();
            String apptDate = ag.getDate();
            String apptService = ag.getService();

            if (agendaService.cancelarAgendamentoCliente(phoneNumber)) {
                saveAndSend(customer, "✅ Seu agendamento foi cancelado com sucesso!", phoneNumber);
                
                if (diaSemana != null) {
                    verificarListaEsperaECanalizar(diaSemana, apptDate, apptTime, apptService);
                }
            }
        } else {
            saveAndSend(customer, "❌ Não encontrei nenhum agendamento seu ativo para cancelar.", phoneNumber);
        }
    }

    private void chamaIAETrataMensagem(Customer customer, String prompt, String phoneNumber) {
        log.info("[IA PROCESSING] Chamando o GPT-4o...");
        String agendaContext = agendaService.getHorariosOcupadosParaIA();
        String aiResponse = openAIService.processCustomerMessage(prompt, getRecentHistory(customer.getId()), agendaContext);

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
                
                agendaService.adicionarAgendamentoViaIA(name, phoneNumber, date, time, service);

                aiResponse = aiResponse.replace(tag, "").trim();
                
                String paymentPrompt = "\n\n*Como deseja realizar o pagamento?*\n\n" +
                                       "1️⃣ **Pagar agora via PIX** 💳\n" +
                                       "2️⃣ **Pagar presencialmente** na barbearia 💵\n\n" +
                                       "_Responda com o número da opção desejada (1 ou 2)._";
                aiResponse = aiResponse + paymentPrompt;
                customerStates.put(phoneNumber, "ESPERANDO_PAGAMENTO");
            } catch (Exception e) { log.error("Falha ao ler TAG NOVO_AGENDAMENTO", e); }
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
                    log.info("[LISTA ESPERA] {} já estava na lista", phoneNumber);
                }

                aiResponse = aiResponse.replace(tag, "").trim();
            } catch (Exception e) { log.error("Falha ao ler TAG LISTA_DE_ESPERA", e); }
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