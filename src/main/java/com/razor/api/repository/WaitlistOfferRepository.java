package com.razor.api.repository;

import com.razor.api.model.WaitlistOffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WaitlistOfferRepository extends JpaRepository<WaitlistOffer, UUID> {

    /** A oferta aberta do cliente (no maximo uma — indice unico parcial). */
    Optional<WaitlistOffer> findFirstByBarbershopIdAndCustomerPhoneAndStatusOrderByCreatedAtDesc(
            UUID barbershopId, String customerPhone, WaitlistOffer.Status status);

    /** Tudo que ja foi oferecido daquela vaga (para o proximo da fila nao repetir ninguem). */
    List<WaitlistOffer> findByBarbershopIdAndSlotDateAndSlotTime(UUID barbershopId, LocalDate slotDate, LocalTime slotTime);

    /** Ofertas abertas cujo prazo venceu — de todos os tenants (o scheduler escopa cada uma). */
    List<WaitlistOffer> findByStatusAndExpiresAtBefore(WaitlistOffer.Status status, LocalDateTime now);

    boolean existsByBarbershopIdAndCustomerPhoneAndStatus(UUID barbershopId, String customerPhone,
                                                          WaitlistOffer.Status status);
}
