package com.razor.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razor.api.config.RazorProperties;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String fetchBase64(String messageId) {
        try {
            String baseUrl = properties.getEvolution().getBaseUrl();
            if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            String url = baseUrl + "/chat/getBase64FromMediaMessage/" + properties.getEvolution().getInstanceName();
            String jsonPayload = String.format("{\"message\":{\"key\":{\"id\":\"%s\"}}}", messageId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("apikey", properties.getEvolution().getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("base64") && !root.get("base64").isNull()) return root.get("base64").asText();
            }
        } catch (Exception e) {
            log.warn("[MEDIA] Falha ao buscar base64 da midia (messageId={}): {}", messageId, e.getMessage());
        }
        return null;
    }
}
