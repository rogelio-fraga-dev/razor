package com.razor.api.service;

import com.razor.api.model.Appointment;
import com.razor.api.model.Professional;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Escolhe qual profissional (barbeiro) atende um agendamento (Fase 1 multiprofissional).
 *
 * <p>Suporta as duas formas:
 * <ul>
 *   <li><b>Escolha explícita</b>: se um profissional preferido é informado e está livre no
 *       horário, ele é respeitado.</li>
 *   <li><b>Auto-atribuição</b>: caso contrário, escolhe entre os ativos livres no horário o
 *       <em>menos ocupado</em> no dia (balanceamento de carga).</li>
 * </ul>
 * Retorna {@code null} quando nenhum profissional ativo está livre naquele horário (todos os
 * barbeiros ocupados) — o agendamento segue sem barbeiro atribuído.
 *
 * <p><b>Serviços que atende (V37):</b> quando o serviço é informado, só concorre quem o faz
 * (ver {@link ServiceCapability}) — tanto na auto-atribuição quanto na escolha explícita: o
 * cliente pedir "barba com o Diego" não faz o Diego, que não faz barba, receber a barba. Barbeiro
 * sem serviços configurados atende todos, então quem nunca configurou nada não muda de
 * comportamento.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfessionalAssignmentService {

    private final ProfessionalRepository professionalRepository;
    private final AppointmentRepository appointmentRepository;
    private final ScheduleBlockService scheduleBlockService;
    private final ServiceOfferingRepository serviceOfferingRepository;

    /** Sem serviço informado: qualquer barbeiro ativo concorre (comportamento anterior à V37). */
    public UUID assign(LocalDate date, LocalTime time, UUID preferredProfessionalId) {
        return assign(date, time, preferredProfessionalId, null);
    }

    public UUID assign(LocalDate date, LocalTime time, UUID preferredProfessionalId, String serviceName) {
        if (date == null || time == null) return null;

        UUID shop = TenantContext.getCurrent();
        List<Professional> ativos = qualified(
                professionalRepository.findByBarbershopIdAndActiveTrue(shop), serviceName);
        if (ativos.isEmpty()) {
            if (serviceName != null) {
                log.warn("[PROFISSIONAL] Nenhum barbeiro ativo atende '{}'; agendamento sem barbeiro.", serviceName);
            }
            return null;
        }

        // Livre = sem outro agendamento no horario E sem folga/bloqueio incidindo (V16).
        List<Professional> livres = ativos.stream()
                .filter(p -> isFree(shop, p.getId(), date, time))
                .filter(p -> !scheduleBlockService.isBlocked(date, time, 30, p.getId()))
                .toList();
        if (livres.isEmpty()) return null;

        // Escolha explícita respeitada quando o preferido está livre
        if (preferredProfessionalId != null) {
            boolean preferredLivre = livres.stream().anyMatch(p -> p.getId().equals(preferredProfessionalId));
            if (preferredLivre) return preferredProfessionalId;
            log.info("[PROFISSIONAL] Preferido {} ocupado ou nao atende o servico; auto-atribuindo outro.",
                    preferredProfessionalId);
        }

        // Auto: menos ocupado no dia
        return livres.stream()
                .min(Comparator.comparingInt(p -> countNoDia(shop, p.getId(), date)))
                .map(Professional::getId)
                .orElse(null);
    }

    /**
     * Os profissionais (da lista dada) que atendem o serviço, na mesma ordem. Serviço nulo ou que
     * não casa com o catálogo não filtra ninguém. Usado também pelo bot para só oferecer ao
     * cliente barbeiros que fazem o que ele pediu.
     */
    public List<Professional> qualified(List<Professional> professionals, String serviceName) {
        if (professionals == null || professionals.isEmpty()) return List.of();
        if (serviceName == null || serviceName.isBlank()) return professionals;
        // Ninguem com restricao configurada: nem consulta o catalogo.
        if (professionals.stream().allMatch(Professional::servesAllServices)) return professionals;
        return ServiceCapability.qualified(professionals, serviceName,
                serviceOfferingRepository.findByBarbershopIdAndActiveTrue(TenantContext.getCurrent()));
    }

    /**
     * Escolhe entre barbeiros JA checados como livres pela duracao inteira do servico
     * ({@link AgendaAvailabilityService#checarHorario}): o preferido, se estiver entre eles; senao
     * o menos ocupado no dia. {@code null} quando a lista vem vazia.
     */
    public UUID escolherEntre(List<UUID> livres, LocalDate date, UUID preferredProfessionalId) {
        if (livres == null || livres.isEmpty()) return null;
        if (preferredProfessionalId != null && livres.contains(preferredProfessionalId)) {
            return preferredProfessionalId;
        }
        if (preferredProfessionalId != null) {
            log.info("[PROFISSIONAL] Preferido {} ocupado; auto-atribuindo outro.", preferredProfessionalId);
        }
        UUID shop = TenantContext.getCurrent();
        return livres.stream()
                .min(Comparator.comparingInt(p -> countNoDia(shop, p, date)))
                .orElse(null);
    }

    /**
     * Dos barbeiros {@code livres} (ids ja checados pela agenda), so os que fazem o servico (V37).
     * Servico nulo ou que nao casa com o catalogo nao filtra ninguem.
     */
    public List<UUID> qualifiedIds(List<UUID> livres, String serviceName) {
        if (livres == null || livres.isEmpty() || serviceName == null || serviceName.isBlank()) {
            return livres == null ? List.of() : livres;
        }
        List<Professional> candidatos = professionalRepository
                .findByBarbershopIdAndActiveTrue(TenantContext.getCurrent()).stream()
                .filter(p -> livres.contains(p.getId()))
                .toList();
        List<UUID> aptos = qualified(candidatos, serviceName).stream().map(Professional::getId).toList();
        return livres.stream().filter(aptos::contains).toList();
    }

    private boolean isFree(UUID shop, UUID professionalId, LocalDate date, LocalTime time) {
        return appointmentRepository
                .findByBarbershopIdAndProfessionalIdAndAppointmentDate(shop, professionalId, date)
                .stream()
                .noneMatch(a -> time.equals(a.getAppointmentTime()));
    }

    private int countNoDia(UUID shop, UUID professionalId, LocalDate date) {
        List<Appointment> apps = appointmentRepository
                .findByBarbershopIdAndProfessionalIdAndAppointmentDate(shop, professionalId, date);
        return apps.size();
    }
}
