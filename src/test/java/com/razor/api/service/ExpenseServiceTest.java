package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.CashMovement;
import com.razor.api.model.CashMovementType;
import com.razor.api.model.Expense;
import com.razor.api.model.ExpenseCategory;
import com.razor.api.repository.ExpenseRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Contas a pagar (V31)")
class ExpenseServiceTest {

    @Mock private ExpenseRepository expenseRepository;
    @Mock private CashRegisterService cashRegisterService;
    @InjectMocks private ExpenseService service;

    private Expense expense(BigDecimal amount, LocalDate due, LocalDate paidAt) {
        return Expense.builder()
                .id(UUID.randomUUID())
                .barbershopId(TenantContext.getCurrent())
                .description("Aluguel")
                .category(ExpenseCategory.ALUGUEL)
                .amount(amount)
                .dueDate(due)
                .paidAt(paidAt)
                .build();
    }

    private void echoSave() {
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- cadastro -------------------------------------------------------------------------

    @Test
    @DisplayName("conta em aberto não tira dinheiro da gaveta, mesmo pedindo pelo caixa")
    void create_unpaid_neverTouchesCashRegister() {
        echoSave();

        Expense saved = service.create("Aluguel", ExpenseCategory.ALUGUEL, new BigDecimal("2500"),
                LocalDate.of(2026, 7, 5), null, true, false, null);

        assertThat(saved.isPaid()).isFalse();
        assertThat(saved.getCashMovementId()).isNull();
        // fromCashRegister=true sem paidAt seria sangria de dinheiro que ainda nao saiu.
        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any());
    }

    @Test
    @DisplayName("conta já paga em espécie vira sangria no caixa")
    void create_paidInCash_recordsSangria() {
        echoSave();
        UUID movementId = UUID.randomUUID();
        when(cashRegisterService.recordMovement(eq(CashMovementType.SANGRIA), any(), any(), any()))
                .thenReturn(CashMovement.builder().id(movementId).build());

        Expense saved = service.create("Energia", ExpenseCategory.ENERGIA, new BigDecimal("430.50"),
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 10), true, false, null);

        assertThat(saved.getCashMovementId()).isEqualTo(movementId);
        verify(cashRegisterService).recordMovement(eq(CashMovementType.SANGRIA), eq(null),
                eq(new BigDecimal("430.50")), eq("Despesa: Energia"));
    }

    @Test
    @DisplayName("conta paga por fora do caixa não mexe na conferência da gaveta")
    void create_paidOutsideCashRegister_noMovement() {
        echoSave();

        Expense saved = service.create("Internet", ExpenseCategory.INTERNET, new BigDecimal("199.90"),
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 15), false, false, null);

        assertThat(saved.isPaid()).isTrue();
        assertThat(saved.getCashMovementId()).isNull();
        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any());
    }

    @Test
    @DisplayName("valor zero ou negativo é recusado")
    void create_nonPositiveAmount_throws() {
        assertThatThrownBy(() -> service.create("X", ExpenseCategory.OUTROS, BigDecimal.ZERO,
                LocalDate.now(), null, false, false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maior que zero");
    }

    // --- quitação -------------------------------------------------------------------------

    @Test
    @DisplayName("quitar duas vezes é recusado — senão a mesma conta geraria duas sangrias")
    void markPaid_twice_throws() {
        Expense paga = expense(new BigDecimal("2500.00"), LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 6));
        when(expenseRepository.findById(paga.getId())).thenReturn(Optional.of(paga));

        assertThatThrownBy(() -> service.markPaid(paga.getId(), LocalDate.of(2026, 7, 7), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("já está paga");

        verify(cashRegisterService, never()).recordMovement(any(), any(), any(), any());
    }

    @Test
    @DisplayName("conta de outro tenant não é encontrada")
    void markPaid_otherTenant_notFound() {
        Expense outra = expense(new BigDecimal("100.00"), LocalDate.now(), null);
        outra.setBarbershopId(UUID.randomUUID());
        when(expenseRepository.findById(outra.getId())).thenReturn(Optional.of(outra));

        assertThatThrownBy(() -> service.markPaid(outra.getId(), null, false))
                .isInstanceOf(NotFoundException.class);
    }

    // --- edição e exclusão travadas após sair do caixa -------------------------------------

    @Test
    @DisplayName("conta paga pelo caixa não pode ser editada — a gaveta discordaria do relatório")
    void update_afterCashPayment_throws() {
        Expense paga = expense(new BigDecimal("2500.00"), LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 5));
        paga.setCashMovementId(UUID.randomUUID());
        when(expenseRepository.findById(paga.getId())).thenReturn(Optional.of(paga));

        assertThatThrownBy(() -> service.update(paga.getId(), "Aluguel", ExpenseCategory.ALUGUEL,
                new BigDecimal("100.00"), LocalDate.of(2026, 7, 5), false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("suprimento");
    }

    @Test
    @DisplayName("conta paga pelo caixa não pode ser apagada")
    void delete_afterCashPayment_throws() {
        Expense paga = expense(new BigDecimal("2500.00"), LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 5));
        paga.setCashMovementId(UUID.randomUUID());
        when(expenseRepository.findById(paga.getId())).thenReturn(Optional.of(paga));

        assertThatThrownBy(() -> service.delete(paga.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(expenseRepository, never()).delete(any());
    }

    @Test
    @DisplayName("conta em aberto pode ser apagada")
    void delete_unpaid_removes() {
        Expense aberta = expense(new BigDecimal("300.00"), LocalDate.of(2026, 8, 5), null);
        when(expenseRepository.findById(aberta.getId())).thenReturn(Optional.of(aberta));

        service.delete(aberta.getId());

        verify(expenseRepository).delete(aberta);
    }

    // --- recorrência ----------------------------------------------------------------------

    @Test
    @DisplayName("repetir copia a recorrente para o mês seguinte, em aberto e sem vínculo de caixa")
    void repeatRecurring_createsNextMonthUnpaid() {
        Expense origem = expense(new BigDecimal("2500.00"), LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 5));
        origem.setRecurring(true);
        origem.setCashMovementId(UUID.randomUUID());
        when(expenseRepository.findByBarbershopIdAndRecurringTrueAndDueDateBetween(
                any(), eq(LocalDate.of(2026, 7, 1)), eq(LocalDate.of(2026, 7, 31))))
                .thenReturn(List.of(origem));
        when(expenseRepository.existsByBarbershopIdAndDescriptionAndDueDateAndRecurringTrue(
                any(), eq("Aluguel"), eq(LocalDate.of(2026, 8, 5)))).thenReturn(false);
        echoSave();

        List<Expense> criadas = service.repeatRecurring(LocalDate.of(2026, 7, 20));

        assertThat(criadas).hasSize(1);
        ArgumentCaptor<Expense> cap = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(cap.capture());
        Expense nova = cap.getValue();
        assertThat(nova.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        // A cópia não pode herdar o pagamento nem a sangria do mês anterior.
        assertThat(nova.getPaidAt()).isNull();
        assertThat(nova.getCashMovementId()).isNull();
        assertThat(nova.isRecurring()).isTrue();
        assertThat(nova.getAmount()).isEqualByComparingTo("2500.00");
    }

    @Test
    @DisplayName("repetir duas vezes não duplica — o comando é idempotente")
    void repeatRecurring_isIdempotent() {
        Expense origem = expense(new BigDecimal("2500.00"), LocalDate.of(2026, 7, 5), null);
        origem.setRecurring(true);
        when(expenseRepository.findByBarbershopIdAndRecurringTrueAndDueDateBetween(any(), any(), any()))
                .thenReturn(List.of(origem));
        when(expenseRepository.existsByBarbershopIdAndDescriptionAndDueDateAndRecurringTrue(
                any(), eq("Aluguel"), eq(LocalDate.of(2026, 8, 5)))).thenReturn(true);

        List<Expense> criadas = service.repeatRecurring(LocalDate.of(2026, 7, 20));

        assertThat(criadas).isEmpty();
        verify(expenseRepository, never()).save(any());
    }

    // --- agregação ------------------------------------------------------------------------

    @Test
    @DisplayName("total por categoria soma as despesas do período e ignora categoria sem gasto")
    void totalsByCategory_sumsAndOmitsEmpty() {
        Expense aluguel = expense(new BigDecimal("2500.00"), LocalDate.of(2026, 7, 5), null);
        Expense energia = expense(new BigDecimal("430.00"), LocalDate.of(2026, 7, 10), null);
        energia.setCategory(ExpenseCategory.ENERGIA);
        Expense energia2 = expense(new BigDecimal("70.00"), LocalDate.of(2026, 7, 20), null);
        energia2.setCategory(ExpenseCategory.ENERGIA);
        when(expenseRepository.findByBarbershopIdAndDueDateBetween(any(), any(), any()))
                .thenReturn(List.of(aluguel, energia, energia2));

        var totals = service.totalsByCategory(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(totals).hasSize(2);
        assertThat(totals.get(ExpenseCategory.ALUGUEL)).isEqualByComparingTo("2500.00");
        assertThat(totals.get(ExpenseCategory.ENERGIA)).isEqualByComparingTo("500.00");
        assertThat(totals).doesNotContainKey(ExpenseCategory.MARKETING);
    }

    @Test
    @DisplayName("vencida é a que passou do prazo e continua em aberto")
    void isOverdue_onlyUnpaidPastDue() {
        LocalDate hoje = LocalDate.of(2026, 7, 20);

        assertThat(expense(BigDecimal.TEN, LocalDate.of(2026, 7, 5), null).isOverdue(hoje)).isTrue();
        // paga fora do prazo nao e "vencida": ja foi resolvida
        assertThat(expense(BigDecimal.TEN, LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 18)).isOverdue(hoje)).isFalse();
        // ainda vai vencer
        assertThat(expense(BigDecimal.TEN, LocalDate.of(2026, 7, 25), null).isOverdue(hoje)).isFalse();
    }
}
