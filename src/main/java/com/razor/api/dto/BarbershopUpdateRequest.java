package com.razor.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Edicao do perfil da barbearia (o slug e imutavel). A instancia de WhatsApp NAO e editavel pelo
 * painel: ela roteia o webhook para o tenant, entao deixar o dono escolhe-la permitiria apontar
 * para a instancia de outra barbearia e receber as mensagens dela. Ela e definida no onboarding ou
 * derivada do slug ao conectar (ver WhatsAppConnectionService). Um campo extra no JSON e ignorado.
 */
public record BarbershopUpdateRequest(
        @NotBlank @Size(max = 255) String name) {
}
