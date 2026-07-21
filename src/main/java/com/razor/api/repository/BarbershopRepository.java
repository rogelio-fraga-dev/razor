package com.razor.api.repository;

import com.razor.api.model.Barbershop;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BarbershopRepository extends JpaRepository<Barbershop, UUID> {

    Optional<Barbershop> findBySlug(String slug);

    Optional<Barbershop> findByWhatsappInstance(String whatsappInstance);

    List<Barbershop> findByActiveTrue();
}
