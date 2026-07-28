package com.razor.api.repository;

import com.razor.api.model.Coupon;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    List<Coupon> findByBarbershopIdOrderByCreatedAtDesc(UUID barbershopId);

    /** Pagina da tela de Cupons — a fidelidade emite sozinha, entao a lista so cresce. */
    Page<Coupon> findByBarbershopId(UUID barbershopId, Pageable pageable);

    /** O caixa digita o codigo sem se preocupar com maiuscula/minuscula. */
    Optional<Coupon> findByBarbershopIdAndCodeIgnoreCase(UUID barbershopId, String code);

    Optional<Coupon> findByIdAndBarbershopId(UUID id, UUID barbershopId);
}
