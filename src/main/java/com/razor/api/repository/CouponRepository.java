package com.razor.api.repository;

import com.razor.api.model.Coupon;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    List<Coupon> findByBarbershopIdOrderByCreatedAtDesc(UUID barbershopId);

    /** O caixa digita o codigo sem se preocupar com maiuscula/minuscula. */
    Optional<Coupon> findByBarbershopIdAndCodeIgnoreCase(UUID barbershopId, String code);

    Optional<Coupon> findByIdAndBarbershopId(UUID id, UUID barbershopId);
}
