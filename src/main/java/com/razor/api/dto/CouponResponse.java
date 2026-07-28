package com.razor.api.dto;

import com.razor.api.model.Coupon;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Cupom como o painel consome. */
public record CouponResponse(
        UUID id,
        String code,
        String description,
        Coupon.DiscountType discountType,
        BigDecimal discountValue,
        Coupon.Origin origin,
        String customerPhone,
        LocalDate validFrom,
        LocalDate validUntil,
        Integer maxUses,
        int usedCount,
        BigDecimal minAmount,
        boolean active,
        /** Ainda da para usar hoje? Junta validade, limite de usos e o liga/desliga. */
        boolean usable) {

    public static CouponResponse from(Coupon c, LocalDate today) {
        boolean usable = c.isActive() && c.hasUsesLeft() && c.isValidOn(today);
        return new CouponResponse(c.getId(), c.getCode(), c.getDescription(), c.getDiscountType(),
                c.getDiscountValue(), c.getOrigin(), c.getCustomerPhone(), c.getValidFrom(),
                c.getValidUntil(), c.getMaxUses(), c.getUsedCount() == null ? 0 : c.getUsedCount(),
                c.getMinAmount(), c.isActive(), usable);
    }
}
