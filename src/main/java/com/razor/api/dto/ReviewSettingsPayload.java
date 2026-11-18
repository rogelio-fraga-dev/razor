package com.razor.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Avaliacao pos-atendimento (V46). Endpoint proprio pelo mesmo motivo do anti-falta: o PUT das
 * configuracoes do bot troca o registro inteiro.
 *
 * @param googleReviewUrl link de avaliacao do Google (vazio = sem link; so agradece)
 * @param delayMinutes    minutos depois do fechamento da comanda (15 min a 24 h)
 */
public record ReviewSettingsPayload(
        @NotNull Boolean enabled,
        @Size(max = 2048) String googleReviewUrl,
        @NotNull @Min(15) @Max(1440) Integer delayMinutes) {
}
