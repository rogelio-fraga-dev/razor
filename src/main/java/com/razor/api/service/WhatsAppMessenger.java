package com.razor.api.service;

import com.razor.api.util.ConsoleText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Envio de mensagens de texto pelo WhatsApp com logging padronizado por papel
 * (ADMIN / CLIENTE / DISPARO / SISTEMA). Concentra o par log+envio que antes
 * estava duplicado no {@code OrchestratorService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppMessenger {

    private final WhatsAppService whatsAppService;

    public void send(String phone, String text, String role) {
        log.info("[{} OUT] Enviando: '{}'", role, ConsoleText.sanitize(text.replace("\n", " ")));
        whatsAppService.sendTextMessage(phone, text);
    }

    /** Como {@link #send}, mas espera a confirmacao e lanca se a mensagem nao sair. */
    public void sendSync(String phone, String text, String role) {
        log.info("[{} OUT] Enviando (sync): '{}'", role, ConsoleText.sanitize(text.replace("\n", " ")));
        whatsAppService.sendTextMessageSync(phone, text);
    }
}
