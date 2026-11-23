package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.razor.api.model.Appointment;
import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.TenantUserRepository;
import com.razor.api.security.TenantUserPrincipal;
import com.razor.api.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Barbeiro so mexe nos proprios agendamentos (remarcar, cancelar, comparecimento); dono e
 * recepcao seguem sem restricao.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendaPermissionsTest {

    @Mock private TenantUserRepository tenantUserRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @InjectMocks private AgendaPermissions permissions;

    private UUID shop;
    private UUID me;
    private UUID colleague;
    private UUID userId;

    @BeforeEach
    void setUp() {
        shop = TenantContext.getCurrent();
        me = UUID.randomUUID();
        colleague = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    private TenantUserPrincipal principal(UserRole role) {
        return new TenantUserPrincipal(userId, "x@loja.com", shop, role);
    }

    private void givenUser(UUID professionalId, boolean active) {
        when(tenantUserRepository.findById(userId)).thenReturn(Optional.of(TenantUser.builder()
                .id(userId).barbershopId(shop).email("x@loja.com").passwordHash("h")
                .role(UserRole.BARBER).active(active).professionalId(professionalId).build()));
    }

    private void givenAppointment(long id, UUID professionalId) {
        when(appointmentRepository.findById(id)).thenReturn(Optional.of(Appointment.builder()
                .id(id).barbershopId(shop).professionalId(professionalId).build()));
    }

    @Test
    @DisplayName("dono e recepção mexem em qualquer agendamento, sem consultar vínculo")
    void ownerAndReception_unrestricted() {
        assertThatCode(() -> permissions.requireCanChange(principal(UserRole.OWNER), 1L, colleague))
                .doesNotThrowAnyException();
        assertThatCode(() -> permissions.requireCanChange(principal(UserRole.RECEPTION), 1L, null))
                .doesNotThrowAnyException();
        verifyNoInteractions(tenantUserRepository, appointmentRepository);
    }

    @Test
    @DisplayName("barbeiro altera o próprio agendamento (e remarca para si mesmo)")
    void barber_ownAppointment_allowed() {
        givenUser(me, true);
        givenAppointment(1L, me);

        assertThatCode(() -> permissions.requireCanChange(principal(UserRole.BARBER), 1L, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> permissions.requireCanChange(principal(UserRole.BARBER), 1L, me))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("barbeiro não altera o agendamento de um colega")
    void barber_colleagueAppointment_forbidden() {
        givenUser(me, true);
        givenAppointment(2L, colleague);

        assertThatThrownBy(() -> permissions.requireCanChange(principal(UserRole.BARBER), 2L, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(AgendaPermissions.NOT_YOURS);
    }

    @Test
    @DisplayName("barbeiro não passa o próprio horário para outro barbeiro")
    void barber_moveToColleague_forbidden() {
        givenUser(me, true);
        givenAppointment(1L, me);

        assertThatThrownBy(() -> permissions.requireCanChange(principal(UserRole.BARBER), 1L, colleague))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(AgendaPermissions.ONLY_TO_YOU);
    }

    @Test
    @DisplayName("barbeiro sem vínculo com profissional não altera nada")
    void barber_unlinked_forbidden() {
        givenUser(null, true);
        givenAppointment(1L, me);

        assertThatThrownBy(() -> permissions.requireCanChange(principal(UserRole.BARBER), 1L, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(AgendaPermissions.NOT_LINKED);
    }

    @Test
    @DisplayName("agendamento sem barbeiro não é 'dele'")
    void barber_unassignedAppointment_forbidden() {
        givenUser(me, true);
        givenAppointment(3L, null);

        assertThatThrownBy(() -> permissions.requireCanChange(principal(UserRole.BARBER), 3L, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("agendamento de outra barbearia responde 404, como se não existisse")
    void barber_otherTenantAppointment_notFound() {
        givenUser(me, true);
        when(appointmentRepository.findById(4L)).thenReturn(Optional.of(Appointment.builder()
                .id(4L).barbershopId(UUID.randomUUID()).professionalId(me).build()));

        assertThatThrownBy(() -> permissions.requireCanChange(principal(UserRole.BARBER), 4L, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("sem principal (nunca deveria chegar aqui) recusa")
    void nullPrincipal_forbidden() {
        assertThatThrownBy(() -> permissions.requireCanChange(null, 1L, null))
                .isInstanceOf(ForbiddenException.class);
    }
}
