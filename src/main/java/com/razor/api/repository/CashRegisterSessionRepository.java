package com.razor.api.repository;

import com.razor.api.model.CashRegisterSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CashRegisterSessionRepository extends JpaRepository<CashRegisterSession, UUID> {

    Optional<CashRegisterSession> findByBarbershopIdAndStatus(UUID barbershopId, CashRegisterSession.Status status);

    List<CashRegisterSession> findByBarbershopIdOrderByOpenedAtDesc(UUID barbershopId);
}
