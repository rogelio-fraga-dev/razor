package com.razor.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.razor.api.config.RazorProperties;
import com.razor.api.dto.WhatsAppConnectResponse;
import com.razor.api.dto.WhatsAppStatusResponse;
import com.razor.api.model.Barbershop;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.tenant.TenantContext;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Conexao do WhatsApp do tenant pelo painel (self-service): cria a instancia na Evolution e entrega
 * o QR code para o dono escanear pelo proprio app, sem abrir a Evolution. Sempre escopado no tenant
 * atual; o nome da instancia vem de {@code barbershops.whatsapp_instance} (derivado do slug se ausente).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppConnectionService {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final String STATE_OPEN = "open";

    private final WebClient webClient;
    private final RazorProperties properties;
    private final BarbershopRepository barbershopRepository;

    /** Estado atual da conexao do tenant. */
    public WhatsAppStatusResponse status() {
        String instance = ensureInstanceName();
        String state = fetchState(instance);
        return new WhatsAppStatusResponse(instance, state, STATE_OPEN.equals(state));
    }

    /**
     * Garante a instancia na Evolution e devolve o QR para pareamento. Se ja estiver conectada,
     * devolve state "open" sem QR.
     */
    public WhatsAppConnectResponse connect() {
        String instance = ensureInstanceName();

        if (STATE_OPEN.equals(fetchState(instance))) {
            return new WhatsAppConnectResponse(instance, STATE_OPEN, null, null);
        }

        createInstanceIfAbsent(instance);
        JsonNode conn = getJson("/instance/connect/" + instance);
        String base64 = conn != null && conn.hasNonNull("base64") ? conn.get("base64").asText() : null;
        String pairing = conn != null && conn.hasNonNull("pairingCode") ? conn.get("pairingCode").asText() : null;
        String state = base64 != null ? "connecting" : fetchState(instance);
        return new WhatsAppConnectResponse(instance, state, base64, pairing);
    }

    /** Desconecta (logout) a sessao de WhatsApp do tenant, mantendo a instancia para reconectar. */
    public void disconnect() {
        String instance = ensureInstanceName();
        try {
            webClient.delete()
                    .uri("/instance/logout/{n}", instance)
                    .header("apikey", apiKey())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
            log.info("[WPP] Logout da instancia '{}' (tenant {})", instance, TenantContext.getCurrent());
        } catch (WebClientResponseException.NotFound ignored) {
            // ja desconectada
        } catch (Exception e) {
            log.warn("[WPP] Falha no logout de '{}': {}", instance, e.getMessage());
        }
    }

    /**
     * Nome da instancia do tenant atual; deriva do slug e persiste quando ainda nao ha um definido,
     * para o dono nao precisar escolher nada.
     */
    public String ensureInstanceName() {
        Barbershop shop = currentShop();
        String instance = shop.getWhatsappInstance();
        if (instance == null || instance.isBlank()) {
            instance = shop.getSlug();
            shop.setWhatsappInstance(instance);
            barbershopRepository.save(shop);
            log.info("[WPP] Instancia derivada do slug '{}' para o tenant {}", instance, shop.getId());
        }
        return instance;
    }

    // --- Evolution API (chamadas sincronas; endpoints de admin, baixa frequencia) ---

    private String fetchState(String instance) {
        try {
            JsonNode node = getJson("/instance/connectionState/" + instance);
            if (node != null && node.path("instance").hasNonNull("state")) {
                return node.path("instance").get("state").asText();
            }
            return "close";
        } catch (WebClientResponseException.NotFound nf) {
            return "close"; // instancia ainda nao existe na Evolution
        } catch (Exception e) {
            log.warn("[WPP] Estado indisponivel para '{}': {}", instance, e.getMessage());
            return "unknown";
        }
    }

    private void createInstanceIfAbsent(String instance) {
        Map<String, Object> body = new HashMap<>();
        body.put("instanceName", instance);
        body.put("integration", "WHATSAPP-BAILEYS");
        body.put("qrcode", true);
        try {
            webClient.post()
                    .uri("/instance/create")
                    .header("apikey", apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
            log.info("[WPP] Instancia '{}' criada na Evolution (tenant {})", instance, TenantContext.getCurrent());
        } catch (WebClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 403 || code == 409) {
                log.debug("[WPP] Instancia '{}' ja existe na Evolution", instance);
            } else {
                throw new UpstreamServiceException("Nao foi possivel criar a instancia de WhatsApp.");
            }
        } catch (Exception e) {
            throw new UpstreamServiceException("WhatsApp (Evolution) indisponivel no momento.");
        }
    }

    private JsonNode getJson(String path) {
        return webClient.get()
                .uri(path)
                .header("apikey", apiKey())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(TIMEOUT);
    }

    private Barbershop currentShop() {
        return barbershopRepository.findById(TenantContext.getCurrent())
                .orElseThrow(() -> new NotFoundException("Barbearia nao encontrada."));
    }

    private String apiKey() {
        String key = properties.getEvolution().getApiKey();
        return (key != null && !key.isBlank()) ? key : "barberbot";
    }
}
