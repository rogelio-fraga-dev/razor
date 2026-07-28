package com.razor.api.service;

import com.razor.api.model.Coupon;
import com.razor.api.model.CouponRedemption;
import com.razor.api.repository.CouponRedemptionRepository;
import com.razor.api.repository.CouponRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cupons de desconto (V29): campanhas criadas pelo dono e recompensas do cartao fidelidade.
 *
 * <p>Antes desta versao o cartao fidelidade mandava um codigo ao cliente que <b>nao existia em
 * lugar nenhum</b> — o barbeiro nao tinha como validar, nada impedia usar duas vezes e nao havia
 * registro do desconto dado. Aqui o cupom passa a ser uma entidade de verdade, com validade,
 * limite de uso e historico.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    /** Pontos do cartao fidelidade que disparam a recompensa. */
    public static final int FIDELITY_TARGET_POINTS = 10;
    /** Desconto da recompensa de fidelidade. */
    private static final BigDecimal FIDELITY_DISCOUNT_PERCENT = new BigDecimal("10");
    /** Prazo para usar a recompensa — cupom eterno vira passivo esquecido. */
    private static final int FIDELITY_VALID_DAYS = 90;

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;

    public List<Coupon> list() {
        return couponRepository.findByBarbershopIdOrderByCreatedAtDesc(TenantContext.getCurrent());
    }

    public List<CouponRedemption> redemptions(UUID couponId) {
        requireOwned(couponId);
        return redemptionRepository.findByCouponIdOrderByRedeemedAtDesc(couponId);
    }

    // ---------- criacao ----------

    @Transactional
    public Coupon create(String code, String description, Coupon.DiscountType type, BigDecimal value,
                         LocalDate validFrom, LocalDate validUntil, Integer maxUses,
                         BigDecimal minAmount, String customerPhone) {
        String cleanCode = normalizeCode(code);
        validate(type, value, validFrom, validUntil, maxUses);
        couponRepository.findByBarbershopIdAndCodeIgnoreCase(TenantContext.getCurrent(), cleanCode)
                .ifPresent(c -> {
                    throw new IllegalArgumentException("Ja existe um cupom com o codigo \"" + cleanCode + "\".");
                });

        Coupon saved = couponRepository.save(Coupon.builder()
                .barbershopId(TenantContext.getCurrent())
                .code(cleanCode)
                .description(blankToNull(description))
                .discountType(type)
                .discountValue(value)
                .origin(Coupon.Origin.MANUAL)
                .customerPhone(blankToNull(customerPhone))
                .validFrom(validFrom)
                .validUntil(validUntil)
                .maxUses(maxUses)
                .minAmount(minAmount)
                .active(true)
                .build());
        log.info("[CUPOM] '{}' criado no tenant {}", cleanCode, TenantContext.getCurrent());
        return saved;
    }

    /**
     * Emite a recompensa do cartao fidelidade para um cliente: cupom pessoal, de uso unico e com
     * prazo. E o codigo que o cliente recebe no WhatsApp — agora gravado, entao o caixa consegue
     * validar e a barbearia sabe quanto deu de desconto.
     */
    @Transactional
    public Coupon issueFidelityReward(String customerPhone) {
        String code = "FIDELIDADE-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase(Locale.ROOT);
        LocalDate hoje = AppointmentDates.today();

        Coupon saved = couponRepository.save(Coupon.builder()
                .barbershopId(TenantContext.getCurrent())
                .code(code)
                .description("Recompensa do cartao fidelidade (" + FIDELITY_TARGET_POINTS + " pontos)")
                .discountType(Coupon.DiscountType.PERCENT)
                .discountValue(FIDELITY_DISCOUNT_PERCENT)
                .origin(Coupon.Origin.FIDELIDADE)
                .customerPhone(customerPhone)
                .validFrom(hoje)
                .validUntil(hoje.plusDays(FIDELITY_VALID_DAYS))
                .maxUses(1)
                .active(true)
                .build());
        log.info("[CUPOM] Recompensa de fidelidade '{}' emitida para {}", code, customerPhone);
        return saved;
    }

    @Transactional
    public Coupon setActive(UUID couponId, boolean active) {
        Coupon coupon = requireOwned(couponId);
        coupon.setActive(active);
        return couponRepository.save(coupon);
    }

    // ---------- uso ----------

    /**
     * Confere se o cupom vale para esta conta e devolve quanto ele tira. Recusa com mensagem
     * especifica: no caixa, "cupom invalido" sem motivo vira discussao com o cliente.
     */
    public Validation validate(String code, BigDecimal total, String customerPhone, LocalDate date) {
        Coupon coupon = couponRepository
                .findByBarbershopIdAndCodeIgnoreCase(TenantContext.getCurrent(), normalizeCode(code))
                .orElseThrow(() -> new NotFoundException("Cupom nao encontrado."));

        if (!coupon.isActive()) {
            throw new IllegalArgumentException("Este cupom esta desativado.");
        }
        if (!coupon.isValidOn(date)) {
            throw new IllegalArgumentException("Este cupom esta fora do prazo de validade.");
        }
        if (!coupon.hasUsesLeft()) {
            throw new IllegalArgumentException("Este cupom ja atingiu o limite de usos.");
        }
        if (coupon.getCustomerPhone() != null
                && !coupon.getCustomerPhone().equals(customerPhone)) {
            throw new IllegalArgumentException("Este cupom e pessoal e nao pertence a este cliente.");
        }
        if (coupon.getMinAmount() != null && total.compareTo(coupon.getMinAmount()) < 0) {
            throw new IllegalArgumentException(
                    "Este cupom vale a partir de R$ " + coupon.getMinAmount() + ".");
        }

        return new Validation(coupon, coupon.discountFor(total));
    }

    /** Registra o uso: incrementa o contador e grava a linha do historico. */
    @Transactional
    public CouponRedemption redeem(Coupon coupon, BigDecimal amount, UUID comandaId, String customerPhone) {
        coupon.setUsedCount((coupon.getUsedCount() == null ? 0 : coupon.getUsedCount()) + 1);
        couponRepository.save(coupon);

        LocalDateTime now = LocalDateTime.now();
        CouponRedemption redemption = redemptionRepository.save(CouponRedemption.builder()
                .barbershopId(TenantContext.getCurrent())
                .couponId(coupon.getId())
                .comandaId(comandaId)
                .customerPhone(customerPhone)
                .amount(amount)
                .redeemedAt(now)
                .redemptionDate(AppointmentDates.today())
                .build());
        log.info("[CUPOM] '{}' usado: desconto de {} no tenant {}",
                coupon.getCode(), amount, TenantContext.getCurrent());
        return redemption;
    }

    /** Quanto a barbearia deu de desconto por cupom no periodo. */
    public BigDecimal totalDiscountBetween(LocalDate from, LocalDate to) {
        return redemptionRepository
                .findByBarbershopIdAndRedemptionDateBetween(TenantContext.getCurrent(), from, to).stream()
                .map(CouponRedemption::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Cupom validado com o desconto que ele daria naquela conta. */
    public record Validation(Coupon coupon, BigDecimal discount) {}

    // ---------- apoio ----------

    private Coupon requireOwned(UUID couponId) {
        return couponRepository.findByIdAndBarbershopId(couponId, TenantContext.getCurrent())
                .orElseThrow(() -> new NotFoundException("Cupom nao encontrado."));
    }

    private void validate(Coupon.DiscountType type, BigDecimal value, LocalDate from,
                          LocalDate until, Integer maxUses) {
        if (type == null) throw new IllegalArgumentException("Informe o tipo de desconto.");
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("O desconto deve ser maior que zero.");
        }
        if (type == Coupon.DiscountType.PERCENT && value.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("O desconto percentual nao pode passar de 100.");
        }
        if (from != null && until != null && until.isBefore(from)) {
            throw new IllegalArgumentException("O fim da validade nao pode ser antes do inicio.");
        }
        if (maxUses != null && maxUses <= 0) {
            throw new IllegalArgumentException("O limite de usos deve ser maior que zero.");
        }
    }

    private String normalizeCode(String code) {
        String clean = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (clean.isEmpty()) throw new IllegalArgumentException("Informe o codigo do cupom.");
        return clean;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
