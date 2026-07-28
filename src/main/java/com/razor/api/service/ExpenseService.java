package com.razor.api.service;

import com.razor.api.model.CashMovement;
import com.razor.api.model.CashMovementType;
import com.razor.api.model.Expense;
import com.razor.api.model.ExpenseCategory;
import com.razor.api.repository.ExpenseRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contas a pagar da barbearia (V31) — o outro lado do caixa.
 *
 * <p><b>Sair do caixa e opcional</b>, pelo mesmo criterio do acerto (V25) e do vale (V26):
 * despesa paga em especie vira {@link CashMovementType#SANGRIA}; boleto ou PIX da conta da
 * barbearia nao mexe na conferencia da gaveta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final CashRegisterService cashRegisterService;

    /** Tudo do tenant, do vencimento mais recente para o mais antigo. */
    public List<Expense> list() {
        return expenseRepository.findByBarbershopIdOrderByDueDateDesc(TenantContext.getCurrent());
    }

    /** Contas em aberto, do vencimento mais antigo para o mais novo. */
    public List<Expense> listUnpaid() {
        return expenseRepository.findByBarbershopIdAndPaidAtIsNullOrderByDueDateAsc(TenantContext.getCurrent());
    }

    /** Despesas por competencia no intervalo — a base do DRE. */
    public List<Expense> betweenDueDates(LocalDate from, LocalDate to) {
        return expenseRepository.findByBarbershopIdAndDueDateBetween(TenantContext.getCurrent(), from, to);
    }

    /**
     * Total por categoria no intervalo, por competencia. Categoria sem despesa nao aparece — o
     * relatorio mostra o que existe, nao uma lista de zeros.
     */
    public Map<ExpenseCategory, BigDecimal> totalsByCategory(LocalDate from, LocalDate to) {
        Map<ExpenseCategory, BigDecimal> byCategory = new EnumMap<>(ExpenseCategory.class);
        for (Expense e : betweenDueDates(from, to)) {
            byCategory.merge(e.getCategory(), e.getAmount(), BigDecimal::add);
        }
        return byCategory;
    }

    /**
     * Cadastra uma conta.
     *
     * @param paidAt           data do desembolso, ou {@code null} para lancar em aberto
     * @param fromCashRegister so vale com {@code paidAt} preenchido: exige caixa aberto e gera a
     *                         sangria. Conta em aberto nao tira dinheiro de gaveta nenhuma.
     */
    @Transactional
    public Expense create(String description, ExpenseCategory category, BigDecimal amount,
                          LocalDate dueDate, LocalDate paidAt, boolean fromCashRegister,
                          boolean recurring, String notes) {
        String desc = requireDescription(description);
        BigDecimal value = requireAmount(amount);
        if (category == null) throw new IllegalArgumentException("Informe a categoria da despesa.");
        LocalDate due = dueDate != null ? dueDate : AppointmentDates.today();

        UUID movementId = null;
        if (paidAt != null && fromCashRegister) {
            movementId = sangria(value, desc);
        }

        Expense saved = expenseRepository.save(Expense.builder()
                .barbershopId(TenantContext.getCurrent())
                .description(desc)
                .category(category)
                .amount(value)
                .dueDate(due)
                .paidAt(paidAt)
                .recurring(recurring)
                .notes(trimToNull(notes))
                .cashMovementId(movementId)
                .build());

        log.info("[DESPESA] '{}' de {} vencendo em {} ({}) no tenant {}",
                desc, value, due, paidAt != null ? "paga" : "em aberto", TenantContext.getCurrent());
        return saved;
    }

    /**
     * Marca uma conta como paga.
     *
     * @param fromCashRegister paga em especie: exige caixa aberto e gera a sangria.
     * @throws IllegalArgumentException se a conta ja estiver paga — repetir o comando nao pode
     *         gerar uma segunda sangria pela mesma conta.
     */
    @Transactional
    public Expense markPaid(UUID id, LocalDate paidAt, boolean fromCashRegister) {
        Expense expense = requireOwned(id);
        if (expense.isPaid()) {
            throw new IllegalArgumentException("Esta conta já está paga (em " + expense.getPaidAt() + ").");
        }
        expense.setPaidAt(paidAt != null ? paidAt : AppointmentDates.today());
        if (fromCashRegister) {
            expense.setCashMovementId(sangria(expense.getAmount(), expense.getDescription()));
        }
        Expense saved = expenseRepository.save(expense);
        log.info("[DESPESA] '{}' paga em {}{} no tenant {}", expense.getDescription(),
                expense.getPaidAt(), fromCashRegister ? " via caixa" : " por fora do caixa",
                TenantContext.getCurrent());
        return saved;
    }

    /**
     * Edita os dados de uma conta. Nao mexe em pagamento: quitar e um comando proprio, porque
     * pode mover dinheiro de verdade.
     */
    @Transactional
    public Expense update(UUID id, String description, ExpenseCategory category, BigDecimal amount,
                          LocalDate dueDate, boolean recurring, String notes) {
        Expense expense = requireOwned(id);
        if (expense.isPaid() && expense.getCashMovementId() != null) {
            // O valor ja virou sangria com este numero. Deixar editar reescreveria a despesa sem
            // reescrever o movimento, e a gaveta passaria a discordar do relatorio em silencio.
            throw new IllegalArgumentException(
                    "Esta conta já foi paga pelo caixa e não pode ser editada. "
                    + "Para corrigir, lance um suprimento e cadastre a conta certa.");
        }
        expense.setDescription(requireDescription(description));
        expense.setCategory(category != null ? category : expense.getCategory());
        expense.setAmount(requireAmount(amount));
        if (dueDate != null) expense.setDueDate(dueDate);
        expense.setRecurring(recurring);
        expense.setNotes(trimToNull(notes));
        return expenseRepository.save(expense);
    }

    /** Apaga uma conta. Recusa se o pagamento ja saiu da gaveta, pelo mesmo motivo do update. */
    @Transactional
    public void delete(UUID id) {
        Expense expense = requireOwned(id);
        if (expense.getCashMovementId() != null) {
            throw new IllegalArgumentException(
                    "Esta conta já foi paga pelo caixa e não pode ser apagada — o dinheiro saiu da "
                    + "gaveta e o movimento continuaria lá. Lance um suprimento para corrigir.");
        }
        expenseRepository.delete(expense);
        log.info("[DESPESA] '{}' apagada no tenant {}", expense.getDescription(), TenantContext.getCurrent());
    }

    /**
     * Copia as despesas recorrentes de {@code sourceMonth} para o mes seguinte, em aberto.
     *
     * <p><b>Idempotente e nunca automatico.</b> Rodar duas vezes nao duplica (a existencia e
     * checada por descricao + vencimento, e o indice unico parcial da V31 fecha a corrida). Ser
     * comando do dono e proposital: gerar sozinho encheria a tela de divida que ninguem conferiu,
     * e "o sistema criou uma conta que eu nao reconheco" e pior que digitar de novo.
     *
     * @return as contas criadas — vazio quando ja estavam todas la
     */
    @Transactional
    public List<Expense> repeatRecurring(LocalDate sourceMonth) {
        UUID shop = TenantContext.getCurrent();
        LocalDate start = sourceMonth.withDayOfMonth(1);
        LocalDate end = start.plusMonths(1).minusDays(1);

        List<Expense> created = new ArrayList<>();
        for (Expense origin : expenseRepository
                .findByBarbershopIdAndRecurringTrueAndDueDateBetween(shop, start, end)) {

            LocalDate nextDue = origin.getDueDate().plusMonths(1);
            if (expenseRepository.existsByBarbershopIdAndDescriptionAndDueDateAndRecurringTrue(
                    shop, origin.getDescription(), nextDue)) {
                continue;
            }
            created.add(expenseRepository.save(Expense.builder()
                    .barbershopId(shop)
                    .description(origin.getDescription())
                    .category(origin.getCategory())
                    .amount(origin.getAmount())
                    .dueDate(nextDue)
                    // Nasce EM ABERTO e sem vinculo com caixa: o mes que vem ainda nao foi pago.
                    .paidAt(null)
                    .cashMovementId(null)
                    .recurring(true)
                    .notes(origin.getNotes())
                    .build()));
        }
        log.info("[DESPESA] Repeticao de {} gerou {} conta(s) para o mes seguinte no tenant {}",
                start, created.size(), shop);
        return created;
    }

    private UUID sangria(BigDecimal value, String description) {
        CashMovement movement = cashRegisterService.recordMovement(
                CashMovementType.SANGRIA, null, value, "Despesa: " + description);
        return movement.getId();
    }

    private Expense requireOwned(UUID id) {
        return expenseRepository.findById(id)
                .filter(e -> TenantContext.getCurrent().equals(e.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Conta nao encontrada."));
    }

    private String requireDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Informe a descrição da despesa.");
        }
        return description.trim();
    }

    private BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("O valor da despesa deve ser maior que zero.");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
