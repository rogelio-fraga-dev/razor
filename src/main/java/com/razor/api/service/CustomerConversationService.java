package com.razor.api.service;

import com.razor.api.ai.AiIntent;
import com.razor.api.ai.AiTagParser;
import com.razor.api.config.RazorProperties;
import com.razor.api.dto.EvolutionWebhookDTO;
import com.razor.api.model.Appointment;
import com.razor.api.model.Customer;
import com.razor.api.model.Interaction;
import com.razor.api.model.PaymentStatus;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.InteractionRepository;
import com.razor.api.repository.WaitlistRepository;
import com.razor.api.state.StateKeys;
import com.razor.api.state.StateStore;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import com.razor.api.util.ConsoleText;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    /** Plano da barbearia (piloto sem cartão fidelidade). Opcional: nulo nos testes = liberado. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PlanFeatures planFeatures;
    private final AgendaService agendaService;
    private final RazorProperties properties;
    private final WaitlistRepository waitlistRepository;
    private final AppointmentRepository appointmentRepository;
    private final WhatsAppMessenger messenger;
    private final EvolutionMediaService evolutionMediaService;
    private final ProfessionalService professionalService;
    private final TenantAdminService tenantAdminService;
    private final BarbershopSettingsService barbershopSettingsService;
    private final PlanPricingService planPricingService;
    private final StateStore stateStore;
    private final WaitlistOfferService waitlistOfferService;
    private final AppointmentConfirmationService confirmationService;
    private final NoShowDepositService noShowDepositService;
    private final ProfessionalAssignmentService professionalAssignmentService;
    private final CustomerReviewService customerReviewService;
    private final PricingService pricingService;
    private final CustomerPreferencesService customerPreferencesService;
    /** Eventos para o filtro de conversas do console (V51); nulo nos testes. */
    private final com.razor.api.platform.ConversationEventRecorder conversationEvents;

    // Publicas + construtor vazio: sao serializadas em JSON pelo StateStore.

    /** Agendamento aguardando a escolha do profissional (agenda multiprofissional). */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class PendingBooking {
        private String name;
        private String date;
        private String time;
        private String service;
        private List<ProfessionalOption> options; // alinhado 1:1 com os numeros exibidos
    }

    /**
     * O minimo do barbeiro que a escolha precisa guardar: id e o nome <b>como o bot o chama</b>
     * (apelido, V37). Guardar a entidade inteira no estado da conversa levaria junto a foto em
     * base64 (~40KB) para o Redis a cada agendamento.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ProfessionalOption {
        private java.util.UUID id;
        private String name;

        static ProfessionalOption of(com.razor.api.model.Professional p) {
            return new ProfessionalOption(p.getId(), p.displayName());
        }
    }

    /** Quanto tempo o "digitando..." fica no WhatsApp enquanto o audio e processado / a IA pensa. */
    static final int AUDIO_PRESENCE_MS = 8000;
    static final int AI_PRESENCE_MS = 5000;

    /** Conversa abandonada morre sozinha (antes: varredura de estados "zumbi"). */
    private static final Duration CONV_TTL = Duration.ofMinutes(30);

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
        // O TTL substitui a antiga varredura de estados zumbi: conversa parada expira sozinha.
        stateStore.put(StateKeys.convState(phone), state, CONV_TTL);
    }

    private String getCustomerState(String phone) {
        return stateStore.get(StateKeys.convState(phone), String.class).orElse(null);
    }

    private void clearCustomerState(String phone) {
        stateStore.remove(StateKeys.convState(phone));
        stateStore.remove(StateKeys.pendingBooking(phone));
        // Fluxo encerrado: o proximo agendamento comeca perguntando o servico de novo.
        stateStore.remove(StateKeys.chosenService(phone));
    }

    // ==========================================
    // FLUXO DO CLIENTE
    // ==========================================
    public void processCustomerMessage(EvolutionWebhookDTO webhook, String phoneNumber) {
        if (webhook.hasSticker()) return;

        Customer customer = customerService.findOrCreateCustomer(phoneNumber, webhook.getData().getPushName());
        String contentToSave = webhook.getMessageText();

        if (webhook.hasAudio()) {
            // O cliente ve "digitando..." enquanto o audio e baixado, transcrito e respondido.
            messenger.presence(phoneNumber, "composing", AUDIO_PRESENCE_MS);
            long t0 = System.nanoTime();
            // So baixa da Evolution se o webhook nao trouxe o base64 (sem download duplicado).
            boolean embutido = webhook.getBase64() != null;
            String base64 = embutido ? webhook.getBase64() : fetchBase64FromEvolution(webhook.getData().getKey().getId());
            long t1 = System.nanoTime();
            String transcription = (base64 != null) ? openAIService.transcribeAudio(base64, webhook.getMimeType()) : null;
            log.info("[TEMPO] Audio de {}: download={} ms ({}), transcricao={} ms", phoneNumber,
                    (t1 - t0) / 1_000_000, embutido ? "base64 no webhook" : "Evolution", (System.nanoTime() - t1) / 1_000_000);
            if (transcription == null) {
                log.warn("[CLIENTE IN] Audio de {} nao pode ser transcrito", phoneNumber);
                interactionRepository.save(Interaction.builder().barbershopId(customer.getBarbershopId()).customer(customer).type(Interaction.InteractionType.USER).content("[Áudio não transcrito]").messageId(webhook.getData().getKey().getId()).build());
                saveAndSend(customer, "Opa, não consegui ouvir seu áudio agora 😅 Pode me mandar por escrito?", phoneNumber);
                return;
            }
            contentToSave = transcription;
        } else if ((contentToSave == null || contentToSave.isBlank()) && (webhook.hasImage() || webhook.hasDocument())) {
            // Foto/arquivo sem legenda: resposta fixa. Antes ia para a IA como "[Arquivo]" e ela
            // chegou a responder "O atendimento foi cancelado" (pente fino de 25/11).
            interactionRepository.save(Interaction.builder().barbershopId(customer.getBarbershopId()).customer(customer).type(Interaction.InteractionType.USER).content("[Arquivo]").messageId(webhook.getData().getKey().getId()).build());
            saveAndSend(customer, FILE_RECEIVED_MSG, phoneNumber);
            return;
        } else if (contentToSave == null || contentToSave.isBlank()) {
            log.info("[CLIENTE IN] Mensagem vazia de {} ignorada", phoneNumber);
            return;
        }

        log.info("[CLIENTE IN] Mensagem: '{}'", sanitizeForConsole(contentToSave));
        interactionRepository.save(Interaction.builder().barbershopId(customer.getBarbershopId()).customer(customer).type(Interaction.InteractionType.USER).content(contentToSave).messageId(webhook.getData().getKey().getId()).build());

        String msgLower = contentToSave.toLowerCase().trim();
        String cState = getCustomerState(phoneNumber);
        // ---- Respostas curtas tratadas ANTES da IA, so com a conversa ociosa (no meio de um fluxo
        //      — pagamento, escolha de barbeiro — a resposta e daquele fluxo). Ordem:
        //      1. "sim"/"não" a uma vaga da lista de espera (V47): pergunta com prazo de minutos, a
        //         mais recente; nao usa "1"/"2", que ficam com a confirmacao da vespera;
        //      2. nota do pedido de avaliacao (V46) — o "parar" dado ali tambem e o opt-out geral;
        //      3. opt-out/opt-in ("parar", "sair", "quero receber") e aniversario (V47);
        //      4. confirmacao da vespera ("1 confirma / 2 desmarca", V42).
        boolean ofertaAberta = false;
        if (cState == null) {
            var vaga = waitlistOfferService.handleReply(phoneNumber, contentToSave);
            if (vaga != null && vaga.isPresent()) {
                tratarRespostaListaEspera(customer, phoneNumber, vaga.get());
                return;
            }
            ofertaAberta = waitlistOfferService.hasOpenOffer(phoneNumber);
        }

        if (cState == null) {
            boolean confirmacaoAberta = stateStore.get(StateKeys.confirmation(phoneNumber), Object.class).isPresent();
            var nota = customerReviewService.handleReply(phoneNumber, contentToSave, confirmacaoAberta);
            if (nota != null && nota.isPresent()) {
                saveAndSend(customer, nota.get(), phoneNumber);
                return;
            }
            var pref = customerPreferencesService.handleOptOut(customer, contentToSave);
            if (pref == null || pref.isEmpty()) pref = customerPreferencesService.handleBirthday(customer, contentToSave);
            if (pref != null && pref.isPresent()) {
                saveAndSend(customer, pref.get(), phoneNumber);
                return;
            }
            var resposta = confirmationService.handleReply(phoneNumber, contentToSave);
            if (resposta.isPresent()) {
                saveAndSend(customer, resposta.get(), phoneNumber);
                return;
            }
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
            List<Appointment> apps = stateStore
                    .getList(StateKeys.cancelOptions(phoneNumber), Appointment.class).orElse(null);
            if (apps == null || apps.isEmpty()) {
                clearCustomerState(phoneNumber);
                saveAndSend(customer, "Sua sessão de cancelamento expirou. Tente novamente.", phoneNumber);
                return;
            }

            if (msgLower.contains("cancelar") || msgLower.contains("desistir") || msgLower.contains("sair") || msgLower.equals("0")) {
                clearCustomerState(phoneNumber);
                stateStore.remove(StateKeys.cancelOptions(phoneNumber));
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
                        waitlistOfferService.offerFreedSlot(ag);
                    } else {
                        saveAndSend(customer, "❌ Não foi possível cancelar esse agendamento.", phoneNumber);
                    }
                    clearCustomerState(phoneNumber);
                    stateStore.remove(StateKeys.cancelOptions(phoneNumber));
                    return;
                }
            } catch (NumberFormatException e) {
                // Ignore and fall through to ask again
            }

            if (msgLower.equals("menu") || msgLower.matches(".*(oi|ola|olá|bom dia|boa tarde|boa noite|tudo bem|fala|opa|salve|hey|hello|hi).*")) {
                clearCustomerState(phoneNumber);
                stateStore.remove(StateKeys.cancelOptions(phoneNumber));
                // Não damos return! A mensagem flui normalmente para exibir o menu ou cumprimentar.
            } else {
                saveAndSend(customer, "Por favor, digite apenas o *NÚMERO* da opção que deseja cancelar, ou *0* para sair.", phoneNumber);
                return;
            }
        }

        // 3. Estado: Esperando escolha de pagamento (PIX / Presencial)
        if ("ESPERANDO_PAGAMENTO".equals(cState)) {
            // O horario marcado por ultimo (o que acabou de nascer), nunca um atendimento antigo.
            Appointment ag = agendaService.getAgendamentoMaisRecente(phoneNumber);

            // Escape: cancelar a pré-reserva
            if (msgLower.contains("cancelar") || msgLower.contains("desistir") || msgLower.contains("não quero") || msgLower.contains("nao quero")) {
                clearCustomerState(phoneNumber);
                if (agendaService.cancelarAgendamentoMaisRecente(phoneNumber) && ag != null) {
                    // Desistiu logo depois de marcar: a vaga volta para a lista de espera.
                    waitlistOfferService.offerFreedSlot(ag);
                }
                // Histórico mantido para a IA ter contexto de cancelamento
                saveAndSend(customer, "❌ Agendamento cancelado! Se mudar de ideia, é só digitar *menu* para recomeçar. 😊", phoneNumber);
                return;
            }

            // Sem agendamento no banco nao ha o que garantir: antes o cliente ouvia "100% garantido"
            // mesmo quando o agendamento nunca tinha sido salvo.
            if (ag == null) {
                log.warn("[AGENDA] {} em ESPERANDO_PAGAMENTO sem agendamento salvo — pedindo os dados de novo", phoneNumber);
                clearCustomerState(phoneNumber);
                saveAndSend(customer, BOOKING_FAILED_MSG, phoneNumber);
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
                saveAndSend(customer, pixMsg + perguntaAniversario(customer), phoneNumber);
                return;
            }

            // 2. Presencial
            if (msgLower.equals("2") || msgLower.contains("presencial")) {
                clearCustomerState(phoneNumber);
                if (ag != null) {
                    ag.setPaymentStatus(PaymentStatus.PRESENCIAL);
                    appointmentRepository.save(ag);
                }
                saveAndSend(customer, "Perfeito! Seu agendamento está confirmado para *pagamento presencial* direto no caixa da barbearia. Te esperamos lá! 💈👊\n\n_Para voltar ao início ou ver outras opções, digite *Menu*._" + perguntaAniversario(customer), phoneNumber);
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

        // 4. Menu explícito — SEMPRE funciona (invariante: "menu"/"opções"/"comandos").
        if (isMenuRequest(msgLower)) {
            log.info("[SISTEMA] Enviando Menu Textual para o Cliente: {}", phoneNumber);
            int points = customer.getFidelityPoints() != null ? customer.getFidelityPoints() : 0;
            String nome = barbershopSettingsService.resolve().displayName();
            saveAndSend(customer, MenuOptions.getMenuAsText(nome, points, loyaltyEnabled()), phoneNumber);
            return;
        }

        // 5. Abertura conversacional: um cliente ocioso que só cumprimenta é recebido como por
        //    um ATENDENTE — não com o menu numerado. "Oi, quero marcar" tem mais que saudação,
        //    então NÃO cai aqui: vai direto para a IA fazer o agendamento.
        if (cState == null && !ofertaAberta && !hasRecentIAConversation(customer.getId())
                && isPureGreeting(msgLower)) {
            log.info("[SISTEMA] Saudação de cliente ocioso — abertura conversacional para: {}", phoneNumber);
            saveAndSend(customer, buildWelcome(), phoneNumber);
            return;
        }

        // 5.5 "Quero cancelar meu horário" / "desmarcar" com horário marcado: cancela DE VERDADE.
        //     Antes só a frase exata funcionava; o resto ia para a IA, que respondia "cancelado"
        //     sem cancelar nada (pente fino de 25/11). Fora de um fluxo de agendamento.
        if (cState == null && wantsToCancelAppointment(msgLower)) {
            trataCancelarAgendamento(customer, phoneNumber);
            return;
        }
        if (cState == null && wantsToReschedule(msgLower)) {
            List<Appointment> futuros = agendaService.getAgendamentosFuturos(phoneNumber);
            if (!futuros.isEmpty()) {
                Appointment ag = futuros.get(0);
                saveAndSend(customer, "Você tem horário em *" + AppointmentDates.formatDate(ag.getAppointmentDate()) + "* às *"
                        + AppointmentDates.formatTime(ag.getAppointmentTime()) + "*. Para trocar, responda *cancelar meu horário* "
                        + "e depois me diga o novo dia e horário que eu marco pra você. 👍", phoneNumber);
                return;
            }
        }

        // 6. Tratamento das Opções do Menu Numérico e Palavras-chave.
        //    Número solto no meio de uma conversa com a IA é resposta à pergunta dela ("1 - 10:30,
        //    2 - 11:30"), não opção do menu: antes "2" virava "Você não possui agendamento".
        if (msgLower.matches("[1-8]") && numberAnswersBot(customer.getId())) {
            chamaIAETrataMensagem(customer, "Opção " + msgLower + " (da sua última pergunta)", phoneNumber);
            return;
        }
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
            saveAndSend(customer, tabelaDePrecos(customer), phoneNumber);
            return;
        } else if (msgLower.equals("6") || msgLower.contains("cartao fidelidade") || msgLower.contains("cartão fidelidade") || msgLower.contains("fidelidade")) {
            if (!loyaltyEnabled()) {
                saveAndSend(customer, "O cartão fidelidade ainda não está ativo por aqui 🙂 Posso te ajudar a marcar um horário?", phoneNumber);
                return;
            }
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
        List<Appointment> apps = agendaService.getAgendamentosFuturos(phoneNumber);
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
        String resp = "🎫 *Seu Cartão Fidelidade - " + barbershopSettingsService.resolve().displayName() + "*\n\n" +
                      "Você possui *" + points + "* de *10* pontos! 🌟\n\n" +
                      "A cada corte você ganha 1 ponto. Ao completar 10 pontos, você ganha *10% de desconto* no seu próximo corte! ✂️🔥";
        saveAndSend(customer, resp, phoneNumber);
    }

    /**
     * Cancelamento pedido pelo cliente — pelo texto exato ou pela IA. So enxerga horarios
     * <b>futuros</b>: atendimento ja feito (e pago) nao e "agendamento ativo" e nunca pode ser
     * apagado por aqui. Com mais de um, pergunta qual; a IA nao escolhe pelo cliente.
     */
    private void trataCancelarAgendamento(Customer customer, String phoneNumber) {
        List<Appointment> apps = agendaService.getAgendamentosFuturos(phoneNumber);

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
                saveAndSend(customer, "✅ Cancelei seu horário de *" + (diaSemana != null ? diaSemana + ", " : "") + apptDate
                        + "* às *" + apptTime + "*" + (apptService != null ? " (" + apptService + ")" : "")
                        + ". Se quiser marcar outro, é só me dizer o dia.", phoneNumber);

                waitlistOfferService.offerFreedSlot(ag);
            } else {
                saveAndSend(customer, "❌ Não foi possível cancelar esse agendamento.", phoneNumber);
            }
        } else {
            setCustomerState(phoneNumber, "ESPERANDO_CANCELAMENTO_MULTIPLO");
            stateStore.put(StateKeys.cancelOptions(phoneNumber), apps, CONV_TTL);
            StringBuilder sb = new StringBuilder("Você tem mais de um agendamento ativo. Qual deles deseja cancelar?\n\n");
            for (int i = 0; i < apps.size(); i++) {
                sb.append("*").append(i + 1).append("* - ").append(AppointmentDates.formatDate(apps.get(i).getAppointmentDate())).append(" às ").append(AppointmentDates.formatTime(apps.get(i).getAppointmentTime())).append("\n");
            }
            sb.append("\nDigite o *NÚMERO* do agendamento que deseja cancelar (ou *0* para sair).");
            saveAndSend(customer, sb.toString(), phoneNumber);
        }
    }

    private void chamaIAETrataMensagem(Customer customer, String prompt, String phoneNumber) {
        log.info("[IA] Chamando a IA para processar a intencao do cliente.");
        messenger.presence(phoneNumber, "composing", AI_PRESENCE_MS);
        long tIa = System.nanoTime();
        List<Interaction> history = getRecentHistory(customer.getId());
        // Servico da conversa (o servico vem ANTES do horario): os horarios que vao para a IA
        // contam so os barbeiros que o fazem e a duracao dele. Desconhecido = a IA pergunta o
        // servico primeiro e nao recebe horario nenhum (ver OpenAIService).
        ProfessionalAssignmentService.ServicoPedido servico = resolverServico(prompt, history, phoneNumber);
        String agendaContext = agendaService.getHorariosOcupadosParaIA(servico);
        // So os horarios por vir: contar o historico fazia o cliente fiel ouvir "limite atingido".
        int activeAppointments = agendaService.getAgendamentosFuturos(phoneNumber).size();
        String knownName = (customer.getName() != null && !customer.getName().isBlank()) ? customer.getName() : "Desconhecido";
        String aiResponse;
        try {
            aiResponse = openAIService.processCustomerMessage(prompt, history, agendaContext, activeAppointments, knownName, customer, servico);
        } catch (AiUnavailableException e) {
            tratarIaIndisponivel(phoneNumber, e);
            return;
        }

        // Escolha indireta ("o segundo", "os dois"): a IA informa o servico pela tag [SERVICO|...].
        // So vale se casar com o catalogo — nome inventado nao vira servico da conversa.
        String servicoDaTag = AiTagParser.extractService(aiResponse);
        if (servicoDaTag != null) {
            String canonico = professionalAssignmentService.nomeDoServicoCitado(servicoDaTag);
            if (canonico != null) lembrarServico(phoneNumber, canonico);
        }

        log.info("[TEMPO] IA para {}: {} ms (contexto + modelo)", phoneNumber, (System.nanoTime() - tIa) / 1_000_000);

        AiIntent intent = aiTagParser.parse(aiResponse);
        String responseToSend;

        if (intent instanceof AiIntent.NovoAgendamento novoAgendamento) {
            responseToSend = tratarNovoAgendamento(customer, phoneNumber, novoAgendamento);
        } else if (intent instanceof AiIntent.Cancelar) {
            // O texto da IA ("cancelado ✅") e descartado de proposito: ela escreve antes de saber
            // se havia o que cancelar, e com dois horarios nao sabe qual. O fluxo deterministico
            // responde o que de fato aconteceu — cancela o unico, pergunta qual, ou diz que nao ha.
            log.info("[AGENDA] IA pediu cancelamento para {}", phoneNumber);
            trataCancelarAgendamento(customer, phoneNumber);
            return;
        } else if (intent instanceof AiIntent.ListaDeEspera listaDeEspera) {
            responseToSend = tratarListaDeEspera(phoneNumber, listaDeEspera);
        } else if (OpenAIService.claimsBookingWithoutTag(intent.cleanedResponse())) {
            // Nem o modelo forte mandou a tag: nao confirma um horario que nao existe.
            log.warn("[IA] Resposta dizia que agendou, mas sem a tag; nada foi gravado para {}", phoneNumber);
            responseToSend = "Opa, não consegui registrar seu horário agora. 😕 Pode responder *SIM* de novo para eu confirmar?";
        } else if (isAbortReply(intent.cleanedResponse()) && !asksToAbort(prompt)) {
            // A IA usava "atendimento cancelado" para 👍, arquivo e mensagem que não entendia.
            log.warn("[IA] Frase de desistência sem o cliente pedir; trocada para {}", phoneNumber);
            responseToSend = "Como posso te ajudar? 😊 Se quiser marcar, me diz o serviço e o dia que fica bom pra você.";
        } else {
            responseToSend = intent.cleanedResponse();
        }

        if (responseToSend == null) return; // o fluxo ja respondeu (ex.: agendou direto com o barbeiro pedido)
        long tEnvio = System.nanoTime();
        saveAndSend(customer, responseToSend, phoneNumber);
        log.info("[TEMPO] Envio para {}: {} ms (gravar + enfileirar)", phoneNumber, (System.nanoTime() - tEnvio) / 1_000_000);
    }

    /** Janela em que falas antigas do cliente ainda contam como "esta conversa" (mesmo TTL do estado). */
    private static final Duration SERVICE_LOOKBACK = CONV_TTL;

    /**
     * Servico desta conversa, na ordem: o citado na mensagem atual (texto ou audio transcrito);
     * o ja definido antes (citado ou escolhido de forma indireta, via tag da IA); o citado nas
     * falas recentes do cliente. {@code null} = ainda nao definido.
     *
     * <p>Falas antigas so contam dentro de {@link #SERVICE_LOOKBACK}: o "corte" do mes passado no
     * historico nao pode pular a pergunta do servico num agendamento novo.
     */
    ProfessionalAssignmentService.ServicoPedido resolverServico(String prompt, List<Interaction> history, String phone) {
        String nome = professionalAssignmentService.nomeDoServicoCitado(prompt);
        if (nome == null) {
            nome = stateStore.get(StateKeys.chosenService(phone), String.class).orElse(null);
        }
        if (nome == null && history != null) {
            LocalDateTime limite = LocalDateTime.now(AppointmentDates.ZONE).minus(SERVICE_LOOKBACK);
            List<Interaction> recentes = history.stream()
                    .filter(i -> i.getTimestamp() != null && i.getTimestamp().isAfter(limite))
                    .toList();
            List<String> falas = falasDoCliente(null, recentes);
            for (String fala : falas) {
                nome = professionalAssignmentService.nomeDoServicoCitado(fala);
                if (nome != null) break;
            }
        }
        if (nome == null) return null;
        lembrarServico(phone, nome);
        return professionalAssignmentService.servicoDefinido(nome);
    }

    private void lembrarServico(String phone, String nome) {
        stateStore.put(StateKeys.chosenService(phone), nome, CONV_TTL);
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

        List<Appointment> apps = agendaService.getAgendamentosFuturos(phoneNumber);
        boolean isDuplicado = naDate != null && naTime != null && apps.stream()
                .anyMatch(a -> naDate.equals(a.getAppointmentDate()) && naTime.equals(a.getAppointmentTime()));

        if (isDuplicado) {
            log.info("[AGENDA] Ignorando tag duplicada gerada pela IA, pois o cliente ja possui este agendamento exato.");
            return "Tudo certo! Seu agendamento para o dia " + date + " às " + time + " já está garantido no sistema. Te esperamos lá!\n\n_Para voltar ao início ou ver outras opções, digite *Menu*._";
        }

        // Limite de horarios futuros por cliente (V36). Conferido aqui, antes de perguntar o
        // barbeiro ou reservar, para a resposta ser gentil e listar o que ele ja tem — a IA pode
        // ter ignorado o aviso do prompt, e o texto dela ("agendado!") seria mentira.
        var limite = agendaService.checkBookingLimit(phoneNumber);
        if (limite.isPresent()) {
            return limite.get().customerMessage(primeiroNome(name != null && !name.isBlank() ? name : customer.getName()));
        }

        // Atualiza o nome do cliente no banco de dados para a IA "lembrar" dele no futuro
        if (name != null && !name.isBlank()) {
            customer.setName(name);
            customerRepository.save(customer);
        }

        // Agenda multiprofissional: com mais de um barbeiro ativo, o cliente escolhe com quem
        // quer se atender. Com 0 ou 1 barbeiro, mantemos a auto-atribuição imediata (padrão).
        // Só entram na lista os que fazem o serviço pedido (V37): oferecer "barba com o Diego"
        // para depois trocar de barbeiro em silêncio seria pior que não oferecer.
        List<com.razor.api.model.Professional> ativos = professionalService.listActiveFor(service);
        if (ativos != null && ativos.size() > 1) {
            List<ProfessionalOption> options = ativos.stream().map(ProfessionalOption::of).toList();
            // Cliente ja disse com quem ("corte amanha 15h com o Bruno"): nao pergunta de novo.
            ProfessionalOption pedido = matchProfessional(na.professional(), options);
            if (pedido != null) {
                log.info("[AGENDA] Cliente {} pediu o barbeiro {} na propria mensagem", phoneNumber, pedido.getName());
                finalizarAgendamentoMultiprofissional(customer, phoneNumber,
                        new PendingBooking(name, date, time, service, options), pedido.getId(), pedido.getName());
                return null;
            }
            stateStore.put(StateKeys.pendingBooking(phoneNumber), new PendingBooking(name, date, time, service, options), CONV_TTL);
            setCustomerState(phoneNumber, "ESPERANDO_PROFISSIONAL");
            log.info("[AGENDA] Estado ESPERANDO_PROFISSIONAL definido para {} ({} barbeiros)", phoneNumber, ativos.size());
            return montarPerguntaProfissional(date, time, options);
        }

        var criado = agendaService.adicionarAgendamentoViaIA(name, phoneNumber, date, time, service);
        if (criado.isEmpty()) {
            // Nada foi salvo: NAO repassa o "Agendamento Confirmado!" da IA nem pede pagamento.
            log.warn("[AGENDA] Tag da IA nao gerou agendamento para {} ('{}' '{}')", phoneNumber, date, time);
            recordEvent(phoneNumber, com.razor.api.platform.ConversationEventRecorder.Kind.BOOKING_FAILED,
                    "IA pediu " + date + " " + time + " (" + service + ")");
            return BOOKING_FAILED_MSG;
        }
        stateStore.remove(StateKeys.chosenService(phoneNumber)); // agendado: o proximo comeca do zero

        // Cliente com faltas repetidas (V42): no lugar da pergunta de pagamento vai o PIX do sinal.
        var sinal = noShowDepositService.requireDepositIfNeeded(criado.get(),
                primeiroNome(name != null && !name.isBlank() ? name : customer.getName()));
        if (sinal.isPresent()) {
            return sinal.get();
        }

        // Verifica se a IA já incluiu a pergunta de pagamento (novo prompt instrui isso).
        // Se não incluiu, adiciona como fallback para garantir o fluxo.
        String response = anexarPerguntaPagamento(comResumoDoValor(na.cleanedResponse(), service));
        setCustomerState(phoneNumber, "ESPERANDO_PAGAMENTO");
        log.info("[AGENDA] Estado ESPERANDO_PAGAMENTO definido para {}", phoneNumber);
        return response;
    }

    /**
     * O barbeiro que o cliente pediu pelo nome, entre os que fazem o servico. Compara sem acento
     * nem caixa, pelo nome inteiro ou pelo primeiro nome ("Bruno" casa "Bruno Lima"). Nome que
     * nao casa ou que casa com mais de um = {@code null} (o bot pergunta com a lista).
     */
    static ProfessionalOption matchProfessional(String pedido, List<ProfessionalOption> options) {
        String alvo = ServiceCapability.normalize(pedido).replaceAll("^(com o|com a|com|o|a)\\s+", "");
        if (alvo.isEmpty() || options == null) return null;
        List<ProfessionalOption> exatos = options.stream()
                .filter(o -> ServiceCapability.normalize(o.getName()).equals(alvo))
                .toList();
        if (exatos.size() == 1) return exatos.get(0);
        String primeiro = alvo.split(" ")[0];
        List<ProfessionalOption> porPrimeiroNome = options.stream()
                .filter(o -> ServiceCapability.normalize(o.getName()).split(" ")[0].equals(primeiro))
                .toList();
        return porPrimeiroNome.size() == 1 ? porPrimeiroNome.get(0) : null;
    }

    /**
     * Com mais de um servico, a confirmacao do agendamento sempre traz cada servico com o preco, o
     * total e a duracao estimada — pelo calculo unico ({@link PricingService#quote}), o mesmo do
     * painel. Se a IA ja escreveu um "Total", nao repete.
     */
    String comResumoDoValor(String texto, String servico) {
        if (pricingService == null || servico == null || servico.isBlank()) return texto;
        if (texto != null && texto.toLowerCase().contains("total")) return texto;
        PricingService.Quote quote;
        try {
            quote = pricingService.quote(servico);
        } catch (Exception e) {
            log.warn("[AGENDA] Nao consegui montar o resumo de valor de '{}': {}", servico, e.getMessage());
            return texto;
        }
        String resumo = resumoDoValor(quote);
        return resumo.isEmpty() ? texto : (texto == null ? "" : texto) + "\n\n" + resumo;
    }

    /** "🧾 Resumo / • Corte — R$ 40,00 / *Total: R$ 70,00* · ⏱️ cerca de 1h". Vazio com um servico so. */
    static String resumoDoValor(PricingService.Quote quote) {
        if (quote == null || !quote.fromCatalog() || !quote.multiple()) return "";
        java.util.Locale br = java.util.Locale.forLanguageTag("pt-BR");
        StringBuilder sb = new StringBuilder("🧾 *Resumo*\n");
        for (PricingService.QuoteLine l : quote.lines()) {
            sb.append("• ").append(l.name()).append(" — ").append(String.format(br, "R$ %.2f", l.price())).append("\n");
        }
        sb.append("*Total: ").append(String.format(br, "R$ %.2f", quote.total())).append("* · ⏱️ cerca de ")
          .append(OpenAIService.formatDuration(quote.durationMinutes()));
        return sb.toString();
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

    /** Monta a pergunta "com qual barbeiro?" listando os profissionais (pelo apelido) numerados. */
    private String montarPerguntaProfissional(String date, String time, List<ProfessionalOption> ativos) {
        StringBuilder sb = new StringBuilder("Quase lá! Para *" + date + "* às *" + time + "*, com qual profissional você prefere se atender? 💈\n\n");
        for (int i = 0; i < ativos.size(); i++) {
            sb.append("*").append(i + 1).append("* - ").append(ativos.get(i).getName()).append("\n");
        }
        sb.append("\n_Responda o *número*, ou digite *tanto faz* para o primeiro disponível._");
        return sb.toString();
    }

    /** Trata a resposta do cliente no estado ESPERANDO_PROFISSIONAL e conclui o agendamento. */
    private void trataEscolhaProfissional(Customer customer, String phoneNumber, String msgLower) {
        PendingBooking pend = stateStore.get(StateKeys.pendingBooking(phoneNumber), PendingBooking.class).orElse(null);
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
                    ProfessionalOption p = pend.getOptions().get(index);
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
        stateStore.remove(StateKeys.pendingBooking(phoneNumber));
        java.util.Optional<com.razor.api.model.Appointment> criado;
        try {
            criado = agendaService.adicionarAgendamentoViaIA(pend.getName(), phoneNumber, pend.getDate(), pend.getTime(), pend.getService(), escolhido);
        } catch (AgendaBookingService.BookingLimitExceededException e) {
            // Marcou outro horario enquanto escolhia o barbeiro (ex.: pelo site): vale o limite.
            clearCustomerState(phoneNumber);
            saveAndSend(customer, e.detail().customerMessage(primeiroNome(pend.getName())), phoneNumber);
            return;
        }
        if (criado.isEmpty()) {
            clearCustomerState(phoneNumber);
            recordEvent(phoneNumber, com.razor.api.platform.ConversationEventRecorder.Kind.BOOKING_FAILED,
                    "escolha de barbeiro " + pend.getDate() + " " + pend.getTime());
            saveAndSend(customer, BOOKING_FAILED_MSG, phoneNumber);
            return;
        }
        stateStore.remove(StateKeys.chosenService(phoneNumber));

        // O barbeiro que FICOU com o horario: se o escolhido ocupou nesse meio-tempo, a agenda
        // atribui outro — e o cliente precisa ouvir o nome certo, nao o que pediu.
        java.util.UUID atribuido = criado.get().getProfessionalId();
        String nomeAtribuido = atribuido == null ? null : pend.getOptions().stream()
                .filter(o -> atribuido.equals(o.getId()))
                .map(ProfessionalOption::getName)
                .findFirst()
                .orElse(atribuido.equals(escolhido) ? nomeEscolhido : null);
        String comQuem = nomeAtribuido != null ? "com *" + nomeAtribuido + "* " : "";
        // O barbeiro pedido já estava ocupado e a agenda deu o horário a outro: o cliente precisa
        // saber (antes o bot confirmava "com o Rafael" e marcava com o Caio).
        String trocaDeBarbeiro = (escolhido != null && atribuido != null && !atribuido.equals(escolhido) && nomeEscolhido != null)
                ? "\n\n⚠️ O *" + nomeEscolhido + "* já estava ocupado nesse horário, então reservei com o *" + nomeAtribuido
                  + "*. Se preferir o " + primeiroNome(nomeEscolhido) + ", me diga outro horário que eu troco."
                : "";
        var sinal = noShowDepositService.requireDepositIfNeeded(criado.get(), primeiroNome(pend.getName()));
        if (sinal.isPresent()) {
            clearCustomerState(phoneNumber);
            saveAndSend(customer, sinal.get(), phoneNumber);
            return;
        }
        String confirm = "Fechado! Seu horário " + comQuem + "para *" + pend.getDate() + "* às *" + pend.getTime() + "* está reservado. 🎉" + trocaDeBarbeiro;
        String response = anexarPerguntaPagamento(comResumoDoValor(confirm, pend.getService()));
        setCustomerState(phoneNumber, "ESPERANDO_PAGAMENTO");
        log.info("[AGENDA] Agendamento multiprofissional concluído para {}; estado ESPERANDO_PAGAMENTO", phoneNumber);
        saveAndSend(customer, response, phoneNumber);
    }

    /** Pergunta do aniversario (V47) no fim do agendamento — uma vez, se a campanha estiver ligada. */
    private String perguntaAniversario(Customer customer) {
        String q = customerPreferencesService.birthdayQuestion(customer);
        return q == null ? "" : q;
    }

    private static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) return null;
        return nome.trim().split("\\s+")[0];
    }

    private String tratarListaDeEspera(String phoneNumber, AiIntent.ListaDeEspera le) {
        // V47: o desejo (dia/periodo/servico/barbeiro) fica estruturado; quem ja espera so atualiza.
        waitlistOfferService.join(phoneNumber, le.name(), le.preferredDay(), le.period(), le.service(), le.professional());
        return le.cleanedResponse();
    }

    /**
     * Resposta a uma vaga da lista de espera (V47). "Sim" que deu certo: conta como vaga
     * reaproveitada, aplica a regra do sinal e pergunta o pagamento, como num agendamento normal.
     */
    private void tratarRespostaListaEspera(Customer customer, String phoneNumber, WaitlistOfferService.Reply r) {
        if (!r.isBooked()) {
            saveAndSend(customer, r.message(), phoneNumber);
            return;
        }
        Appointment ag = r.booked();
        String nome = primeiroNome(ag.getCustomerName() != null ? ag.getCustomerName() : customer.getName());
        // A vaga liberada (desistencia/confirmacao) foi reaproveitada: conta no card da IA.
        confirmationService.record(com.razor.api.model.AiImpactEvent.Type.WAITLIST_REFILL, ag);
        var sinal = noShowDepositService.requireDepositIfNeeded(ag, nome);
        if (sinal.isPresent()) {
            saveAndSend(customer, sinal.get(), phoneNumber);
            return;
        }
        String successMsg = "Perfeito" + (nome != null ? ", " + nome : "") + "! 🥳\n\n" +
                "Seu horário foi agendado para *" + AppointmentDates.formatDate(ag.getAppointmentDate()) + "* às *"
                + AppointmentDates.formatTime(ag.getAppointmentTime()) + "*"
                + (ag.getService() != null ? " para *" + ag.getService() + "*" : "") + "! 🎉\n\n" +
                "*Como deseja pagar?*\n\n" +
                "1️⃣ PIX 💳\n" +
                "2️⃣ Presencialmente 💵\n\n" +
                "_Responda 1 ou 2. Ou digite *cancelar* para desistir._";
        setCustomerState(phoneNumber, "ESPERANDO_PAGAMENTO");
        saveAndSend(customer, successMsg, phoneNumber);
    }

    /**
     * Tabela de precos <b>daquele cliente</b>: assinante ve o preco com o beneficio do plano,
     * como no app do concorrente ("De R$ 35,00 / Por R$ 0,00"). Ate aqui o bot mandava o texto
     * fixo das configuracoes, entao quem pagava plano ouvia o preco cheio na conversa.
     *
     * <p>Sem catalogo cadastrado, cai no texto livre das configuracoes — barbearia que ainda nao
     * montou o catalogo continua respondendo o que respondia antes.
     */
    /**
     * Moeda em pt-BR, sempre. Concatenar {@code BigDecimal} direto imprimiria "R$ 35.00" com
     * ponto, destoando do resto das mensagens do bot — e o cliente final le isso.
     */
    private String brl(java.math.BigDecimal value) {
        return String.format(java.util.Locale.forLanguageTag("pt-BR"), "R$ %.2f", value);
    }

    private String tabelaDePrecos(Customer customer) {
        List<PlanPricingService.PricedItem> itens =
                planPricingService.priceCatalogFor(customer, AppointmentDates.today());
        if (itens.isEmpty()) {
            return barbershopSettingsService.resolve().servicesText();
        }

        StringBuilder sb = new StringBuilder("💈 *Nossos serviços*\n━━━━━━━━━━━━━━━━━━\n");
        boolean temDesconto = false;
        for (PlanPricingService.PricedItem item : itens) {
            sb.append("• *").append(item.name()).append("* — ");
            if (item.isFree()) {
                sb.append("~").append(brl(item.originalPrice())).append("~ *GRÁTIS pelo seu plano* 🎉");
                temDesconto = true;
            } else if (item.discounted()) {
                sb.append("~").append(brl(item.originalPrice())).append("~ *")
                        .append(brl(item.finalPrice())).append("*");
                temDesconto = true;
            } else {
                sb.append(brl(item.finalPrice()));
            }
            sb.append("\n");
        }
        if (temDesconto) {
            sb.append("\n_Os valores acima já consideram os benefícios do seu plano._");
        }
        return sb.toString();
    }

    private void saveAndSend(Customer customer, String content, String phone) {
        interactionRepository.save(Interaction.builder().barbershopId(customer.getBarbershopId()).customer(customer).type(Interaction.InteractionType.BOT).content(content).build());
        sendMsg(phone, content, "CLIENTE");
    }

    /** Invariante do menu: "menu", "opções" ou "comandos" SEMPRE abrem o menu completo. */
    private boolean isMenuRequest(String msgLower) {
        String t = msgLower.trim();
        return t.equals("menu") || t.equals("opcoes") || t.equals("opções")
                || t.equals("opcao") || t.equals("opção") || t.equals("comandos")
                || t.equals("ver opcoes") || t.equals("ver opções") || t.equals("ver menu");
    }

    /**
     * True apenas quando a mensagem e SO uma saudacao curta ("oi", "bom dia").
     * "Oi, quero marcar um corte" tem intencao alem do cumprimento — vai para a IA agendar.
     */
    private boolean isPureGreeting(String msgLower) {
        String t = msgLower.trim().replaceAll("[!.,?]+$", "");
        if (t.isEmpty() || t.length() > 24) return false;
        if (t.matches(".*(marcar|agendar|corte|barba|horario|horário|preco|preço|servico|serviço|cancelar).*")) {
            return false;
        }
        return t.matches("^(oi+|ola|olá|opa|eae|e ai|e aí|salve|fala|fala ai|bom dia|boa tarde|"
                + "boa noite|tudo bem|tudo bom|beleza|blz|hey|hello|hi|hola)( .*)?$");
    }

    /**
     * Saudacao de abertura no tom de atendente, com o nome do tenant — ou o texto de boas-vindas
     * que o dono configurou (welcomeText, V15). O menu continua a um "menu" de distancia.
     */
    private String buildWelcome() {
        BarbershopSettingsService.BotSettings bot = barbershopSettingsService.resolve();
        if (bot.welcomeText() != null && !bot.welcomeText().isBlank()) {
            return bot.welcomeText();
        }
        return "Opa! 👋 Aqui é o atendimento da *" + bot.displayName() + "*.\n\n"
                + "Posso marcar um horário pra você — é só me dizer *o serviço* que quer e *o dia*. 💈\n\n"
                + "_Se preferir ver tudo que dá pra fazer por aqui, é só digitar *menu*._";
    }

    /** Agendamento nao foi salvo (ex.: data/hora ilegivel): pede os dados de novo, sem confirmar nada. */
    static final String BOOKING_FAILED_MSG = "Ops, não consegui registrar esse horário 😕 "
            + "Pode me confirmar de novo o *dia* e o *horário* que você quer? (ex.: *26/05 às 14:00*)";

    /** Mensagem ao cliente quando a IA nao responde. NAO vai para o historico da IA. */
    static final String AI_CONTINGENCY_MSG = "Opa, nosso atendimento automático está instável agora 😕 "
            + "Já avisei a equipe da barbearia. Tente de novo em alguns minutos ou digite *menu* para ver as opções.";

    /** Aviso de IA fora do ar para os admins do tenant: no maximo um a cada 30 minutos. */
    private static final Duration AI_ALERT_THROTTLE = Duration.ofMinutes(30);

    /**
     * IA fora do ar: o cliente recebe a contingencia, mas ela NAO e gravada como fala do bot —
     * senao a proxima chamada a IA leria o proprio "deu erro" como contexto. Os admins do tenant
     * sao avisados (com throttle, para uma queda nao virar uma enxurrada de mensagens).
     */
    private void recordEvent(String phone, com.razor.api.platform.ConversationEventRecorder.Kind kind, String detail) {
        if (conversationEvents != null) conversationEvents.record(phone, kind, detail);
    }

    private void tratarIaIndisponivel(String phoneNumber, AiUnavailableException e) {
        log.error("[IA] Indisponivel ao atender {} — enviando contingencia", phoneNumber, e);
        sendMsg(phoneNumber, AI_CONTINGENCY_MSG, "CLIENTE");
        recordEvent(phoneNumber, com.razor.api.platform.ConversationEventRecorder.Kind.AI_ERROR,
                com.razor.api.service.HealthSignals.classifyAiErrorPublic(e));

        String alertKey = StateKeys.aiOutageAlert();
        if (stateStore.get(alertKey, Boolean.class).isPresent()) return;
        stateStore.put(alertKey, Boolean.TRUE, AI_ALERT_THROTTLE);
        for (String adm : tenantAdminService.adminPhones()) {
            sendMsg(adm, "⚠️ *IA INDISPONÍVEL*\nO atendimento automático não conseguiu responder um cliente ("
                    + phoneNumber + "). Verifique a chave/cota da OpenAI. Os clientes estão recebendo uma "
                    + "mensagem de contingência.\n_(Próximo aviso em no mínimo 30 min)_", "ADMIN");
        }
    }

    /** Historico com TIPO (USER/BOT): o papel no chat da IA vem dele, nunca da posicao na lista. */
    /** Mensagem atual e as falas anteriores do cliente, da mais recente para a mais antiga. */
    static List<String> falasDoCliente(String atual, List<Interaction> history) {
        List<String> falas = new java.util.ArrayList<>();
        if (atual != null && !atual.isBlank()) falas.add(atual);
        if (history != null) {
            history.stream()
                    .filter(i -> i.getType() == Interaction.InteractionType.USER && i.getContent() != null)
                    .sorted(java.util.Comparator.comparing(Interaction::getTimestamp,
                            java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                    .map(Interaction::getContent)
                    .forEach(falas::add);
        }
        return falas;
    }

    private List<Interaction> getRecentHistory(java.util.UUID customerId) {
        return interactionRepository.findRecentInteractionsByCustomerId(customerId);
    }

    /**
     * Verifica se o cliente tem uma conversa ativa recente com a IA (últimas 4+ interações nos últimos 5 min).
     * Isso evita que saudações como "Oi, me chamo João" disparem o menu no meio de um agendamento.
     */
    static final String FILE_RECEIVED_MSG = "Recebi seu arquivo 👍 Por aqui eu leio texto e áudio. "
            + "Se for o comprovante do PIX, é só mostrar na barbearia no dia. Posso te ajudar em mais alguma coisa?";

    private static String semAcento(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }

    /** "quero cancelar meu horário", "desmarcar o corte de sábado", "cancela minha reserva". */
    static boolean wantsToCancelAppointment(String msg) {
        if (msg == null) return false;
        String t = semAcento(msg);
        if (t.matches("\\s*desmarcar\\s*")) return true;
        return t.matches("(?s).*\\b(cancel\\w*|desmarc\\w*)\\b.*")
                && t.matches("(?s).*\\b(horario|agendamento|marcacao|hora|corte|reserva|barba|atendimento marcado)\\b.*");
    }

    /** "quero mudar meu horário", "dá pra remarcar?", "trocar o dia do meu corte". */
    static boolean wantsToReschedule(String msg) {
        if (msg == null) return false;
        String t = semAcento(msg);
        return t.matches("(?s).*\\b(remarc\\w*|reagend\\w*)\\b.*")
                || (t.matches("(?s).*\\b(mudar|trocar|alterar|passar)\\b.*") && t.matches("(?s).*\\bmeu (horario|agendamento|corte|dia)\\b.*"));
    }

    private boolean loyaltyEnabled() {
        return planFeatures == null || planFeatures.enabled(PlanFeatures.Feature.LOYALTY);
    }

    static boolean isAbortReply(String text) {
        return text != null && text.toLowerCase().contains("atendimento foi cancelado");
    }

    /** O cliente pediu, com palavras, para cancelar ou desistir. */
    static boolean asksToAbort(String msg) {
        if (msg == null) return false;
        String t = java.text.Normalizer.normalize(msg, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
        return t.matches("(?s).*\\b(cancel\\w*|desist\\w*|deixa (pra|para) la|esquece|nao quero( mais)?|para(r)? tudo|nao precisa)\\b.*");
    }

    /**
     * Número solto responde à pergunta do bot (e não ao menu) quando a última mensagem dele, de
     * até 30 min atrás, não foi o próprio menu nem o "digite 1 para marcar".
     */
    private boolean numberAnswersBot(java.util.UUID customerId) {
        List<Interaction> recent = interactionRepository.findRecentInteractionsByCustomerId(customerId);
        Interaction lastBot = recent.stream().filter(i -> i.getType() == Interaction.InteractionType.BOT).findFirst().orElse(null);
        if (lastBot == null || lastBot.getTimestamp() == null || lastBot.getContent() == null) return false;
        long min = java.time.Duration.between(lastBot.getTimestamp(), java.time.LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"))).toMinutes();
        if (Math.abs(min) > 30) return false;
        String c = lastBot.getContent();
        if (c.contains("1️⃣ Agendar") || c.contains("Digite *1* para marcar")) return false;
        // Só faz sentido "2" = segunda opção quando a pergunta trouxe opções em lista.
        return c.lines().filter(l -> l.trim().matches("^([-•▪]|\\*?\\d{1,2}\\*?\\s*[-–.)]|[1-9]️⃣).*")).count() >= 2;
    }

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

}
