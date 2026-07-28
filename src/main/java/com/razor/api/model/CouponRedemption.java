package com.razor.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Um uso de cupom (V29). Mapeia {@code coupon_redemptions}.
 *
 * <p>Existe como linha, e nao so como contador no cupom, porque sem historico nao da para
 * responder "quanto demos de desconto neste mes?" — que e a pergunta que o dono faz.
 */
@Entity
@Table(name = "coupon_redemptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Column(name = "coupon_id", nullable = false)
    private UUID couponId;

    @Column(name = "comanda_id")
    private UUID comandaId;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    /** Desconto concedido de fato, congelado no uso. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "redeemed_at", nullable = false)
    private LocalDateTime redeemedAt;

    @Column(name = "redemption_date", nullable = false)
    private LocalDate redemptionDate;
}
