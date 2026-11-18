package com.razor.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razor.api.config.RazorProperties;
import com.razor.api.tenant.TenantResolver;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Busca o conteudo base64 de uma midia (imagem/audio/documento) na Evolution API,
 * usada quando o webhook chega sem o base64 embutido. Extraido do
 * {@code OrchestratorService} para isolar a integracao HTTP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvolutionMediaService {

    private final RazorProperties properties;
    private final TenantResolver tenantResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** IDs de mensagem da Evolution/WhatsApp: alfanumericos, sem nada que mude o JSON. */
    private static final java.util.regex.Pattern MESSAGE_ID = java.util.regex.Pattern.compile("^[A-Za-z0-9_-]{1,128}$");
    private static final java.time.Duration CONNECT_TIMEOUT = java.time.Duration.ofSeconds(5);
    private static final java.time.Duration REQUEST_TIMEOUT = java.time.Duration.ofSeconds(30);

    /** Um cliente so, com timeout de conexao: sem ele uma Evolution travada prendia a thread. */
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    public String fetchBase64(String messageId) {
        // O messageId vem do webhook (entrada externa). Antes ia direto num String.format do
        // JSON: um id com aspas injetava campos no corpo enviado a Evolution.
        if (messageId == null || !MESSAGE_ID.matcher(messageId).matches()) {
            log.warn("[MEDIA] messageId invalido recusado: '{}'",
                    messageId == null ? null : messageId.substring(0, Math.min(messageId.length(), 64)));
            return null;
        }
        try {
            String baseUrl = properties.getEvolution().getBaseUrl();
            if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            String url = baseUrl + "/chat/getBase64FromMediaMessage/" + tenantResolver.outboundInstance();
            String jsonPayload = buildPayload(messageId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("apikey", properties.getEvolution().getApiKey())
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("base64") && !root.get("base64").isNull()) return root.get("base64").asText();
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("[MEDIA] Falha ao buscar base64 da midia (messageId={}): {}", messageId, e.getMessage());
        }
        return null;
    }

    /** Corpo {@code {"message":{"key":{"id":...}}}} montado pelo Jackson (escape garantido). */
    // package-private: coberto por EvolutionMediaServiceTest.
    String buildPayload(String messageId) throws com.fasterxml.jackson.core.JsonProcessingException {
        var root = objectMapper.createObjectNode();
        root.putObject("message").putObject("key").put("id", messageId);
        return objectMapper.writeValueAsString(root);
    }
}
