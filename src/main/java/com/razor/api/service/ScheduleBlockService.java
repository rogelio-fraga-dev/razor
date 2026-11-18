package com.razor.api.service;

import com.razor.api.model.ScheduleBlock;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.ScheduleBlockRepository;
import com.razor.api.tenant.TenantContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Folgas e bloqueios de agenda do tenant (V16): responde "este horario esta disponivel?"
 * antes de a IA oferecer ou marcar. Sempre escopado no tenant atual.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleBlockService {

    private final ScheduleBlockRepository repository;
    private final ProfessionalRepository professionalRepository;

    public List<ScheduleBlock> listAll() {
        return repository.findByBarbershopId(TenantContext.getCurrent());
    }

    /**
     * True quando o slot esta bloqueado (folga, feriado, almoco...) para aquele profissional.
     * {@code professionalId} nulo considera apenas bloqueios da barbearia inteira.
     */
    public boolean isBlocked(LocalDate date, LocalTime slotStart, int durationMinutes, UUID professionalId) {
        return listAll().stream().anyMatch(b ->
                b.appliesTo(professionalId) && b.appliesOn(date) && b.coversSlot(slotStart, durationMinutes));
    }

    /** Bloqueios que incidem numa data — usado para filtrar a grade do dia de uma vez so. */
    public List<ScheduleBlock> blocksOn(LocalDate date) {
        return listAll().stream().filter(b -> b.appliesOn(date)).toList();
    }

    public ScheduleBlock create(UUID professionalId, LocalDate blockDate, Short weekday,
                                LocalTime startTime, LocalTime endTime, String reason) {
        validate(blockDate, weekday, startTime, endTime);
        requireProfessionalOfThisShop(professionalId, TenantContext.getCurrent());
        ScheduleBlock saved = repository.save(ScheduleBlock.builder()
                .barbershopId(TenantContext.getCurrent())
                .professionalId(professionalId)
                .blockDate(blockDate)
                .weekday(weekday)
                .startTime(startTime)
                .endTime(endTime)
                .reason(reason)
                .build());
        log.info("[AGENDA] Bloqueio criado no tenant {} ({})", TenantContext.getCurrent(), reason);
        return saved;
    }

    public void delete(UUID id) {
        ScheduleBlock block = repository.findById(id)
                .filter(b -> TenantContext.getCurrent().equals(b.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Bloqueio nao encontrado."));
        repository.delete(block);
    }

    /** Isolamento: barbeiro de outra barbearia (ou inexistente) nao recebe bloqueio por aqui. */
    private void requireProfessionalOfThisShop(UUID professionalId, UUID shop) {
        if (professionalId == null) return;
        professionalRepository.findById(professionalId)
                .filter(p -> shop.equals(p.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Profissional nao encontrado."));
    }

    /** Mesmas regras das constraints da V16, com mensagem amigavel antes de bater no banco. */
    private void validate(LocalDate blockDate, Short weekday, LocalTime start, LocalTime end) {
        boolean pontual = blockDate != null;
        boolean recorrente = weekday != null;
        if (pontual == recorrente) {
            throw new IllegalArgumentException("Informe uma data OU um dia da semana (apenas um dos dois).");
        }
        if (recorrente && (weekday < 0 || weekday > 6)) {
            throw new IllegalArgumentException("Dia da semana deve estar entre 0 (domingo) e 6 (sabado).");
        }
        boolean semHora = start == null && end == null;
        boolean comHora = start != null && end != null;
        if (!semHora && !comHora) {
            throw new IllegalArgumentException("Informe hora de inicio e fim, ou nenhuma (dia inteiro).");
        }
        if (comHora && !end.isAfter(start)) {
            throw new IllegalArgumentException("A hora final deve ser depois da inicial.");
        }
    }
}
