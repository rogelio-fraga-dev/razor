package com.razor.api.repository;

import com.razor.api.model.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {
    List<WaitlistEntry> findByPreferredDayIgnoreCaseOrderByCreatedAtAsc(String preferredDay);
    boolean existsByCustomerPhone(String customerPhone);
    void deleteByCustomerPhone(String customerPhone);
}
