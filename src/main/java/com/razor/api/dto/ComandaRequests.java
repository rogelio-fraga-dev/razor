package com.razor.api.dto;

import com.razor.api.model.CashPaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** Payloads da comanda (V22). Precos nunca vem do cliente — quem manda e o catalogo. */
public final class ComandaRequests {

    private ComandaRequests() {}

    /** Abertura: tudo opcional (comanda de balcao pode nao ter nome nem barbeiro). */
    public record Open(String customerName, String customerPhone, UUID professionalId) {}

    /** Item entrando na conta. O tipo vem da rota (servico ou produto). */
    public record AddItem(
            @NotNull(message = "Informe o item.") UUID referenceId,
            @NotNull(message = "Informe a quantidade.")
                    @Positive(message = "A quantidade deve ser maior que zero.")
                    Integer quantity) {}

    /** Fechamento: so a forma de pagamento; o total vem dos itens. */
    public record Close(
            @NotNull(message = "Escolha a forma de pagamento da comanda.") CashPaymentMethod paymentMethod) {}
}
