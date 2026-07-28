package com.razor.api.repository;

import com.razor.api.model.ProfessionalAdvance;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfessionalAdvanceRepository extends JpaRepository<ProfessionalAdvance, UUID> {

    List<ProfessionalAdvance> findByBarbershopIdOrderByAdvanceDateDesc(UUID barbershopId);

    /** Pagina do historico de vales. */
    Page<ProfessionalAdvance> findByBarbershopId(UUID barbershopId, Pageable pageable);

    /** Vales do intervalo — o que sera descontado no acerto daquele periodo. */
    List<ProfessionalAdvance> findByBarbershopIdAndAdvanceDateBetween(
            UUID barbershopId, LocalDate from, LocalDate to);
}
