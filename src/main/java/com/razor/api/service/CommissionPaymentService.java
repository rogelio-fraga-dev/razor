package com.razor.api.service;

import com.razor.api.model.CashMovement;
import com.razor.api.model.CashMovementType;
import com.razor.api.model.CommissionPayment;
import com.razor.api.model.Professional;
import com.razor.api.repository.CommissionPaymentRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acerto de comissao com o barbeiro (V25): registra o que ja foi pago, para o dono nao pagar a
 * mesma quinzena duas vezes.
 *
 * <p><b>Sair do caixa e opcional.</b> Pagar por PIX ou transferencia nao tira dinheiro da gaveta;
 * so o pagamento em especie vira {@link CashMovementType#SANGRIA}. Forcar a sangria distorceria
 * a conferencia do fechamento de quem nao paga em dinheiro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionPaymentService {

    private final CommissionPaymentRepository paymentRepository;
    private final ProfessionalRepository professionalRepository;
    private final CashRegisterService cashRegisterService;

    /** Historico de acertos do tenant, do mais recente para o mais antigo. */
    public List<CommissionPayment> history() {
        return paymentRepository.findByBarbershopIdOrderByPaidAtDesc(TenantContext.getCurrent());
    }

    /** Uma pagina do historico (a ordenacao vem no {@link org.springframework.data.domain.Pageable}). */
    public org.springframework.data.domain.Page<CommissionPayment> page(
            org.springframework.data.domain.Pageable pageable) {
        return paymentRepository.findByBarbershopId(TenantContext.getCurrent(), pageable);
    }

    /**
     * Quanto ja foi pago a cada barbeiro referente ao intervalo consultado. Considera apenas
     * pagamentos <b>contidos</b> na janela — sobreposicao parcial seria palpite.
     */
    public Map<UUID, BigDecimal> paidByProfessional(LocalDate from, LocalDate to) {
        Map<UUID, BigDecimal> paid = new HashMap<>();
        for (CommissionPayment p : paymentRepository
                .findByBarbershopIdAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqual(
                        TenantContext.getCurrent(), from, to)) {
            paid.merge(p.getProfessionalId(), p.getAmount(), BigDecimal::add);
        }
        return paid;
    }

    /**
     * Registra um acerto. Com {@code fromCashRegister}, exige caixa aberto e lanca a sangria —
     * se nao houver caixa, o pagamento inteiro e recusado, em vez de registrar um acerto que
     * nunca saiu da gaveta.
     */
    @Transactional
    public CommissionPayment pay(UUID professionalId, LocalDate periodStart, LocalDate periodEnd,
                                 BigDecimal amount, boolean fromCashRegister, String notes,
                                 String paidByName) {
        if (periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("Informe o periodo do acerto.");
        }
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("O fim do periodo nao pode ser antes do inicio.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("O valor do acerto deve ser maior que zero.");
        }
        Professional professional = professionalRepository.findById(professionalId)
                .filter(p -> TenantContext.getCurrent().equals(p.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Profissional nao encontrado."));

        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);

        UUID movementId = null;
        if (fromCashRegister) {
            String description = "Comissao " + professional.getName();
            CashMovement movement = cashRegisterService.recordMovement(
                    CashMovementType.SANGRIA, null, value, description);
            movementId = movement.getId();
        }

        LocalDateTime now = LocalDateTime.now();
        CommissionPayment saved = paymentRepository.save(CommissionPayment.builder()
                .barbershopId(TenantContext.getCurrent())
                .professionalId(professionalId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .amount(value)
                .notes(notes == null || notes.isBlank() ? null : notes.trim())
                .cashMovementId(movementId)
                .paidByName(paidByName)
                .paidAt(now)
                .paymentDate(AppointmentDates.today())
                .build());

        log.info("[COMISSAO] Acerto de {} com '{}' ({} a {}){} no tenant {}",
                value, professional.getName(), periodStart, periodEnd,
                fromCashRegister ? " via caixa" : " por fora do caixa", TenantContext.getCurrent());
        return saved;
    }
}
