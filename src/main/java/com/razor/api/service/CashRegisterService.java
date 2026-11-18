package com.razor.api.service;

import com.razor.api.dto.CashSessionSummary;
import com.razor.api.model.CashMovement;
import com.razor.api.model.CashMovementType;
import com.razor.api.model.CashPaymentMethod;
import com.razor.api.model.CashRegisterSession;
import com.razor.api.repository.CashMovementRepository;
import com.razor.api.repository.CashRegisterSessionRepository;
import com.razor.api.repository.PaymentMethodFeeRepository;
import com.razor.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caixa da barbearia (V18): abertura com fundo de troco, movimentos (venda/sangria/suprimento)
 * e fechamento com conferência da gaveta. Sempre escopado no tenant atual; no máximo uma sessão
 * aberta por barbearia.
 *
 * <p><b>Gaveta esperada</b> = fundo + suprimentos − sangrias + vendas em dinheiro. Só o dinheiro
 * mexe na gaveta física; PIX/cartão entram nos totais, mas não na conferência.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CashRegisterService {

    private final CashRegisterSessionRepository sessionRepository;
    private final CashMovementRepository movementRepository;
    private final PaymentMethodFeeRepository feeRepository;

    /** Sessão aberta do tenant, se houver. */
    public java.util.Optional<CashRegisterSession> openSession() {
        return sessionRepository.findByBarbershopIdAndStatus(
                TenantContext.getCurrent(), CashRegisterSession.Status.OPEN);
    }

    /** Retrato da sessão aberta (ou {@code null} quando o caixa está fechado). */
    public CashSessionSummary currentSummary() {
        return openSession().map(this::buildSummary).orElse(null);
    }

    @Transactional
    public CashSessionSummary open(BigDecimal openingAmount, UUID userId, String userName) {
        if (openSession().isPresent()) {
            throw new IllegalArgumentException("Já existe um caixa aberto. Feche-o antes de abrir outro.");
        }
        if (openingAmount == null || openingAmount.signum() < 0) {
            throw new IllegalArgumentException("O fundo de troco não pode ser negativo.");
        }
        CashRegisterSession session = sessionRepository.save(CashRegisterSession.builder()
                .barbershopId(TenantContext.getCurrent())
                .openedByUserId(userId)
                .openedByName(userName)
                .openedAt(LocalDateTime.now())
                .openingAmount(scale(openingAmount))
                .status(CashRegisterSession.Status.OPEN)
                .build());
        log.info("[CAIXA] Aberto no tenant {} com fundo {}", TenantContext.getCurrent(), session.getOpeningAmount());
        return buildSummary(session);
    }

    @Transactional
    public CashSessionSummary addMovement(CashMovementType type, CashPaymentMethod method,
                                          BigDecimal amount, String description) {
        CashMovement movement = recordMovement(type, method, amount, description);
        return buildSummary(sessionRepository.findById(movement.getSessionId()).orElseThrow());
    }

    /**
     * Grava o movimento e devolve a linha criada. Existe separado de {@link #addMovement} porque
     * a venda de balcao precisa guardar o id do movimento para amarrar venda e caixa.
     */
    @Transactional
    public CashMovement recordMovement(CashMovementType type, CashPaymentMethod method,
                                       BigDecimal amount, String description) {
        return recordMovement(type, method, amount, description, null);
    }

    /**
     * Como {@link #recordMovement(CashMovementType, CashPaymentMethod, BigDecimal, String)},
     * amarrando a venda a uma comanda (V35) — no split, cada parte do pagamento chama isto uma
     * vez, com a sua forma. A taxa da forma de pagamento (se houver) e calculada aqui e gravada
     * no movimento.
     */
    @Transactional
    public CashMovement recordMovement(CashMovementType type, CashPaymentMethod method,
                                       BigDecimal amount, String description, UUID comandaId) {
        CashRegisterSession session = openSession()
                .orElseThrow(() -> new IllegalArgumentException("Nenhum caixa aberto. Abra o caixa primeiro."));
        if (type == null) {
            throw new IllegalArgumentException("Informe o tipo do movimento.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("O valor deve ser maior que zero.");
        }
        CashPaymentMethod effectiveMethod;
        if (type == CashMovementType.VENDA) {
            if (method == null) {
                throw new IllegalArgumentException("Escolha a forma de pagamento da venda.");
            }
            effectiveMethod = method;
        } else {
            // Sangria/suprimento são sempre dinheiro da gaveta — forma de pagamento não se aplica.
            effectiveMethod = null;
        }

        CashMovement saved = movementRepository.save(CashMovement.builder()
                .sessionId(session.getId())
                .barbershopId(TenantContext.getCurrent())
                .type(type)
                .paymentMethod(effectiveMethod)
                .amount(scale(amount))
                .feeAmount(feeFor(effectiveMethod, scale(amount)))
                .comandaId(type == CashMovementType.VENDA ? comandaId : null)
                .description(description == null || description.isBlank() ? null : description.trim())
                .build());
        log.info("[CAIXA] {} de {} no tenant {}", type, scale(amount), TenantContext.getCurrent());
        return saved;
    }

    @Transactional
    public CashSessionSummary close(BigDecimal counted, String notes, UUID userId, String userName) {
        CashRegisterSession session = openSession()
                .orElseThrow(() -> new IllegalArgumentException("Nenhum caixa aberto para fechar."));
        if (counted == null || counted.signum() < 0) {
            throw new IllegalArgumentException("Informe o valor contado na gaveta.");
        }
        List<CashMovement> movements = movementRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        BigDecimal expected = expectedDrawer(session, movements);

        session.setClosingExpected(expected);
        session.setClosingCounted(scale(counted));
        session.setClosedByUserId(userId);
        session.setClosedByName(userName);
        session.setClosedAt(LocalDateTime.now());
        session.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
        session.setStatus(CashRegisterSession.Status.CLOSED);
        sessionRepository.save(session);

        BigDecimal diff = scale(counted).subtract(expected);
        log.info("[CAIXA] Fechado no tenant {}: esperado {}, contado {}, diferença {}",
                TenantContext.getCurrent(), expected, scale(counted), diff);
        return buildSummary(session, movements);
    }

    /** Partes do pagamento de uma comanda fechada (V35), na ordem em que entraram. */
    public List<CashMovement> movementsOfComanda(UUID comandaId) {
        return movementRepository.findByComandaIdOrderByCreatedAtAsc(comandaId).stream()
                .filter(m -> TenantContext.getCurrent().equals(m.getBarbershopId()))
                .toList();
    }

    // ---------- cálculo ----------

    /**
     * Taxa da forma de pagamento sobre esta venda, congelada no movimento. Sangria/suprimento
     * (sem forma) e dinheiro nunca tem taxa.
     */
    private BigDecimal feeFor(CashPaymentMethod method, BigDecimal amount) {
        if (method == null || !method.acceptsFee()) return BigDecimal.ZERO.setScale(2);
        return feeRepository.findByBarbershopIdAndPaymentMethod(TenantContext.getCurrent(), method)
                .map(f -> PaymentFeeService.feeOf(amount, f.getFeePercent()))
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    private CashSessionSummary buildSummary(CashRegisterSession session) {
        return buildSummary(session, movementRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()));
    }

    private CashSessionSummary buildSummary(CashRegisterSession session, List<CashMovement> movements) {
        BigDecimal dinheiro = sumSales(movements, CashPaymentMethod.DINHEIRO);
        BigDecimal pix = sumSales(movements, CashPaymentMethod.PIX);
        BigDecimal debito = sumSales(movements, CashPaymentMethod.DEBITO);
        BigDecimal credito = sumSales(movements, CashPaymentMethod.CREDITO);
        BigDecimal outro = sumSales(movements, CashPaymentMethod.OUTRO);
        BigDecimal taxas = scale(movements.stream()
                .filter(m -> m.getType() == CashMovementType.VENDA && m.getFeeAmount() != null)
                .map(CashMovement::getFeeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal vendasTotal = sumByType(movements, CashMovementType.VENDA);
        BigDecimal sangrias = sumByType(movements, CashMovementType.SANGRIA);
        BigDecimal suprimentos = sumByType(movements, CashMovementType.SUPRIMENTO);

        BigDecimal expected = expectedDrawer(session, movements);
        BigDecimal difference = session.getStatus() == CashRegisterSession.Status.CLOSED
                && session.getClosingCounted() != null
                ? session.getClosingCounted().subtract(session.getClosingExpected())
                : null;

        var totals = new CashSessionSummary.Totals(dinheiro, pix, debito, credito, outro, vendasTotal,
                taxas, sangrias, suprimentos);
        var views = movements.stream().map(CashSessionSummary.MovementView::from).toList();

        return new CashSessionSummary(
                session.getId(), session.getStatus().name(),
                session.getOpenedByName(), session.getOpenedAt(), session.getOpeningAmount(),
                session.getClosedByName(), session.getClosedAt(),
                session.getClosingCounted(), session.getClosingExpected(),
                session.getNotes(), views, totals, expected, difference);
    }

    /** Dinheiro que deveria estar na gaveta: fundo + suprimentos − sangrias + vendas em dinheiro. */
    private BigDecimal expectedDrawer(CashRegisterSession session, List<CashMovement> movements) {
        return session.getOpeningAmount()
                .add(sumByType(movements, CashMovementType.SUPRIMENTO))
                .subtract(sumByType(movements, CashMovementType.SANGRIA))
                .add(sumSales(movements, CashPaymentMethod.DINHEIRO));
    }

    private BigDecimal sumByType(List<CashMovement> movements, CashMovementType type) {
        return scale(movements.stream()
                .filter(m -> m.getType() == type)
                .map(CashMovement::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal sumSales(List<CashMovement> movements, CashPaymentMethod method) {
        return scale(movements.stream()
                .filter(m -> m.getType() == CashMovementType.VENDA && m.getPaymentMethod() == method)
                .map(CashMovement::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal scale(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
