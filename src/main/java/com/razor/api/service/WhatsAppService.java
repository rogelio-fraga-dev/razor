package com.razor.api.service;

import com.razor.api.client.EvolutionClient;
import com.razor.api.dto.MessageDTO;
import com.razor.api.tenant.TenantContext;
import com.razor.api.tenant.TenantResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    /** Sucesso/falha de envio por barbearia para o monitor de saude (V50). Nulo so em teste. */
    private final HealthSignals healthSignals;

    /** Tempo maximo esperando a Evolution confirmar um envio sincrono. */
    static final Duration SYNC_SEND_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Envio "dispara e esquece" — para respostas de chat, onde nao ha o que fazer com a falha
     * alem de logar. Quem precisa SABER se a mensagem saiu (tarefa agendada, cupom, disparo em
     * massa) deve usar {@link #sendTextMessageSync}.
     */
    public void sendTextMessage(String phone, String message) {
        String instance = tenantResolver.outboundInstance();
        // O tenant vai capturado junto: o ThreadLocal nao atravessa o runAsync.
        UUID shop = currentShop();
        CompletableFuture.runAsync(() -> {
            try {
                evolutionClient.sendTextMessage(instance, phone, message).block(SYNC_SEND_TIMEOUT);
                recordSend(shop, true);
            } catch (Exception e) {
                recordSend(shop, false);
                log.error("Erro ao enviar mensagem para {}: {}", phone, e.getMessage(), e);
            }
        });
    }

    /**
     * Envia e espera a Evolution aceitar. Falha (rede, instancia desconectada, timeout) sobe
     * como {@link WhatsAppSendException} — o chamador decide marcar FAILED, contar como nao
     * enviado, etc. Antes so existia o envio assincrono, entao todo chamador achava que tinha
     * enviado.
     */
    public void sendTextMessageSync(String phone, String message) {
        String instance = tenantResolver.outboundInstance();
        UUID shop = currentShop();
        try {
            evolutionClient.sendTextMessage(instance, phone, message).block(SYNC_SEND_TIMEOUT);
            recordSend(shop, true);
        } catch (Exception e) {
            recordSend(shop, false);
            throw new WhatsAppSendException("Falha ao enviar mensagem para " + phone + ": " + e.getMessage(), e);
        }
    }

    private static UUID currentShop() {
        return TenantContext.isSet() ? TenantContext.getCurrent() : null;
    }

    private void recordSend(UUID shop, boolean ok) {
        if (healthSignals == null || shop == null) return;
        if (ok) healthSignals.sendSuccess(shop);
        else healthSignals.sendFailure(shop);
    }

    /**
     * Indicador "digitando..." (V46): avisa o cliente que a mensagem chegou enquanto o audio e
     * transcrito e a IA responde. Assincrono e sem garantia — falha so vai para o log de debug.
     */
    public void sendPresence(String phone, String presence, int delayMs) {
        String instance = tenantResolver.outboundInstance();
        CompletableFuture.runAsync(() -> {
            try {
                evolutionClient.sendPresence(instance, phone, presence, delayMs).block(SYNC_SEND_TIMEOUT);
            } catch (Exception e) {
                log.debug("Presenca '{}' para {} nao saiu: {}", presence, phone, e.getMessage());
            }
        });
    }

    /** Envio sincrono que falhou. */
    public static class WhatsAppSendException extends RuntimeException {
        public WhatsAppSendException(String message, Throwable cause) {
            super(message, cause);
        }
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
