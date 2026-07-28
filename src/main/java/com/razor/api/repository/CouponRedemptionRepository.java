package com.razor.api.repository;

import com.razor.api.model.CouponRedemption;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, UUID> {

    List<CouponRedemption> findByCouponIdOrderByRedeemedAtDesc(UUID couponId);

    /** Base de "quanto demos de desconto no periodo". */
    List<CouponRedemption> findByBarbershopIdAndRedemptionDateBetween(
            UUID barbershopId, LocalDate from, LocalDate to);

    long countByCouponIdAndCustomerPhone(UUID couponId, String customerPhone);
}
