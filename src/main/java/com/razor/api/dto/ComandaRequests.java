package com.razor.api.dto;

import com.razor.api.model.CashPaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Payloads da comanda (V22). Precos nunca vem do cliente — quem manda e o catalogo. */
public final class ComandaRequests {

    private ComandaRequests() {}

    /**
     * Abertura: tudo opcional (comanda de balcao pode nao ter nome nem barbeiro).
     * {@code appointmentId} vincula a comanda ao agendamento que a originou (V28) — com vinculo,
     * o financeiro nao conta o servico duas vezes.
     */
    public record Open(String customerName, String customerPhone, UUID professionalId,
                       Long appointmentId) {}

    /** Item entrando na conta. O tipo vem da rota (servico ou produto). */
    public record AddItem(
            @NotNull(message = "Informe o item.") UUID referenceId,
            @NotNull(message = "Informe a quantidade.")
                    @Positive(message = "A quantidade deve ser maior que zero.")
                    Integer quantity) {}

    /**
     * Fechamento: como foi pago e, opcionalmente, o cupom de desconto (V29). O total vem dos
     * itens; o cupom e validado antes de lancar no caixa.
     *
     * <p>Duas formas de dizer como foi pago (V35):
     * <ul>
     *   <li>{@code payments} — uma ou mais partes {@code {method, amount}} (split). A soma tem que
     *       bater exatamente com o total ja com o desconto do cupom.</li>
     *   <li>{@code paymentMethod} — forma unica, o contrato antigo: o total inteiro nela.</li>
     * </ul>
     * Com {@code payments} preenchido, {@code paymentMethod} e ignorado.
     */
    public record Close(
            CashPaymentMethod paymentMethod,
            String couponCode,
            @Valid List<PaymentPart> payments) {

        /** Construtor do contrato antigo (forma unica). */
        public Close(CashPaymentMethod paymentMethod, String couponCode) {
            this(paymentMethod, couponCode, null);
        }
    }

    /** Uma parte do pagamento: quanto entrou e por qual forma. */
    public record PaymentPart(
            @NotNull(message = "Escolha a forma de pagamento de cada parte.") CashPaymentMethod method,
            @NotNull(message = "Informe o valor de cada parte do pagamento.")
                    @Positive(message = "Cada parte do pagamento deve ser maior que zero.")
                    BigDecimal amount) {}

    /** Simulacao de cupom no fechamento: o total vem da propria comanda, nunca do cliente. */
    public record CouponPreview(@NotBlank(message = "Informe o cupom.") String code) {}

    /** Pre-visualizacao do fechamento (pacote + cupom). Cupom opcional; corpo vazio vale. */
    public record ClosePreview(String couponCode) {}
}
