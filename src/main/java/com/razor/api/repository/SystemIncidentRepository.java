package com.razor.api.repository;

import com.razor.api.model.IncidentType;
import com.razor.api.model.SystemIncident;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Incidentes de saude (V50). Leitura do painel sempre filtrada pela barbearia do login. */
@Repository
public interface SystemIncidentRepository extends JpaRepository<SystemIncident, UUID> {

    /** O incidente ABERTO de (barbearia, tipo). */
    Optional<SystemIncident> findFirstByBarbershopIdAndTypeAndStatus(
            UUID barbershopId, IncidentType type, SystemIncident.Status status);

    /** O incidente ABERTO da plataforma (barbershop_id nulo) daquele tipo. */
    Optional<SystemIncident> findFirstByBarbershopIdIsNullAndTypeAndStatus(
            IncidentType type, SystemIncident.Status status);

    List<SystemIncident> findByStatus(SystemIncident.Status status);

    long countByStatus(SystemIncident.Status status);

    /** Abertos da barbearia + os da plataforma (que valem para todos). */
    /** Abertos da barbearia mais os da plataforma (barbershop_id nulo). */
    default List<SystemIncident> findOpenVisibleTo(UUID shop) {
        return findVisibleToWithStatus(shop, SystemIncident.Status.OPEN);
    }

    // Status por parametro: enum aninhado escrito na JPQL nao e resolvido pelo Hibernate 6.
    @Query("select i from SystemIncident i where i.status = :status "
            + "and (i.barbershopId = :shop or i.barbershopId is null) order by i.firstSeenAt desc")
    List<SystemIncident> findVisibleToWithStatus(@Param("shop") UUID shop, @Param("status") SystemIncident.Status status);

    /** Historico recente (abertos e resolvidos) da barbearia + plataforma. */
    @Query("select i from SystemIncident i where (i.barbershopId = :shop or i.barbershopId is null) "
            + "and i.lastSeenAt >= :since order by i.lastSeenAt desc")
    List<SystemIncident> findRecentVisibleTo(@Param("shop") UUID shop, @Param("since") LocalDateTime since,
                                             Pageable page);
}
