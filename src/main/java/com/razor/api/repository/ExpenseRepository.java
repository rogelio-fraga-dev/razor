package com.razor.api.repository;

import com.razor.api.model.Expense;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    /** Despesas por competencia no intervalo — a consulta do DRE. */
    List<Expense> findByBarbershopIdAndDueDateBetween(UUID barbershopId, LocalDate from, LocalDate to);

    /** Tudo do tenant, do vencimento mais recente para o mais antigo (tela de Despesas). */
    List<Expense> findByBarbershopIdOrderByDueDateDesc(UUID barbershopId);

    /** Pagina da tela de Despesas — ~8 contas por mes acumulam rapido. */
    Page<Expense> findByBarbershopId(UUID barbershopId, Pageable pageable);

    /** Pagina so das contas em aberto. */
    Page<Expense> findByBarbershopIdAndPaidAtIsNull(UUID barbershopId, Pageable pageable);

    /** O que esta em aberto, do mais antigo para o mais novo — quem venceu primeiro aparece antes. */
    List<Expense> findByBarbershopIdAndPaidAtIsNullOrderByDueDateAsc(UUID barbershopId);

    /** Recorrentes com vencimento no intervalo — a base para gerar o mes seguinte. */
    List<Expense> findByBarbershopIdAndRecurringTrueAndDueDateBetween(
            UUID barbershopId, LocalDate from, LocalDate to);

    /** Trava de idempotencia na geracao do mes seguinte (espelha o indice unico parcial da V31). */
    boolean existsByBarbershopIdAndDescriptionAndDueDateAndRecurringTrue(
            UUID barbershopId, String description, LocalDate dueDate);
}
