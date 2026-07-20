package com.razor.api.repository;

import com.razor.api.model.ServiceOffering;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {

    List<ServiceOffering> findByBarbershopId(UUID barbershopId);

    List<ServiceOffering> findByBarbershopIdAndActiveTrue(UUID barbershopId);
}
