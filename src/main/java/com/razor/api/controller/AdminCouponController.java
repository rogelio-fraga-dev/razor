package com.razor.api.controller;

import com.razor.api.dto.CouponRequests;
import com.razor.api.dto.CouponResponse;
import com.razor.api.model.Coupon;
import com.razor.api.service.CouponService;
import com.razor.api.util.AppointmentDates;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Cupons de desconto no painel web (tenant vem do JWT): campanhas do dono e as recompensas
 * emitidas pelo cartao fidelidade (V29).
 */
@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponService couponService;

    @GetMapping
    public List<CouponResponse> list() {
        LocalDate hoje = AppointmentDates.today();
        return couponService.list().stream().map(c -> CouponResponse.from(c, hoje)).toList();
    }

    @PostMapping
    public ResponseEntity<CouponResponse> create(@Valid @RequestBody CouponRequests.Create req) {
        Coupon created = couponService.create(req.code(), req.description(), req.discountType(),
                req.discountValue(), req.validFrom(), req.validUntil(), req.maxUses(),
                req.minAmount(), req.customerPhone());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CouponResponse.from(created, AppointmentDates.today()));
    }

    /** Liga/desliga o cupom sem apagar — o historico de uso precisa continuar existindo. */
    @PutMapping("/{id}/active")
    public CouponResponse setActive(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        return CouponResponse.from(couponService.setActive(id, active), AppointmentDates.today());
    }

    /**
     * Simula o cupom numa conta: devolve o desconto e o total final, ou o motivo da recusa.
     * E o que o caixa consulta antes de fechar.
     */
    @PostMapping("/preview")
    public Map<String, Object> preview(@Valid @RequestBody CouponRequests.Preview req) {
        CouponService.Validation v = couponService.validate(
                req.code(), req.total(), req.customerPhone(), AppointmentDates.today());
        BigDecimal finalTotal = req.total().subtract(v.discount());
        return Map.of(
                "code", v.coupon().getCode(),
                "discount", v.discount(),
                "finalTotal", finalTotal);
    }

    /** Quanto a barbearia deu de desconto por cupom no periodo. */
    @GetMapping("/total-discount")
    public Map<String, Object> totalDiscount(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate hoje = AppointmentDates.today();
        LocalDate start = from != null ? from : hoje.withDayOfMonth(1);
        LocalDate end = to != null ? to : hoje;
        return Map.of("from", start, "to", end, "total", couponService.totalDiscountBetween(start, end));
    }
}
