package com.razor.api.dto;

import com.razor.api.model.CashMovement;
import com.razor.api.model.CashMovementType;
import com.razor.api.model.CashPaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Retrato completo de uma sessão de caixa para o painel: a sessão, seus movimentos e os totais
 * já calculados (por forma de pagamento, sangrias, suprimentos e a <b>gaveta esperada</b>).
 */
public record CashSessionSummary(
        UUID id,
        String status,
        String openedByName,
        LocalDateTime openedAt,
        BigDecimal openingAmount,
        String closedByName,
        LocalDateTime closedAt,
        BigDecimal closingCounted,
        BigDecimal closingExpected,
        String notes,
        List<MovementView> movements,
        Totals totals,
        /** Dinheiro que deveria estar na gaveta agora (fundo + suprimentos - sangrias + vendas em dinheiro). */
        BigDecimal expectedDrawer,
        /** Contado - esperado no fechamento (sobra positiva, falta negativa); nulo enquanto aberto. */
        BigDecimal difference) {

    public record MovementView(
            UUID id,
            CashMovementType type,
            CashPaymentMethod paymentMethod,
            BigDecimal amount,
            String description,
            LocalDateTime createdAt) {

        public static MovementView from(CashMovement m) {
            return new MovementView(m.getId(), m.getType(), m.getPaymentMethod(),
                    m.getAmount(), m.getDescription(), m.getCreatedAt());
        }
    }

    public record Totals(
            BigDecimal vendasDinheiro,
            BigDecimal vendasPix,
            BigDecimal vendasDebito,
            BigDecimal vendasCredito,
            BigDecimal vendasTotal,
            BigDecimal sangrias,
            BigDecimal suprimentos) {
    }
}
