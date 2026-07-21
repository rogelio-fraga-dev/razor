package com.razor.api.repository;

import com.razor.api.model.Professional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfessionalRepository extends JpaRepository<Professional, UUID> {

    List<Professional> findByBarbershopId(UUID barbershopId);

    List<Professional> findByBarbershopIdAndActiveTrue(UUID barbershopId);
}
