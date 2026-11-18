package com.razor.api.repository;

import com.razor.api.model.ServicePackage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServicePackageRepository extends JpaRepository<ServicePackage, UUID> {

    List<ServicePackage> findByBarbershopIdOrderByNameAsc(UUID barbershopId);

    Optional<ServicePackage> findByIdAndBarbershopId(UUID id, UUID barbershopId);

    Optional<ServicePackage> findByBarbershopIdAndNameIgnoreCase(UUID barbershopId, String name);
}
