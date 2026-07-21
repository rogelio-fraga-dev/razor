package com.razor.api.repository;

import com.razor.api.model.TenantUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, UUID> {

    Optional<TenantUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
