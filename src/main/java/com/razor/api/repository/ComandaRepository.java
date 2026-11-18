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

    /** Comandas de um intervalo — base do faturamento de servico avulso (V28). */
    List<Comanda> findByBarbershopIdAndComandaDateBetween(UUID barbershopId, LocalDate from, LocalDate to);

    /** Leitura escopada no tenant: nao le comanda de outra barbearia por id adivinhado. */
    Optional<Comanda> findByIdAndBarbershopId(UUID id, UUID barbershopId);

    /** Ja existe comanda (em qualquer situacao) vinculada a este agendamento? */
    boolean existsByAppointmentId(Long appointmentId);
}
