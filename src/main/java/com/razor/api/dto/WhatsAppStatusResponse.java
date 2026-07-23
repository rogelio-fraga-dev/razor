package com.razor.api.dto;

/**
 * Estado da conexao de WhatsApp (instancia da Evolution) do tenant, para o painel web.
 * {@code state}: "open" (conectado), "connecting" (aguardando QR), "close" (desconectado) ou
 * "unknown" (Evolution indisponivel). {@code connected} = atalho para state == "open".
 */
public record WhatsAppStatusResponse(String instanceName, String state, boolean connected) {
}
