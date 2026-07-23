package com.razor.api.controller;

import com.razor.api.dto.WhatsAppConnectResponse;
import com.razor.api.dto.WhatsAppStatusResponse;
import com.razor.api.service.WhatsAppConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Conexao de WhatsApp da barbearia pelo painel (tenant vem do JWT): consultar estado, gerar o QR
 * para parear pelo app e desconectar — tudo sem abrir a Evolution.
 */
@RestController
@RequestMapping("/api/admin/whatsapp")
@RequiredArgsConstructor
public class AdminWhatsAppController {

    private final WhatsAppConnectionService whatsAppConnectionService;

    @GetMapping("/status")
    public WhatsAppStatusResponse status() {
        return whatsAppConnectionService.status();
    }

    @PostMapping("/connect")
    public WhatsAppConnectResponse connect() {
        return whatsAppConnectionService.connect();
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Void> disconnect() {
        whatsAppConnectionService.disconnect();
        return ResponseEntity.noContent().build();
    }
}
