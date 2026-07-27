package com.razor.api.repository;

import com.razor.api.model.Comanda;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComandaRepository extends JpaRepository<Comanda, UUID> {

    /** Comandas abertas do tenant — a tela do dia. */
    List<Comanda> findByBarbershopIdAndStatusOrderByOpenedAtAsc(UUID barbershopId, Comanda.Status status);

    List<Comanda> findByBarbershopIdAndComandaDateOrderByOpenedAtDesc(UUID barbershopId, LocalDate date);

    /** Leitura escopada no tenant: nao le comanda de outra barbearia por id adivinhado. */
    Optional<Comanda> findByIdAndBarbershopId(UUID id, UUID barbershopId);
}
