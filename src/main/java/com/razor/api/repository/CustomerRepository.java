package com.razor.api.repository;

import com.razor.api.model.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    // Acesso escopado por tenant (Fase 1). O telefone e unico POR barbearia,
    // entao lookups por telefone/nome sempre passam o barbershop_id.
    Optional<Customer> findByBarbershopIdAndPhoneNumber(UUID barbershopId, String phoneNumber);

    Optional<Customer> findByBarbershopIdAndNameIgnoreCase(UUID barbershopId, String name);

    List<Customer> findByBarbershopId(UUID barbershopId);

    long countByBarbershopId(UUID barbershopId);
}
