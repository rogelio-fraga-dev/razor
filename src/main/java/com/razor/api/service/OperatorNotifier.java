package com.razor.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.razor.api.config.RazorProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Aviso ao OPERADOR da plataforma (quem mantem o Razor no ar), V50. Opt-in e desligado por
 * padrao — dois canais, independentes:
 *
 * <ul>
 *   <li>{@code razor.monitoring.operator-webhook-url} (env {@code RAZOR_OPERATOR_WEBHOOK_URL}):
 *       POST JSON com {@code text} (Slack) e {@code content} (Discord) — o mesmo texto nos dois,
 *       para servir nos dois sem adaptador.</li>
 *   <li>{@code razor.monitoring.operator-log-alerts} (env {@code RAZOR_OPERATOR_LOG_ALERTS}): uma
 *       linha {@code [OPERADOR]} em nivel ERROR, para quem ja coleta log e nao quer webhook.</li>
 * </ul>
 *
 * <p>O texto leva tipo, barbearia (slug) e a categoria do problema — nunca segredo, telefone de
 * cliente ou a mensagem crua do provedor. Falha do webhook so e logada: avisar o operador nunca
 * pode derrubar o monitor.
 */
@Slf4j
@Component
public class OperatorNotifier {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final RazorProperties properties;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public OperatorNotifier(RazorProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    }

    OperatorNotifier(RazorProperties properties, HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    /** Algum canal do operador esta ligado. */
    public boolean enabled() {
        RazorProperties.Monitoring m = properties.getMonitoring();
        return m.isOperatorLogAlerts() || hasWebhook();
    }

    /** Envia o aviso pelos canais ligados. @return true se ao menos um canal aceitou. */
    public boolean notify(String text) {
        boolean delivered = false;
        if (properties.getMonitoring().isOperatorLogAlerts()) {
            log.error("[OPERADOR] {}", text);
            delivered = true;
        }
        if (hasWebhook()) {
            delivered |= postWebhook(text);
        }
        return delivered;
    }

    private boolean hasWebhook() {
        String url = properties.getMonitoring().getOperatorWebhookUrl();
        return url != null && !url.isBlank();
    }

    private boolean postWebhook(String text) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("text", text);
            body.put("content", text);
            HttpRequest req = HttpRequest.newBuilder(URI.create(properties.getMonitoring().getOperatorWebhookUrl().trim()))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() >= 200 && res.statusCode() < 300) return true;
            // A URL nao vai para o log: em Slack/Discord ela E o segredo.
            log.warn("[MONITOR] Webhook do operador respondeu HTTP {}", res.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("[MONITOR] Falha ao avisar o operador pelo webhook: {}", e.getClass().getSimpleName());
        }
        return false;
    }
}
