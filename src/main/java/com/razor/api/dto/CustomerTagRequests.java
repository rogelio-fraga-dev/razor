package com.razor.api.dto;

import com.razor.api.model.CustomerStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Pedidos do painel para tags de cliente e disparo por grupo (V36). */
public final class CustomerTagRequests {

    private CustomerTagRequests() {
    }

    /** Criar/editar tag. Cor nula = cinza padrao. */
    public record TagRequest(@NotBlank @Size(max = 40) String name, String color) {
    }

    /** Conjunto completo de tags do cliente (substitui o atual; vazio tira todas). */
    public record CustomerTagsRequest(List<UUID> tagIds) {
    }

    /**
     * Disparo para um grupo: tag e/ou status (nenhum dos dois = base toda). {@code promo} escolhe
     * o cabecalho "Promoção" em vez de "Aviso", como no WhatsApp do dono.
     */
    public record BroadcastRequest(@NotBlank @Size(max = 1000) String message, UUID tagId,
                                   CustomerStatus status, boolean promo) {
    }

    /** Quantos vao receber — mostrado antes de confirmar, e devolvido ao disparar. */
    public record BroadcastResponse(int recipients) {
    }
}
