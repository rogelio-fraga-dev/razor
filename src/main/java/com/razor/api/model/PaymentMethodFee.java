package com.razor.api.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Taxa percentual que a barbearia paga por forma de pagamento (V35) — a da maquininha no
 * crédito, por exemplo. Aplicada a cada VENDA no momento em que entra no caixa e gravada no
 * próprio movimento ({@link CashMovement#getFeeAmount()}). Mapeia {@code payment_method_fees}.
 */
@Entity
@Table(name = "payment_method_fees")
@IdClass(PaymentMethodFee.Key.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodFee {

    @Id
    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 12)
    private CashPaymentMethod paymentMethod;

    /** Pontos percentuais: {@code 3.50} = 3,5%. */
    @Column(name = "fee_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal feePercent;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private UUID barbershopId;
        private CashPaymentMethod paymentMethod;
    }
}
