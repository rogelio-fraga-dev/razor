package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Coupon;
import com.razor.api.model.CouponRedemption;
import com.razor.api.repository.CouponRedemptionRepository;
import com.razor.api.repository.CouponRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Testes de cupom (V29): calculo do desconto e as recusas com motivo. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouponServiceTest {

    @Mock private CouponRepository couponRepository;
    @Mock private CouponRedemptionRepository redemptionRepository;

    @InjectMocks private CouponService service;

    private final LocalDate hoje = AppointmentDates.today();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Coupon coupon(Coupon.DiscountType type, String value) {
        return Coupon.builder()
                .id(UUID.randomUUID()).barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .code("VOLTA10").discountType(type).discountValue(new BigDecimal(value))
                .origin(Coupon.Origin.MANUAL).usedCount(0).active(true).build();
    }

    private void givenCoupon(Coupon c) {
        when(couponRepository.findByBarbershopIdAndCodeIgnoreCase(any(), eq("VOLTA10")))
                .thenReturn(Optional.of(c));
    }

    // ---------- cálculo ----------

    @Test
    @DisplayName("desconto percentual sobre o total")
    void percentDiscount() {
        givenCoupon(coupon(Coupon.DiscountType.PERCENT, "10"));

        var v = service.validate("VOLTA10", new BigDecimal("100.00"), null, hoje);

        assertThat(v.discount()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("desconto de valor fixo")
    void fixedDiscount() {
        givenCoupon(coupon(Coupon.DiscountType.FIXED, "15"));

        var v = service.validate("VOLTA10", new BigDecimal("100.00"), null, hoje);

        assertThat(v.discount()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("valor fixo maior que a conta não deixa o total negativo")
    void fixedDiscount_neverExceedsTotal() {
        givenCoupon(coupon(Coupon.DiscountType.FIXED, "50"));

        var v = service.validate("VOLTA10", new BigDecimal("30.00"), null, hoje);

        assertThat(v.discount()).isEqualByComparingTo("30.00"); // e não 50
    }

    @Test
    @DisplayName("código é aceito em qualquer caixa (maiúscula/minúscula)")
    void codeIsCaseInsensitive() {
        givenCoupon(coupon(Coupon.DiscountType.PERCENT, "10"));

        assertThat(service.validate("volta10", new BigDecimal("100.00"), null, hoje).discount())
                .isEqualByComparingTo("10.00");
    }

    // ---------- recusas, cada uma com o seu motivo ----------

    @Test
    @DisplayName("cupom inexistente")
    void unknownCoupon_notFound() {
        when(couponRepository.findByBarbershopIdAndCodeIgnoreCase(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validate("XXX", new BigDecimal("50"), null, hoje))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("cupom desativado")
    void inactiveCoupon_isRejected() {
        Coupon c = coupon(Coupon.DiscountType.PERCENT, "10");
        c.setActive(false);
        givenCoupon(c);

        assertThatThrownBy(() -> service.validate("VOLTA10", new BigDecimal("50"), null, hoje))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("desativado");
    }

    @Test
    @DisplayName("cupom fora da validade")
    void expiredCoupon_isRejected() {
        Coupon c = coupon(Coupon.DiscountType.PERCENT, "10");
        c.setValidUntil(hoje.minusDays(1));
        givenCoupon(c);

        assertThatThrownBy(() -> service.validate("VOLTA10", new BigDecimal("50"), null, hoje))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validade");
    }

    @Test
    @DisplayName("cupom que já atingiu o limite de usos")
    void exhaustedCoupon_isRejected() {
        Coupon c = coupon(Coupon.DiscountType.PERCENT, "10");
        c.setMaxUses(1);
        c.setUsedCount(1);
        givenCoupon(c);

        assertThatThrownBy(() -> service.validate("VOLTA10", new BigDecimal("50"), null, hoje))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limite de usos");
    }

    @Test
    @DisplayName("cupom pessoal não vale para outro cliente")
    void personalCoupon_rejectsOtherCustomer() {
        Coupon c = coupon(Coupon.DiscountType.PERCENT, "10");
        c.setCustomerPhone("5511111");
        givenCoupon(c);

        assertThatThrownBy(() -> service.validate("VOLTA10", new BigDecimal("50"), "5522222", hoje))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pessoal");
    }

    @Test
    @DisplayName("cupom pessoal vale para o dono dele")
    void personalCoupon_acceptsOwner() {
        Coupon c = coupon(Coupon.DiscountType.PERCENT, "10");
        c.setCustomerPhone("5511111");
        givenCoupon(c);

        assertThat(service.validate("VOLTA10", new BigDecimal("50"), "5511111", hoje).discount())
                .isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("conta abaixo do mínimo do cupom")
    void belowMinimum_isRejected() {
        Coupon c = coupon(Coupon.DiscountType.FIXED, "20");
        c.setMinAmount(new BigDecimal("100"));
        givenCoupon(c);

        assertThatThrownBy(() -> service.validate("VOLTA10", new BigDecimal("50"), null, hoje))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a partir de");
    }

    // ---------- uso ----------

    @Test
    @DisplayName("usar o cupom incrementa o contador e grava o histórico")
    void redeem_incrementsAndRecords() {
        Coupon c = coupon(Coupon.DiscountType.PERCENT, "10");
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));
        when(redemptionRepository.save(any(CouponRedemption.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID comandaId = UUID.randomUUID();
        CouponRedemption r = service.redeem(c, new BigDecimal("10.00"), comandaId, "5511999");

        assertThat(c.getUsedCount()).isEqualTo(1);
        assertThat(r.getAmount()).isEqualByComparingTo("10.00");
        assertThat(r.getComandaId()).isEqualTo(comandaId);
    }

    // ---------- fidelidade ----------

    @Test
    @DisplayName("recompensa de fidelidade é pessoal, de uso único e com prazo")
    void fidelityReward_isPersonalSingleUseAndExpires() {
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        Coupon c = service.issueFidelityReward("5511999");

        assertThat(c.getOrigin()).isEqualTo(Coupon.Origin.FIDELIDADE);
        assertThat(c.getCustomerPhone()).isEqualTo("5511999");
        assertThat(c.getMaxUses()).isEqualTo(1);
        assertThat(c.getValidUntil()).isAfter(hoje);
        assertThat(c.getCode()).startsWith("FIDELIDADE-");
    }

    // ---------- criação ----------

    @Test
    @DisplayName("código repetido é recusado")
    void create_duplicateCode_isRejected() {
        when(couponRepository.findByBarbershopIdAndCodeIgnoreCase(any(), any()))
                .thenReturn(Optional.of(coupon(Coupon.DiscountType.PERCENT, "10")));

        assertThatThrownBy(() -> service.create("volta10", null, Coupon.DiscountType.PERCENT,
                new BigDecimal("10"), null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ja existe");

        verify(couponRepository, never()).save(any());
    }

    @Test
    @DisplayName("percentual acima de 100 e validade invertida são recusados")
    void create_invalidValues_areRejected() {
        when(couponRepository.findByBarbershopIdAndCodeIgnoreCase(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("A", null, Coupon.DiscountType.PERCENT,
                new BigDecimal("150"), null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create("B", null, Coupon.DiscountType.FIXED,
                new BigDecimal("10"), hoje.plusDays(5), hoje, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(couponRepository, never()).save(any());
    }

    @Test
    @DisplayName("código é normalizado para maiúsculas")
    void create_normalizesCode() {
        when(couponRepository.findByBarbershopIdAndCodeIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        Coupon c = service.create(" volta10 ", "Campanha", Coupon.DiscountType.PERCENT,
                new BigDecimal("10"), null, null, null, null, null);

        assertThat(c.getCode()).isEqualTo("VOLTA10");
    }
}
