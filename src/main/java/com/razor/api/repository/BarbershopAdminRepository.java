package com.razor.api.repository;

import com.razor.api.model.BarbershopAdmin;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BarbershopAdminRepository extends JpaRepository<BarbershopAdmin, UUID> {

    List<BarbershopAdmin> findByBarbershopId(UUID barbershopId);

    boolean existsByBarbershopIdAndPhone(UUID barbershopId, String phone);
}
