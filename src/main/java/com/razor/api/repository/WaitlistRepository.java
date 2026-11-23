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

    // V47: so quem ainda espera conta (BOOKED/CANCELLED ficam de historico).
    List<WaitlistEntry> findByBarbershopIdAndStatusOrderByCreatedAtAsc(UUID barbershopId, WaitlistEntry.Status status);
    List<WaitlistEntry> findByBarbershopIdAndCustomerPhoneAndStatus(UUID barbershopId, String customerPhone,
                                                                    WaitlistEntry.Status status);
    long countByBarbershopIdAndStatus(UUID barbershopId, WaitlistEntry.Status status);
}
