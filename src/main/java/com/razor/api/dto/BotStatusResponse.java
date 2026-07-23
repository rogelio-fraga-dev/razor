package com.razor.api.dto;

/**
 * Saude do bot do tenant para o painel: conexao do WhatsApp, IA configurada e o liga/desliga.
 * E a resposta do "esta tudo ok?" que o dono ve nas Configuracoes.
 */
public record BotStatusResponse(
        String instanceName,
        String whatsappState,
        boolean whatsappConnected,
        boolean aiConfigured,
        boolean botEnabled) {
}
