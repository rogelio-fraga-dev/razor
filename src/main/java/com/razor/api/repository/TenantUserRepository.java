package com.razor.api.repository;

import com.razor.api.model.TenantUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, UUID> {

    Optional<TenantUser> findByEmail(String email);

    boolean existsByEmail(String email);

    List<TenantUser> findByBarbershopIdOrderByNameAsc(UUID barbershopId);

    Optional<TenantUser> findByBarbershopIdAndProfessionalId(UUID barbershopId, UUID professionalId);
}
