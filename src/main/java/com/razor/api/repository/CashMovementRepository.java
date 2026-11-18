package com.razor.api.repository;

import com.razor.api.model.CashMovement;
import com.razor.api.model.CashMovementType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CashMovementRepository extends JpaRepository<CashMovement, UUID> {

    List<CashMovement> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    /** As partes do pagamento de uma comanda (V35) — uma por linha de pagamento. */
    List<CashMovement> findByComandaIdOrderByCreatedAtAsc(UUID comandaId);

    /** Movimentos de um tipo no intervalo [from, to) — base dos recebimentos por forma (V35). */
    List<CashMovement> findByBarbershopIdAndTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID barbershopId, CashMovementType type, LocalDateTime from, LocalDateTime to);
}
