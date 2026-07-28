package com.razor.api.service;

import com.razor.api.model.CashMovement;
import com.razor.api.model.CashMovementType;
import com.razor.api.model.Professional;
import com.razor.api.model.ProfessionalAdvance;
import com.razor.api.repository.ProfessionalAdvanceRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vales e adiantamentos do barbeiro (V26). O valor sai agora e e descontado da comissao no
 * acerto do periodo que contem a data do vale.
 *
 * <p><b>Sair do caixa e opcional</b>, igual ao acerto (V25): vale em especie vira
 * {@link CashMovementType#SANGRIA}; vale por PIX nao mexe na conferencia da gaveta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfessionalAdvanceService {

    private final ProfessionalAdvanceRepository advanceRepository;
    private final ProfessionalRepository professionalRepository;
    private final CashRegisterService cashRegisterService;

    /** Historico de vales do tenant, do mais recente para o mais antigo. */
    public List<ProfessionalAdvance> history() {
        return advanceRepository.findByBarbershopIdOrderByAdvanceDateDesc(TenantContext.getCurrent());
    }

    /** Uma pagina do historico de vales (a ordenacao vem no {@link Pageable}). */
    public org.springframework.data.domain.Page<ProfessionalAdvance> page(
            org.springframework.data.domain.Pageable pageable) {
        return advanceRepository.findByBarbershopId(TenantContext.getCurrent(), pageable);
    }

    /** Quanto cada barbeiro pegou de vale no intervalo — o que o acerto vai descontar. */
    public Map<UUID, BigDecimal> advancedByProfessional(LocalDate from, LocalDate to) {
        Map<UUID, BigDecimal> byProf = new HashMap<>();
        for (ProfessionalAdvance a : advanceRepository
                .findByBarbershopIdAndAdvanceDateBetween(TenantContext.getCurrent(), from, to)) {
            byProf.merge(a.getProfessionalId(), a.getAmount(), BigDecimal::add);
        }
        return byProf;
    }

    /**
     * Registra um vale. Com {@code fromCashRegister}, exige caixa aberto e lanca a sangria — sem
     * caixa, o vale inteiro e recusado, em vez de registrar um adiantamento que nunca saiu da
     * gaveta.
     */
    @Transactional
    public ProfessionalAdvance grant(UUID professionalId, BigDecimal amount, LocalDate date,
                                     boolean fromCashRegister, String notes, String grantedByName) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("O valor do vale deve ser maior que zero.");
        }
        Professional professional = professionalRepository.findById(professionalId)
                .filter(p -> TenantContext.getCurrent().equals(p.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Profissional nao encontrado."));

        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        LocalDate advanceDate = date != null ? date : AppointmentDates.today();

        UUID movementId = null;
        if (fromCashRegister) {
            CashMovement movement = cashRegisterService.recordMovement(
                    CashMovementType.SANGRIA, null, value, "Vale " + professional.getName());
            movementId = movement.getId();
        }

        ProfessionalAdvance saved = advanceRepository.save(ProfessionalAdvance.builder()
                .barbershopId(TenantContext.getCurrent())
                .professionalId(professionalId)
                .amount(value)
                .advanceDate(advanceDate)
                .notes(notes == null || notes.isBlank() ? null : notes.trim())
                .cashMovementId(movementId)
                .grantedByName(grantedByName)
                .build());

        log.info("[VALE] {} para '{}' em {}{} no tenant {}", value, professional.getName(), advanceDate,
                fromCashRegister ? " via caixa" : " por fora do caixa", TenantContext.getCurrent());
        return saved;
    }
}
