package com.razor.api.controller;

import com.razor.api.config.RazorProperties;
import com.razor.api.dto.EvolutionWebhookDTO;
import com.razor.api.service.WebhookRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookRouter webhookRouter;
    private final ObjectMapper objectMapper;
    private final RazorProperties properties;

    /**
     * Endpoint que recebe webhooks da Evolution API (mensagens e QR Code).
     *
     * <p><b>Protegido por token compartilhado</b> ({@code X-Webhook-Token} ou {@code ?token=}),
     * <b>fail-closed</b> como o onboarding: sem {@code razor.webhook.token} configurado, nada entra.
     * Sem isso qualquer um poderia forjar uma mensagem com o telefone de um admin e executar
     * comandos de gestao (listar base, apagar cliente, disparo em massa).
     *
     * <p>O header tem prioridade. A query string continua aceita porque o webhook GLOBAL da
     * Evolution (WEBHOOK_GLOBAL_URL) so recebe uma URL, sem headers proprios. Como URL com segredo
     * vaza em access log, o nginx do painel nao loga /api/webhook e este controller nunca loga a
     * URL nem o token.
     */
    @PostMapping
    public ResponseEntity<String> receiveWebhook(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Webhook-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken) {

        if (!isAuthorized(headerToken, queryToken)) {
            log.warn("[WEBHOOK] Requisicao rejeitada: token ausente ou invalido.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("nao autorizado");
        }

        try {
            String event = body.get("event") != null ? body.get("event").toString() : null;
            String instance = body.get("instance") != null ? body.get("instance").toString() : null;
            // Só loga em INFO os eventos que importam (mensagens e QR); presence.update etc em DEBUG
            boolean relevante = event != null && (
                event.equalsIgnoreCase("messages.upsert") || event.equalsIgnoreCase("MESSAGES_UPSERT")
                || event.equalsIgnoreCase("qrcode.updated") || event.equalsIgnoreCase("QRCODE_UPDATED"));
            if (relevante) {
                log.info("Webhook - {} | {}", event, instance);
            } else {
                log.debug("Webhook - {} | {}", event, instance);
            }

            // QRCODE_UPDATED nao e mais tratado aqui: o QR sai so pelo painel autenticado
            // (POST /api/admin/whatsapp/connect). O antigo "salvar em qrcode-evolution.png" gravava
            // o QR de QUALQUER tenant num arquivo unico e devolvia o caminho absoluto do servidor.

            // Evento de mensagens: delega para o orquestrador
            if ("messages.upsert".equalsIgnoreCase(event) || "MESSAGES_UPSERT".equalsIgnoreCase(event)) {
                EvolutionWebhookDTO webhook = objectMapper.convertValue(body, EvolutionWebhookDTO.class);
                webhookRouter.processWebhook(webhook);
            }

            return ResponseEntity.ok("Webhook recebido com sucesso");
        } catch (Exception e) {
            log.error("Erro ao processar webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao processar webhook");
        }
    }

    /** Comparacao em tempo constante; vazio no config = fail-closed (webhook desabilitado). */
    private boolean isAuthorized(String headerToken, String queryToken) {
        String expected = properties.getWebhook().getToken();
        if (expected == null || expected.isBlank()) {
            log.error("[WEBHOOK] razor.webhook.token nao configurado — webhook desabilitado (fail-closed). "
                    + "Defina WEBHOOK_TOKEN e aponte a Evolution para /api/webhook?token=...");
            return false;
        }
        String provided = (headerToken != null && !headerToken.isBlank()) ? headerToken : queryToken;
        if (provided == null || provided.isBlank()) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Endpoint de health check
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Razor está online!");
    }

    // REMOVIDO: GET /api/webhook/qrcode servia o QR de pareamento SEM autenticacao — qualquer
    // um poderia conectar o WhatsApp da barbearia ao proprio aparelho. O QR agora sai apenas
    // pelo painel autenticado (POST /api/admin/whatsapp/connect), escopado no tenant do JWT.
}
