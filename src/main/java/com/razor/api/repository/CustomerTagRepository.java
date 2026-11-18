package com.razor.api.repository;

import com.razor.api.model.CustomerTag;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerTagRepository extends JpaRepository<CustomerTag, UUID> {

    List<CustomerTag> findByBarbershopIdOrderByNameAsc(UUID barbershopId);

    Optional<CustomerTag> findByIdAndBarbershopId(UUID id, UUID barbershopId);

    Optional<CustomerTag> findByBarbershopIdAndNameIgnoreCase(UUID barbershopId, String name);
}
