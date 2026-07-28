package com.razor.api.dto;

import com.razor.api.model.Coupon;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** Payloads de cupom (V29). */
public final class CouponRequests {

    private CouponRequests() {}

    /**
     * Criacao de cupom. Datas, limite de usos, valor minimo e telefone sao opcionais:
     * sem telefone o cupom e de campanha aberta; sem limite, ilimitado.
     */
    public record Create(
            @NotBlank(message = "Informe o codigo do cupom.") String code,
            String description,
            @NotNull(message = "Informe o tipo de desconto.") Coupon.DiscountType discountType,
            @NotNull(message = "Informe o valor do desconto.")
                    @Positive(message = "O desconto deve ser maior que zero.") BigDecimal discountValue,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validUntil,
            @Positive(message = "O limite de usos deve ser maior que zero.") Integer maxUses,
            BigDecimal minAmount,
            String customerPhone) {}

    /** Simulacao: quanto este cupom tiraria de uma conta. */
    public record Preview(
            @NotBlank(message = "Informe o codigo do cupom.") String code,
            @NotNull(message = "Informe o total da conta.") BigDecimal total,
            String customerPhone) {}
}
