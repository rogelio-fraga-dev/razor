package com.razor.api.service;

import com.razor.api.dto.EvolutionWebhookDTO;
import com.razor.api.repository.InteractionRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.tenant.TenantResolver;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
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

    private final CustomerService customerService;
    private final InteractionRepository interactionRepository;
    private final AdminCommandService adminCommandService;
    private final CustomerConversationService customerConversationService;
    private final TenantResolver tenantResolver;
    private final TenantAdminService tenantAdminService;

    // Instancia, nao static: servico singleton (mesmo comportamento) e testes isolados.
    private final Map<String, LocalDateTime> processedMessageIds = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 600000)
    public void clearProcessedCache() {
        LocalDateTime limit = LocalDateTime.now().minusMinutes(20);
        processedMessageIds.entrySet().removeIf(entry -> entry.getValue().isBefore(limit));
    }

    @Async
    public void processWebhook(EvolutionWebhookDTO webhook) {
        try {
            // Resolve o tenant desta mensagem (por instancia da Evolution) e o mantem
            // no contexto por toda a thread de processamento. Precisa ser setado AQUI
            // (thread @Async), nao no controller.
            TenantContext.set(tenantResolver.resolveByInstance(webhook.getInstance()));

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

            if (tenantAdminService.isAdmin(phoneNumber)) {
                log.info("[SISTEMA] Remetente identificado como ADMINISTRADOR.");
                adminCommandService.processAdminMessage(webhook, phoneNumber);
            } else {
                if (customerService.isCustomerPaused(phoneNumber)) {
                    log.info("[PAUSADO] O cliente {} esta pausado. Atendimento automatico suspenso.", phoneNumber);
                    log.info("=======================================================================");
                    return;
                }
                log.info("[CLIENTE] Remetente identificado como CLIENTE.");
                customerConversationService.processCustomerMessage(webhook, phoneNumber);
            }
            log.info("=======================================================================");
        } catch (Exception e) {
            log.error("[ERRO FATAL ORCHESTRATOR] Falha critica ao processar webhook: {}", e.getMessage(), e);
        } finally {
            TenantContext.clear();
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

}
