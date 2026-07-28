package com.razor.api.repository;

import com.razor.api.model.StockMovement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    /** Extrato de um produto, do mais recente para o mais antigo. */
    List<StockMovement> findByProductIdOrderByCreatedAtDesc(UUID productId);

    List<StockMovement> findTop50ByBarbershopIdOrderByCreatedAtDesc(UUID barbershopId);
}
