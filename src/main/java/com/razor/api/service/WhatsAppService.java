package com.razor.api.service;

import com.razor.api.client.EvolutionClient;
import com.razor.api.dto.MessageDTO;
import com.razor.api.tenant.TenantResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Camada de envio pelo WhatsApp (Evolution API) com envio assincrono e tratamento de erro.
 *
 * <p><b>Roteamento multi-tenant:</b> o envio real roda em {@code CompletableFuture.runAsync},
 * que NAO propaga o TenantContext (ThreadLocal). Por isso a instancia de saida do tenant e
 * resolvida AQUI, na thread do caller (que tem o tenant setado), e capturada para dentro do
 * lambda assincrono.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppService {

    private final EvolutionClient evolutionClient;
    private final TenantResolver tenantResolver;

    public void sendTextMessage(String phone, String message) {
        String instance = tenantResolver.outboundInstance();
        CompletableFuture.runAsync(() -> {
            try {
                evolutionClient.sendTextMessage(instance, phone, message).block();
            } catch (Exception e) {
                log.error("Erro ao enviar mensagem para {}: {}", phone, e.getMessage(), e);
            }
        });
    }

    public void sendMessage(MessageDTO messageDTO) {
        String instance = tenantResolver.outboundInstance();
        CompletableFuture.runAsync(() -> {
            try {
                evolutionClient.sendMessage(instance, messageDTO).block();
            } catch (Exception e) {
                log.error("Erro ao enviar mensagem: {}", e.getMessage(), e);
            }
        });
    }

    public void sendImage(String phone, String imageUrl, String caption) {
        String instance = tenantResolver.outboundInstance();
        CompletableFuture.runAsync(() -> {
            try {
                evolutionClient.sendImageMessage(instance, phone, imageUrl, caption).block();
            } catch (Exception e) {
                log.error("Erro ao enviar imagem para {}: {}", phone, e.getMessage(), e);
            }
        });
    }

    public void sendMenuList(String phone, int points) {
        sendTextMessage(phone, MenuOptions.getMenuAsText(points));
    }

    /**
     * Envia lista interativa (Botões) — sincrono, entao a instancia do tenant e resolvida direto.
     */
    public void sendListMessage(String phone, String title, String description,
                                String buttonText, String footerText,
                                List<Map<String, Object>> sections) {
        String instance = tenantResolver.outboundInstance();
        try {
            evolutionClient.sendListMessage(instance, phone, title, description, buttonText, footerText, sections).block();
        } catch (Exception e) {
            log.error("Erro ao enviar lista interativa para {}: {}", phone, e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
