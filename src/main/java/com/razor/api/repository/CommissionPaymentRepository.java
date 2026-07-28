package com.razor.api.repository;

import com.razor.api.model.CommissionPayment;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommissionPaymentRepository extends JpaRepository<CommissionPayment, UUID> {

    List<CommissionPayment> findByBarbershopIdOrderByPaidAtDesc(UUID barbershopId);

    /** Pagina do historico de acertos pagos. */
    Page<CommissionPayment> findByBarbershopId(UUID barbershopId, Pageable pageable);

    /**
     * Pagamentos <b>contidos</b> na janela consultada: e o que responde "quanto ja paguei
     * referente a este intervalo?" sem ambiguidade de sobreposicao parcial.
     */
    List<CommissionPayment> findByBarbershopIdAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqual(
            UUID barbershopId, LocalDate from, LocalDate to);
}
