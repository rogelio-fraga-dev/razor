package com.razor.api.controller;

import com.razor.api.dto.ExpenseRequests;
import com.razor.api.dto.ExpenseResponse;
import com.razor.api.dto.PageResponse;
import com.razor.api.model.Expense;
import com.razor.api.model.ExpenseCategory;
import com.razor.api.service.ExpenseService;
import com.razor.api.util.AppointmentDates;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Contas a pagar (V31) no painel web — tenant vem do JWT, papel exigido: OWNER (ver
 * {@code SecurityConfig}). O resultado do periodo aparece no DRE
 * ({@code /api/admin/reports/dre}), que consome as mesmas despesas por competencia.
 */
@RestController
@RequestMapping("/api/admin/expenses")
@RequiredArgsConstructor
public class AdminExpenseController {

    private final ExpenseService expenseService;

    /** Categorias disponiveis, com o rotulo pronto — evita o painel manter a lista duplicada. */
    @GetMapping("/categories")
    public List<Map<String, String>> categories() {
        return java.util.Arrays.stream(ExpenseCategory.values())
                .map(c -> Map.of("value", c.name(), "label", c.label()))
                .toList();
    }

    @GetMapping
    public PageResponse<ExpenseResponse> list(
            @RequestParam(required = false) Boolean unpaidOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        LocalDate today = AppointmentDates.today();
        boolean pendentes = Boolean.TRUE.equals(unpaidOnly);
        // Pendencias: quem venceu ha mais tempo primeiro (e o que cobra acao). Lista completa:
        // vencimento mais recente primeiro, que e como o dono navega o historico.
        Sort sort = pendentes ? Sort.by(Sort.Order.asc("dueDate")) : Sort.by(Sort.Order.desc("dueDate"));

        return PageResponse.of(
                expenseService.page(pendentes, PageResponse.pageable(page, size, sort)),
                e -> ExpenseResponse.from(e, today));
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody ExpenseRequests.Create req) {
        Expense saved = expenseService.create(
                req.description(), req.category(), req.amount(), req.dueDate(), req.paidAt(),
                Boolean.TRUE.equals(req.fromCashRegister()), Boolean.TRUE.equals(req.recurring()),
                req.notes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ExpenseResponse.from(saved, AppointmentDates.today()));
    }

    @PutMapping("/{id}")
    public ExpenseResponse update(@PathVariable UUID id, @Valid @RequestBody ExpenseRequests.Update req) {
        Expense saved = expenseService.update(id, req.description(), req.category(), req.amount(),
                req.dueDate(), Boolean.TRUE.equals(req.recurring()), req.notes());
        return ExpenseResponse.from(saved, AppointmentDates.today());
    }

    /** Quitacao. Comando proprio, e nao um campo do update, porque pode mover dinheiro de verdade. */
    @PostMapping("/{id}/pay")
    public ExpenseResponse pay(@PathVariable UUID id, @RequestBody(required = false) ExpenseRequests.Pay req) {
        LocalDate paidAt = req == null ? null : req.paidAt();
        boolean fromCash = req != null && Boolean.TRUE.equals(req.fromCashRegister());
        return ExpenseResponse.from(expenseService.markPaid(id, paidAt, fromCash), AppointmentDates.today());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        expenseService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Copia as recorrentes do mes informado (padrao: o mes atual) para o mes seguinte, em aberto.
     * Idempotente: repetir o comando nao duplica nada.
     */
    @PostMapping("/repeat")
    public List<ExpenseResponse> repeat(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        LocalDate today = AppointmentDates.today();
        return expenseService.repeatRecurring(month != null ? month : today).stream()
                .map(e -> ExpenseResponse.from(e, today))
                .toList();
    }
}
