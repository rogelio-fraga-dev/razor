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

    /**
     * A comanda travada para escrita ({@code SELECT ... FOR UPDATE}) — o fechamento comeca por
     * aqui. Dois cliques em "Fechar" (ou duas abas) esperam um pelo outro, e o segundo ve a
     * comanda ja FECHADA em vez de lancar o dinheiro de novo no caixa.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select c from Comanda c where c.id = :id and c.barbershopId = :shop")
    Optional<Comanda> lockByIdAndBarbershopId(@org.springframework.data.repository.query.Param("id") UUID id,
                                              @org.springframework.data.repository.query.Param("shop") UUID barbershopId);

    /** Comandas ligadas a estes agendamentos — a agenda mostra "comanda aberta" no bloco. */
    List<Comanda> findByBarbershopIdAndAppointmentIdIn(UUID barbershopId, java.util.Collection<Long> appointmentIds);
    /** Ja existe comanda (em qualquer situacao) vinculada a este agendamento? */
    boolean existsByAppointmentId(Long appointmentId);
}
