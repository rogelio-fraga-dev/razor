package com.razor.api.repository;

import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, UUID> {

    Optional<TenantUser> findByEmail(String email);

    boolean existsByEmail(String email);

    List<TenantUser> findByBarbershopIdOrderByNameAsc(UUID barbershopId);

    Optional<TenantUser> findByBarbershopIdAndProfessionalId(UUID barbershopId, UUID professionalId);

    /**
     * Usuarios ativos de um papel no tenant, com trava de escrita (FOR UPDATE). Usado na regra
     * "a barbearia nunca fica sem dono ativo" para serializar rebaixamentos simultaneos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from TenantUser u where u.barbershopId = :shop and u.role = :role and u.active = true")
    List<TenantUser> lockActiveByRole(@Param("shop") UUID barbershopId, @Param("role") UserRole role);
}
