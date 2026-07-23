package com.razor.api.dto;

/**
 * Resposta do "conectar WhatsApp" do painel. Quando ja conectado, {@code state} = "open" e o QR vem
 * nulo. Caso contrario, {@code qrCodeBase64} traz o QR (data URI PNG) para o dono escanear pelo app,
 * e {@code pairingCode} um codigo alternativo de pareamento quando a Evolution o fornece.
 */
public record WhatsAppConnectResponse(
        String instanceName, String state, String qrCodeBase64, String pairingCode) {
}
