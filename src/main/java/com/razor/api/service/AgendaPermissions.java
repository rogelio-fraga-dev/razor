package com.razor.api.service;

import com.razor.api.model.Appointment;
import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.TenantUserRepository;
import com.razor.api.security.TenantUserPrincipal;
import com.razor.api.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Quem pode mexer em qual agendamento da agenda do painel.
 *
 * <p>Dono e recepcao: qualquer agendamento do tenant (o {@code SecurityConfig} ja barrou quem nao
 * e do tenant). Barbeiro: <b>so os proprios</b> — o agendamento precisa ser do profissional ligado
 * ao login dele (V44, {@code tenant_users.professional_id}) e, ao remarcar, continuar com ele. Sem
 * vinculo, o barbeiro nao altera nada: nao ha como saber quais horarios sao "dele".
 *
 * <p>O vinculo e lido do banco a cada chamada (nao do JWT): desligar o login de um profissional
 * vale na hora, sem esperar a sessao expirar.
 */
@Service
@RequiredArgsConstructor
public class AgendaPermissions {

    static final String NOT_LINKED =
            "Seu login não está ligado a um profissional — peça ao dono para fazer o vínculo em Configurações › Equipe.";
    static final String NOT_YOURS = "Você só pode alterar os seus próprios agendamentos.";
    static final String ONLY_TO_YOU = "Você só pode remarcar para a sua própria agenda.";

    private final TenantUserRepository tenantUserRepository;
    private final AppointmentRepository appointmentRepository;

    /**
     * Confere se {@code principal} pode alterar o agendamento {@code appointmentId} (remarcar,
     * cancelar, comparecimento). {@code targetProfessionalId} e o barbeiro de destino numa
     * remarcacao (nulo nas demais acoes). Lanca {@link ForbiddenException} (403) quando nao pode.
     */
    public void requireCanChange(TenantUserPrincipal principal, Long appointmentId, UUID targetProfessionalId) {
        if (!restricted(principal)) return;
        UUID mine = linkedProfessional(principal);
        UUID shop = TenantContext.getCurrent();
        Appointment ag = appointmentRepository.findById(appointmentId)
                .filter(a -> Objects.equals(shop, a.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Agendamento não encontrado."));
        if (!mine.equals(ag.getProfessionalId())) throw new ForbiddenException(NOT_YOURS);
        if (targetProfessionalId != null && !mine.equals(targetProfessionalId)) {
            throw new ForbiddenException(ONLY_TO_YOU);
        }
    }

    /** O papel tem a restricao "so os proprios"? Hoje so o barbeiro. */
    static boolean restricted(TenantUserPrincipal principal) {
        return principal == null || principal.role() == UserRole.BARBER;
    }

    private UUID linkedProfessional(TenantUserPrincipal principal) {
        if (principal == null) throw new ForbiddenException(NOT_YOURS);
        return tenantUserRepository.findById(principal.userId())
                .filter(u -> Objects.equals(u.getBarbershopId(), principal.barbershopId()))
                .filter(TenantUser::isActive)
                .map(TenantUser::getProfessionalId)
                .orElseThrow(() -> new ForbiddenException(NOT_LINKED));
    }
}
