package com.razor.api.service;

import com.razor.api.ai.AiIntent;
import com.razor.api.ai.AiTagParser;
import com.razor.api.config.RazorProperties;
import com.razor.api.dto.EvolutionWebhookDTO;
import com.razor.api.model.Appointment;
import com.razor.api.model.Customer;
import com.razor.api.model.Interaction;
import com.razor.api.model.PaymentStatus;
import com.razor.api.model.WaitlistEntry;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.InteractionRepository;
import com.razor.api.repository.WaitlistRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import com.razor.api.util.ConsoleText;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Fluxo do CLIENTE: menu numerico, saudacoes, agendamento/cancelamento via IA,
 * escolha de pagamento (PIX/presencial), lista de espera e a maquina de estados
 * da conversa. Extraido do {@code OrchestratorService} (Fase 0.5).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class CustomerConversationService {

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final InteractionRepository interactionRepository;
    private final OpenAIService openAIService;
    private final AgendaService agendaService;
    private final RazorProperties properties;
    private final WaitlistRepository waitlistRepository;
    private final AppointmentRepository appointmentRepository;
    private final WhatsAppMessenger messenger;
    private final EvolutionMediaService evolutionMediaService;
    private final ProfessionalService professionalService;
    private final TenantAdminService tenantAdminService;
    private final BarbershopSettingsService barbershopSettingsService;

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class WaitlistOffer {
        private String phone;
        private String name;
        private String date;
        private String time;
        private String service;
    }

    /** Agendamento aguardando a escolha do profissional (agenda multiprofissional). */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class PendingBooking {
        private String name;
        private String date;
        private String time;
        private String service;
        private List<com.razor.api.model.Professional> options; // alinhado 1:1 com os numeros exibidos
    }

    // Estado por-cliente (chaveado por telefone). Instancia, nao static: o servico e um
    // singleton (mesmo comportamento em runtime) e assim cada teste tem estado isolado.
    private final Map<String, String> customerStates = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> customerStatesTimestamps = new ConcurrentHashMap<>();
    private final Map<String, List<Appointment>> customerCancelOptions = new ConcurrentHashMap<>();
    private final Map<String, WaitlistOffer> waitlistOffers = new ConcurrentHashMap<>();
    private final Map<String, PendingBooking> pendingBookings = new ConcurrentHashMap<>();
    private final AiTagParser aiTagParser = new AiTagParser();

    private void sendMsg(String phone, String text, String role) {
        messenger.send(phone, text, role);
    }

    private String sanitizeForConsole(String text) {
        return ConsoleText.sanitize(text);
    }

    private String fetchBase64FromEvolution(String messageId) {
        return evolutionMediaService.fetchBase64(messageId);
    }

    private void setCustomerState(String phone, String state) {
        customerStates.put(phone, state);
        customerStatesTimestamps.put(phone, LocalDateTime.now());
    }

    private void clearCustomerState(String phone) {
        customerStates.remove(phone);
        customerStatesTimestamps.remove(phone);
        pendingBookings.remove(phone);
    }

    /** Limpa estados zumbi abandonados (>30min). Antes vivia no clearProcessedCache do Orchestrator. */
    @Scheduled(fixedRate = 600000)
    public void clearZombieCustomerStates() {
        LocalDateTime stateLimit = LocalDateTime.now().minusMinutes(30);
        customerStatesTimestamps.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(stateLimit)) {
                clearCustomerState(entry.getKey());
                return true;
            }
            return false;
        });
    }

    // ==========================================
    // FLUXO DO CLIENTE
    // ==========================================
    public void processCustomerMessage(EvolutionWebhookDTO webhook, String phoneNumber) {
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
        interactionRepository.save(Interaction.builder().barbershopId(customer.getBarbershopId()).customer(customer).type(Interaction.InteractionType.USER).content(contentToSave).messageId(webhook.getData().getKey().getId()).build());

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

            setCustomerState(phoneNumber, "ESPERANDO_PAGAMENTO");
            saveAndSend(customer, successMsg, phoneNumber);
            return;
        }

        // 2. Estado: Esperando motivo de falar com atendente
        if ("ESPERANDO_MOTIVO".equals(cState)) {
            clearCustomerState(phoneNumber);
            customerService.pauseCustomer(phoneNumber, 15);
            for (String adm : tenantAdminService.adminPhones()) {
                sendMsg(adm, "🚨 *ATENDIMENTO*\n👤 " + customer.getName() + " (" + phoneNumber + ")\n💬 Motivo: " + contentToSave, "ADMIN");
            }
            saveAndSend(customer, "✅ Entendido! O responsável já foi avisado e vai te responder aqui em breve.\n_(Meu atendimento automático foi pausado por 15 minutos)_", phoneNumber);
            agendaService.agendarNotificacaoRetorno(phoneNumber, 15);
            return;
        }

        // Tratamento do estado de escolha múltipla para cancelamento
        if ("ESPERANDO_CANCELAMENTO_MULTIPLO".equals(cState)) {
            List<Appointment> apps = customerCancelOptions.get(phoneNumber);
            if (apps == null || apps.isEmpty()) {
                clearCustomerState(phoneNumber);
                saveAndSend(customer, "Sua sessão de cancelamento expirou. Tente novamente.", phoneNumber);
                return;
            }

            if (msgLower.contains("cancelar") || msgLower.contains("desistir") || msgLower.contains("sair") || msgLower.equals("0")) {
                clearCustomerState(phoneNumber);
                customerCancelOptions.remove(phoneNumber);
                saveAndSend(customer, "Operação cancelada! Nenhum agendamento foi excluído.", phoneNumber);
                return;
            }

            try {
                int index = Integer.parseInt(msgLower.trim()) - 1;
                if (index >= 0 && index < apps.size()) {
                    Appointment ag = apps.get(index);
                    String diaSemana = ag.getAppointmentDate() != null
                            ? ag.getAppointmentDate().getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("pt", "BR"))
                            : null;

                    String apptTime = AppointmentDates.formatTime(ag.getAppointmentTime());
                    String apptDate = AppointmentDates.formatDate(ag.getAppointmentDate());
                    String apptService = ag.getService();

                    if (agendaService.cancelarAgendamentoExato(ag)) {
                        saveAndSend(customer, "✅ Seu agendamento de *" + apptDate + " às " + apptTime + "* foi cancelado com sucesso!", phoneNumber);
                        if (diaSemana != null) {
                            verificarListaEsperaECanalizar(diaSemana, apptDate, apptTime, apptService);
                        }
                    } else {
                        saveAndSend(customer, "❌ Não foi possível cancelar esse agendamento.", phoneNumber);
                    }
                    clearCustomerState(phoneNumber);
                    customerCancelOptions.remove(phoneNumber);
                    return;
                }
            } catch (NumberFormatException e) {
                // Ignore and fall through to ask again
            }

            if (msgLower.equals("menu") || msgLower.matches(".*(oi|ola|olá|bom dia|boa tarde|boa noite|tudo bem|fala|opa|salve|hey|hello|hi).*")) {
                clearCustomerState(phoneNumber);
                customerCancelOptions.remove(phoneNumber);
                // Não damos return! A mensagem flui normalmente para exibir o menu ou cumprimentar.
            } else {
                saveAndSend(customer, "Por favor, digite apenas o *NÚMERO* da opção que deseja cancelar, ou *0* para sair.", phoneNumber);
                return;
            }
        }

        // 3. Estado: Esperando escolha de pagamento (PIX / Presencial)
        if ("ESPERANDO_PAGAMENTO".equals(cState)) {
            Appointment ag = agendaService.getAgendamentoCliente(phoneNumber);

            // Escape: cancelar a pré-reserva
            if (msgLower.contains("cancelar") || msgLower.contains("desistir") || msgLower.contains("não quero") || msgLower.contains("nao quero")) {
                clearCustomerState(phoneNumber);
                agendaService.cancelarAgendamentoCliente(phoneNumber);
                // Histórico mantido para a IA ter contexto de cancelamento
                saveAndSend(customer, "❌ Agendamento cancelado! Se mudar de ideia, é só digitar *menu* para recomeçar. 😊", phoneNumber);
                return;
            }

            // PIX
            if (msgLower.equals("1") || msgLower.contains("pix")) {
                clearCustomerState(phoneNumber);
                // Sem apagar o histórico! Correção do Bug 1
                if (ag != null) {
                    ag.setPaymentStatus(PaymentStatus.PIX);
                    appointmentRepository.save(ag);
                }
                var cfg = barbershopSettingsService.resolve();
                String pixMsg;
                if (cfg.hasPix()) {
                    StringBuilder sb = new StringBuilder("🔑 *CHAVE PIX:* `").append(cfg.pixKey()).append("`\n\n");
                    if (cfg.pixCopyPaste() != null && !cfg.pixCopyPaste().isBlank()) {
                        sb.append("📌 *PIX Copia e Cola:*\n`").append(cfg.pixCopyPaste()).append("`\n\n");
                    }
                    sb.append("💡 _Realize o pagamento e traga o comprovante no dia do atendimento. ")
                      .append("Seu horário está 100% garantido! Te esperamos lá!_ 💈👊\n\n")
                      .append("_Para voltar ao início ou ver outras opções, digite *Menu*._");
                    pixMsg = sb.toString();
                } else {
                    // Sem PIX configurado pelo dono: NUNCA inventar uma chave — orienta o cliente.
                    pixMsg = "😕 O PIX ainda não está disponível por aqui.\n\n" +
                             "Sem problema: você pode pagar *presencialmente* no dia do atendimento. " +
                             "Seu horário está garantido! 💈👊\n\n" +
                             "_Para voltar ao início, digite *Menu*._";
                }
                saveAndSend(customer, pixMsg, phoneNumber);
                return;
            }

            // 2. Presencial
            if (msgLower.equals("2") || msgLower.contains("presencial")) {
                clearCustomerState(phoneNumber);
                if (ag != null) {
                    ag.setPaymentStatus(PaymentStatus.PRESENCIAL);
                    appointmentRepository.save(ag);
                }
                saveAndSend(customer, "Perfeito! Seu agendamento está confirmado para *pagamento presencial* direto no caixa da barbearia. Te esperamos lá! 💈👊\n\n_Para voltar ao início ou ver outras opções, digite *Menu*._", phoneNumber);
                return;
            }

            // Qualquer outra coisa: o cliente ignorou a pergunta (ex: mandou "obrigado").
            // Assumimos Presencial por padrão e removemos a trava para ele não ficar preso!
            clearCustomerState(phoneNumber);
            if (ag != null) {
                ag.setPaymentStatus(PaymentStatus.PRESENCIAL);
                appointmentRepository.save(ag);
            }
            saveAndSend(customer, "Tudo certo! Confirmamos seu pagamento como *presencial* direto no caixa. Muito obrigado! 💈👊", phoneNumber);
            return;
        }

        // 3.5. Estado: Esperando escolha do profissional (agenda multiprofissional)
        if ("ESPERANDO_PROFISSIONAL".equals(cState)) {
            trataEscolhaProfissional(customer, phoneNumber, msgLower);
            return;
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

        // 6. Tratamento das Opções do Menu Numérico e Palavras-chave
        if (msgLower.equals("1") || msgLower.equals("agendar") || msgLower.equals("agendar horario")) {
            contentToSave = "Quero agendar um horário."; // IA assume
        } else if ((msgLower.equals("2") || msgLower.contains("meus agendamentos") || msgLower.contains("meu agendamento") || msgLower.contains("meu horário") || msgLower.contains("meu horario"))
                && !msgLower.contains("cancelar")) {
            // Guard: "cancelar meu horário" contem "meu horário"; sem o !contains("cancelar")
            // a opcao 2 (ver agendamentos) interceptava o comando de cancelamento e ele nunca funcionava.
            trataVisualizarAgendamentos(customer, phoneNumber);
            return;
        } else if (msgLower.equals("3") || msgLower.contains("horarios disponiveis") || msgLower.contains("horários disponíveis")) {
            contentToSave = "Quais os horários disponíveis?"; // IA assume
        } else if (msgLower.equals("4") || msgLower.contains("endereco") || msgLower.contains("endereço") || msgLower.contains("localizacao") || msgLower.contains("localização")) {
            BarbershopSettingsService.BotSettings bot = barbershopSettingsService.resolve();
            String endMsg = bot.addressText() + "\n\n📍 Mapa: " + bot.addressMapsUrl();
            saveAndSend(customer, endMsg, phoneNumber);
            return;
        } else if (msgLower.equals("5") || msgLower.contains("servicos") || msgLower.contains("serviços") || msgLower.contains("precos") || msgLower.contains("preços") || msgLower.contains("planos vip")) {
            saveAndSend(customer, barbershopSettingsService.resolve().servicesText(), phoneNumber);
            return;
        } else if (msgLower.equals("6") || msgLower.contains("cartao fidelidade") || msgLower.contains("cartão fidelidade") || msgLower.contains("fidelidade")) {
            int points = customer.getFidelityPoints() != null ? customer.getFidelityPoints() : 0;
            String fidMsg = "💳 *Seu Cartão Fidelidade Digital*\n\nVocê tem *" + points + "* pontos acumulados!\n(Lembre-se: A cada 10 pontos você ganha 10% de desconto!)";
            saveAndSend(customer, fidMsg, phoneNumber);
            return;
        } else if (msgLower.equals("7") || msgLower.contains("instagram")) {
            saveAndSend(customer, "📸 Siga a gente no Instagram para acompanhar nosso trabalho!\n\n" + barbershopSettingsService.resolve().instagramUrl(), phoneNumber);
            return;
        } else if (msgLower.equals("cancelar meu horário") || msgLower.equals("cancelar meu horario")) {
            trataCancelarAgendamento(customer, phoneNumber);
            return;
        } else if (msgLower.equals("8") || msgLower.contains("falar com") || msgLower.contains("atendente")) {
            setCustomerState(phoneNumber, "ESPERANDO_MOTIVO");
            saveAndSend(customer, "Poxa, vi que você quer um atendimento humano! 🧔‍♂️\nPor favor, me explique brevemente a sua dúvida para eu já adiantar o assunto para o barbeiro:", phoneNumber);
            return;
        }

        // 7. Tudo o resto vai para a IA (incluindo intenções traduzidas)
        // A IA tem o contexto completo da conversa e decide o que fazer.
        chamaIAETrataMensagem(customer, contentToSave, phoneNumber);
    }

    private void trataVisualizarAgendamentos(Customer customer, String phoneNumber) {
        List<Appointment> apps = agendaService.getAgendamentosClienteList(phoneNumber);
        if (!apps.isEmpty()) {
            StringBuilder sb = new StringBuilder("📅 *Seus Agendamentos:*\n\n");
            for (Appointment ag : apps) {
                sb.append("🔹 ").append(ag.getService()).append("\nDia: ").append(AppointmentDates.formatDate(ag.getAppointmentDate())).append(" às ").append(AppointmentDates.formatTime(ag.getAppointmentTime())).append("\n\n");
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
        List<Appointment> apps = agendaService.getAgendamentosClienteList(phoneNumber);

        if (apps.isEmpty()) {
            saveAndSend(customer, "❌ Não encontrei nenhum agendamento seu ativo para cancelar.", phoneNumber);
            return;
        }

        if (apps.size() == 1) {
            Appointment ag = apps.get(0);
            String diaSemana = ag.getAppointmentDate() != null
                    ? ag.getAppointmentDate().getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("pt", "BR"))
                    : null;

            String apptTime = AppointmentDates.formatTime(ag.getAppointmentTime());
            String apptDate = AppointmentDates.formatDate(ag.getAppointmentDate());
            String apptService = ag.getService();

            if (agendaService.cancelarAgendamentoExato(ag)) {
                saveAndSend(customer, "✅ Seu agendamento foi cancelado com sucesso!", phoneNumber);

                if (diaSemana != null) {
                    verificarListaEsperaECanalizar(diaSemana, apptDate, apptTime, apptService);
                }
            }
        } else {
            setCustomerState(phoneNumber, "ESPERANDO_CANCELAMENTO_MULTIPLO");
            customerCancelOptions.put(phoneNumber, apps);
            StringBuilder sb = new StringBuilder("Você tem mais de um agendamento ativo. Qual deles deseja cancelar?\n\n");
            for (int i = 0; i < apps.size(); i++) {
                sb.append("*").append(i + 1).append("* - ").append(AppointmentDates.formatDate(apps.get(i).getAppointmentDate())).append(" às ").append(AppointmentDates.formatTime(apps.get(i).getAppointmentTime())).append("\n");
            }
            sb.append("\nDigite o *NÚMERO* do agendamento que deseja cancelar (ou *0* para sair).");
            saveAndSend(customer, sb.toString(), phoneNumber);
        }
    }

    private void chamaIAETrataMensagem(Customer customer, String prompt, String phoneNumber) {
        log.info("[IA] Chamando o GPT-4o para processar a intencao do cliente.");
        String agendaContext = agendaService.getHorariosOcupadosParaIA();
        int activeAppointments = agendaService.getAgendamentosClienteList(phoneNumber).size();
        String knownName = (customer.getName() != null && !customer.getName().isBlank()) ? customer.getName() : "Desconhecido";
        String aiResponse = openAIService.processCustomerMessage(prompt, getRecentHistory(customer.getId()), agendaContext, activeAppointments, knownName);

        AiIntent intent = aiTagParser.parse(aiResponse);
        String responseToSend;

        if (intent instanceof AiIntent.NovoAgendamento novoAgendamento) {
            responseToSend = tratarNovoAgendamento(customer, phoneNumber, novoAgendamento);
        } else if (intent instanceof AiIntent.Cancelar cancelar) {
            responseToSend = cancelar.cleanedResponse();
            boolean cancelled = agendaService.cancelarAgendamentoCliente(phoneNumber);
            if (cancelled) {
                log.info("[AGENDA] Cancelamento via IA executado para {}", phoneNumber);
            } else {
                log.info("[AGENDA] IA tentou cancelar mas nao havia agendamento para {}", phoneNumber);
            }
        } else if (intent instanceof AiIntent.ListaDeEspera listaDeEspera) {
            responseToSend = tratarListaDeEspera(phoneNumber, listaDeEspera);
        } else {
            responseToSend = intent.cleanedResponse();
        }

        saveAndSend(customer, responseToSend, phoneNumber);
    }

    private String tratarNovoAgendamento(Customer customer, String phoneNumber, AiIntent.NovoAgendamento na) {
        String name = na.name();
        String date = na.date();
        String time = na.time();
        String service = na.service();

        log.info("[IA] Tag de agendamento identificada! Cliente: {}, Data: {}, Hora: {}, Servico: {}", name, date, time, service);

        java.time.LocalDate parsedDate;
        java.time.LocalTime parsedTime;
        try {
            parsedDate = AppointmentDates.parseDate(date);
            parsedTime = AppointmentDates.parseTime(time);
        } catch (Exception e) {
            parsedDate = null;
            parsedTime = null;
        }
        final java.time.LocalDate naDate = parsedDate;
        final java.time.LocalTime naTime = parsedTime;

        List<Appointment> apps = agendaService.getAgendamentosClienteList(phoneNumber);
        boolean isDuplicado = naDate != null && naTime != null && apps.stream()
                .anyMatch(a -> naDate.equals(a.getAppointmentDate()) && naTime.equals(a.getAppointmentTime()));

        if (isDuplicado) {
            log.info("[AGENDA] Ignorando tag duplicada gerada pela IA, pois o cliente ja possui este agendamento exato.");
            return "Tudo certo! Seu agendamento para o dia " + date + " às " + time + " já está garantido no sistema. Te esperamos lá!\n\n_Para voltar ao início ou ver outras opções, digite *Menu*._";
        }

        // Atualiza o nome do cliente no banco de dados para a IA "lembrar" dele no futuro
        if (name != null && !name.isBlank()) {
            customer.setName(name);
            customerRepository.save(customer);
        }

        // Agenda multiprofissional: com mais de um barbeiro ativo, o cliente escolhe com quem
        // quer se atender. Com 0 ou 1 barbeiro, mantemos a auto-atribuição imediata (padrão).
        List<com.razor.api.model.Professional> ativos = professionalService.listActive();
        if (ativos != null && ativos.size() > 1) {
            pendingBookings.put(phoneNumber, new PendingBooking(name, date, time, service, ativos));
            setCustomerState(phoneNumber, "ESPERANDO_PROFISSIONAL");
            log.info("[AGENDA] Estado ESPERANDO_PROFISSIONAL definido para {} ({} barbeiros)", phoneNumber, ativos.size());
            return montarPerguntaProfissional(date, time, ativos);
        }

        agendaService.adicionarAgendamentoViaIA(name, phoneNumber, date, time, service);

        // Verifica se a IA já incluiu a pergunta de pagamento (novo prompt instrui isso).
        // Se não incluiu, adiciona como fallback para garantir o fluxo.
        String response = anexarPerguntaPagamento(na.cleanedResponse());
        setCustomerState(phoneNumber, "ESPERANDO_PAGAMENTO");
        log.info("[AGENDA] Estado ESPERANDO_PAGAMENTO definido para {}", phoneNumber);
        return response;
    }

    /** Anexa a pergunta de pagamento ao texto se a IA ainda não a incluiu (fallback do fluxo). */
    private String anexarPerguntaPagamento(String response) {
        if (response.contains("PIX") || response.contains("presencial") || response.contains("pagamento")) {
            return response;
        }
        return response + "\n\n*Como deseja realizar o pagamento?*\n\n" +
                "1️⃣ Pagar agora via PIX 💳\n" +
                "2️⃣ Pagar presencialmente na barbearia 💵\n\n" +
                "_Responda com 1 ou 2._";
    }

    /** Monta a pergunta "com qual barbeiro?" listando os profissionais ativos numerados. */
    private String montarPerguntaProfissional(String date, String time, List<com.razor.api.model.Professional> ativos) {
        StringBuilder sb = new StringBuilder("Quase lá! Para *" + date + "* às *" + time + "*, com qual profissional você prefere se atender? 💈\n\n");
        for (int i = 0; i < ativos.size(); i++) {
            sb.append("*").append(i + 1).append("* - ").append(ativos.get(i).getName()).append("\n");
        }
        sb.append("\n_Responda o *número*, ou digite *tanto faz* para o primeiro disponível._");
        return sb.toString();
    }

    /** Trata a resposta do cliente no estado ESPERANDO_PROFISSIONAL e conclui o agendamento. */
    private void trataEscolhaProfissional(Customer customer, String phoneNumber, String msgLower) {
        PendingBooking pend = pendingBookings.get(phoneNumber);
        if (pend == null) {
            clearCustomerState(phoneNumber);
            saveAndSend(customer, "Sua sessão de agendamento expirou. Digite *menu* para recomeçar.", phoneNumber);
            return;
        }

        // Escape: desistir do agendamento
        if (msgLower.contains("cancelar") || msgLower.contains("desistir") || msgLower.equals("0")) {
            clearCustomerState(phoneNumber);
            saveAndSend(customer, "Sem problemas! Agendamento não concluído. Digite *menu* quando quiser recomeçar. 😊", phoneNumber);
            return;
        }

        java.util.UUID escolhido = null;
        String nomeEscolhido = null;

        boolean tantoFaz = msgLower.contains("tanto faz") || msgLower.contains("qualquer")
                || msgLower.contains("indiferente") || msgLower.contains("primeiro");
        if (!tantoFaz) {
            try {
                int index = Integer.parseInt(msgLower.trim()) - 1;
                if (index >= 0 && index < pend.getOptions().size()) {
                    com.razor.api.model.Professional p = pend.getOptions().get(index);
                    escolhido = p.getId();
                    nomeEscolhido = p.getName();
                } else {
                    saveAndSend(customer, "Não encontrei essa opção. Digite o *número* de um dos profissionais, ou *tanto faz*.", phoneNumber);
                    return;
                }
            } catch (NumberFormatException e) {
                saveAndSend(customer, "Não entendi. Digite apenas o *número* do profissional, ou *tanto faz*.", phoneNumber);
                return;
            }
        }

        finalizarAgendamentoMultiprofissional(customer, phoneNumber, pend, escolhido, nomeEscolhido);
    }

    /** Conclui o agendamento com o profissional escolhido e avança para o pagamento. */
    private void finalizarAgendamentoMultiprofissional(Customer customer, String phoneNumber, PendingBooking pend,
                                                       java.util.UUID escolhido, String nomeEscolhido) {
        agendaService.adicionarAgendamentoViaIA(pend.getName(), phoneNumber, pend.getDate(), pend.getTime(), pend.getService(), escolhido);
        pendingBookings.remove(phoneNumber);

        String comQuem = nomeEscolhido != null ? "com *" + nomeEscolhido + "* " : "";
        String confirm = "Fechado! Seu horário " + comQuem + "para *" + pend.getDate() + "* às *" + pend.getTime() + "* está reservado. 🎉";
        String response = anexarPerguntaPagamento(confirm);
        setCustomerState(phoneNumber, "ESPERANDO_PAGAMENTO");
        log.info("[AGENDA] Agendamento multiprofissional concluído para {}; estado ESPERANDO_PAGAMENTO", phoneNumber);
        saveAndSend(customer, response, phoneNumber);
    }

    private String tratarListaDeEspera(String phoneNumber, AiIntent.ListaDeEspera le) {
        if (!waitlistRepository.existsByBarbershopIdAndCustomerPhone(TenantContext.getCurrent(), phoneNumber)) {
            WaitlistEntry entry = WaitlistEntry.builder()
                    .barbershopId(TenantContext.getCurrent())
                    .customerPhone(phoneNumber)
                    .customerName(le.name())
                    .preferredDay(le.preferredDay())
                    .build();
            waitlistRepository.save(entry);
            log.info("[LISTA ESPERA] {} adicionado para o dia {}", phoneNumber, le.preferredDay());
        } else {
            log.info("[LISTA ESPERA] {} ja estava cadastrado na lista", phoneNumber);
        }
        return le.cleanedResponse();
    }

    private void saveAndSend(Customer customer, String content, String phone) {
        interactionRepository.save(Interaction.builder().barbershopId(customer.getBarbershopId()).customer(customer).type(Interaction.InteractionType.BOT).content(content).build());
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
            List<WaitlistEntry> allEntries = waitlistRepository.findByBarbershopId(TenantContext.getCurrent());
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
