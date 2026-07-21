package com.razor.api.repository;

import com.razor.api.model.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {
    List<WaitlistEntry> findByPreferredDayIgnoreCaseOrderByCreatedAtAsc(String preferredDay);
    void deleteByCustomerPhone(String customerPhone);

    // Acesso escopado por tenant (Fase 1).
    List<WaitlistEntry> findByBarbershopId(UUID barbershopId);
    boolean existsByBarbershopIdAndCustomerPhone(UUID barbershopId, String customerPhone);
    long countByBarbershopId(UUID barbershopId);
}
