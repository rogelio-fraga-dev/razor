package com.razor.api.service;

import com.razor.api.dto.EvolutionWebhookDTO;
import com.razor.api.repository.InteractionRepository;
import com.razor.api.state.StateKeys;
import com.razor.api.state.StateStore;
import com.razor.api.tenant.TenantContext;
import com.razor.api.tenant.TenantResolver;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Ponto de entrada do webhook da Evolution API. Faz a triagem (ignora mensagens
 * do proprio bot/grupos, deduplica por messageId) e roteia para o fluxo do admin
 * ou do cliente. A logica de cada fluxo vive em {@link AdminCommandService} e
 * {@link CustomerConversationService} (Fase 0.5 — quebra do OrchestratorService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookRouter {

    // Mensagens mais antigas que isso sao historico sincronizado pela Evolution ao (re)conectar
    // — nao devem ser respondidas (evita o bot "acordar" respondendo conversas velhas).
    private static final long MAX_MESSAGE_AGE_SECONDS = 300;

    /** Janela de deduplicacao: a Evolution pode reentregar o mesmo evento. */
    private static final Duration DEDUP_TTL = Duration.ofMinutes(20);

    private final CustomerService customerService;
    private final InteractionRepository interactionRepository;
    private final AdminCommandService adminCommandService;
    private final CustomerConversationService customerConversationService;
    private final TenantResolver tenantResolver;
    private final TenantAdminService tenantAdminService;
    private final BarbershopSettingsService barbershopSettingsService;
    /** Barbearia suspensa pelo console: o bot não responde (26/11). Opcional: nulo nos testes. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.razor.api.repository.BarbershopRepository barbershopRepository;
    private final StateStore stateStore;
    private final WhatsAppMessenger messenger;

    /** Aviso de instancia desconhecida: no maximo um log por instancia nesta janela. */
    private static final Duration UNKNOWN_INSTANCE_WARN_TTL = Duration.ofMinutes(10);

    /** Resposta ao cliente quando o processamento da mensagem quebra (antes: silencio total). */
    static final String CONTINGENCY_MSG = "Opa, tive um probleminha para processar sua mensagem 😕 "
            + "Pode tentar de novo em alguns instantes? Se preferir, digite *menu*.";

    @Async
    public void processWebhook(EvolutionWebhookDTO webhook) {
        String dedupKey = null;     // marcado como visto nesta execucao (desfeito se falhar)
        String customerPhone = null; // preenchido so quando o remetente e cliente
        try {
            // Resolve o tenant desta mensagem (por instancia da Evolution) e o mantem
            // no contexto por toda a thread de processamento. Precisa ser setado AQUI
            // (thread @Async), nao no controller.
            java.util.UUID tenant = tenantResolver.resolveByInstance(webhook.getInstance());
            if (tenant == null) {
                // Instancia desconhecida: nao existe barbearia dona desta mensagem. Descartar e
                // a unica acao segura — processar no tenant padrao permitiria injecao de comandos.
                // O log e limitado por instancia: sem ele uma instancia mal configurada ficava
                // muda sem pista; sem o limite, um ataque enchia o log.
                warnUnknownInstance(webhook.getInstance());
                return;
            }
            TenantContext.set(tenant);
            if (barbershopRepository != null && barbershopRepository.findById(tenant)
                    .map(b -> "SUSPENDED".equals(b.getStatus())).orElse(false)) {
                log.info("[SUSPENSA] Barbearia suspensa pelo console: mensagem ignorada.");
                return;
            }

            if (shouldIgnoreMessage(webhook)) {
                log.debug("[IGNORED] Mensagem ignorada (do proprio bot, grupo ou sem numero valido).");
                return;
            }
            if (isHistoricalMessage(webhook)) {
                log.info("[HISTORICO] Mensagem antiga ignorada (sync da Evolution ao conectar).");
                return;
            }
            if (isDuplicateMessage(webhook)) {
                log.info("[DUP] Mensagem duplicada ignorada (ID: {}).", webhook.getData().getKey().getId());
                return;
            }
            String messageId = webhook.getData().getKey().getId();
            dedupKey = messageId != null ? StateKeys.dedup(messageId) : null;

            String phoneNumber = webhook.getPhoneNumber();
            String pushName = webhook.getData() != null ? webhook.getData().getPushName() : "Desconhecido";
            String msgType = webhook.hasAudio() ? "AUDIO" :
                             webhook.hasImage() ? "IMAGEM" :
                             webhook.hasDocument() ? "DOCUMENTO" :
                             webhook.hasSticker() ? "STICKER" : "TEXTO";

            log.info("=======================================================================");
            log.info("[RECEBIDO] Nova mensagem de: {} ({}) | Tipo: {}", pushName, phoneNumber, msgType);

            if (tenantAdminService.isAdmin(phoneNumber)) {
                log.info("[SISTEMA] Remetente identificado como ADMINISTRADOR.");
                adminCommandService.processAdminMessage(webhook, phoneNumber);
            } else {
                if (!barbershopSettingsService.isBotEnabled()) {
                    log.info("[BOT OFF] Bot desativado pelo dono neste tenant — mensagem de cliente ignorada.");
                    log.info("=======================================================================");
                    return;
                }
                if (customerService.isCustomerPaused(phoneNumber)) {
                    log.info("[PAUSADO] O cliente {} esta pausado. Atendimento automatico suspenso.", phoneNumber);
                    log.info("=======================================================================");
                    return;
                }
                log.info("[CLIENTE] Remetente identificado como CLIENTE.");
                customerPhone = phoneNumber;
                customerConversationService.processCustomerMessage(webhook, phoneNumber);
            }
            log.info("=======================================================================");
        } catch (Exception e) {
            log.error("[ERRO FATAL ORCHESTRATOR] Falha critica ao processar webhook: {}", e.getMessage(), e);
            // A mensagem foi marcada como vista ANTES de processar; sem desmarcar, a reentrega
            // da Evolution seria descartada como duplicada e a mensagem se perderia de vez.
            if (dedupKey != null) {
                stateStore.remove(dedupKey);
            }
            // Cliente nao fica no vacuo: antes ele simplesmente nao recebia resposta nenhuma.
            if (customerPhone != null) {
                try {
                    messenger.send(customerPhone, CONTINGENCY_MSG, "CLIENTE");
                } catch (Exception sendError) {
                    log.error("[ERRO FATAL ORCHESTRATOR] Nem a contingencia saiu para {}", customerPhone, sendError);
                }
            }
        } finally {
            TenantContext.clear();
        }
    }

    private void warnUnknownInstance(String instance) {
        String key = "warn:unknown-instance:" + instance;
        if (stateStore.get(key, Boolean.class).isPresent()) return;
        stateStore.put(key, Boolean.TRUE, UNKNOWN_INSTANCE_WARN_TTL);
        log.warn("[WEBHOOK] Instancia desconhecida '{}' — mensagem descartada (proximo aviso desta "
                + "instancia em {} min)", instance, UNKNOWN_INSTANCE_WARN_TTL.toMinutes());
    }

    private boolean shouldIgnoreMessage(EvolutionWebhookDTO webhook) {
        if (webhook.getData() == null || webhook.getData().getKey() == null) return true;
        if (Boolean.TRUE.equals(webhook.getData().getKey().getFromMe())) return true;
        return webhook.isGroupChat() || webhook.getPhoneNumber() == null;
    }

    /** Historico sincronizado ao conectar: timestamp valido e mais velho que o limite. */
    private boolean isHistoricalMessage(EvolutionWebhookDTO webhook) {
        Long ts = webhook.getData() != null ? webhook.getData().getMessageTimestamp() : null;
        if (ts == null || ts <= 0) return false; // sem timestamp confiavel = processa normalmente
        return java.time.Instant.now().getEpochSecond() - ts > MAX_MESSAGE_AGE_SECONDS;
    }

    private boolean isDuplicateMessage(EvolutionWebhookDTO webhook) {
        String messageId = webhook.getData().getKey().getId();
        if (messageId == null) return false;

        String key = StateKeys.dedup(messageId);
        if (stateStore.get(key, Boolean.class).isPresent()) return true; // ja visto na janela
        stateStore.put(key, Boolean.TRUE, DEDUP_TTL);

        // Segunda barreira: mensagem ja persistida (sobrevive a restart, ao contrario do cache).
        return interactionRepository.existsByMessageId(messageId);
    }

}
