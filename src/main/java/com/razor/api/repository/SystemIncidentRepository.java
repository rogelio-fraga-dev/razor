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
    @Query("select i from SystemIncident i where i.status = com.razor.api.model.SystemIncident.Status.OPEN "
            + "and (i.barbershopId = :shop or i.barbershopId is null) order by i.firstSeenAt desc")
    List<SystemIncident> findOpenVisibleTo(@Param("shop") UUID shop);

    /** Historico recente (abertos e resolvidos) da barbearia + plataforma. */
    @Query("select i from SystemIncident i where (i.barbershopId = :shop or i.barbershopId is null) "
            + "and i.lastSeenAt >= :since order by i.lastSeenAt desc")
    List<SystemIncident> findRecentVisibleTo(@Param("shop") UUID shop, @Param("since") LocalDateTime since,
                                             Pageable page);
}
