package com.razor.api.repository;

import com.razor.api.model.AiImpactEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiImpactEventRepository extends JpaRepository<AiImpactEvent, UUID> {

    /** Eventos do tipo no intervalo [from, to). */
    long countByBarbershopIdAndEventTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID barbershopId, AiImpactEvent.Type eventType, LocalDateTime from, LocalDateTime to);
}
