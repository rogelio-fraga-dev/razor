package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Professional;
import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.TenantUserRepository;
import com.razor.api.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PanelUserServiceTest {

    @Mock private TenantUserRepository tenantUserRepository;
    @Mock private ProfessionalRepository professionalRepository;
    @InjectMocks private PanelUserService service;

    private final UUID shop = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID prof = UUID.randomUUID();
    private TenantUser user;

    @BeforeEach
    void setUp() {
        TenantContext.set(shop);
        user = TenantUser.builder().id(userId).barbershopId(shop).email("b@x").role(UserRole.BARBER).build();
        when(tenantUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tenantUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(professionalRepository.findById(prof)).thenReturn(Optional.of(
                Professional.builder().id(prof).barbershopId(shop).name("Rafael").build()));
        when(tenantUserRepository.findByBarbershopIdAndProfessionalId(shop, prof)).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("liga e desliga o login do profissional")
    void linkAndUnlink() {
        assertThat(service.linkProfessional(userId, prof).getProfessionalId()).isEqualTo(prof);
        assertThat(service.linkProfessional(userId, null).getProfessionalId()).isNull();
    }

    @Test
    @DisplayName("profissional de outra barbearia: 404 e nada é gravado")
    void otherTenantProfessional() {
        UUID alien = UUID.randomUUID();
        when(professionalRepository.findById(alien)).thenReturn(Optional.of(
                Professional.builder().id(alien).barbershopId(UUID.randomUUID()).name("X").build()));

        assertThatThrownBy(() -> service.linkProfessional(userId, alien)).isInstanceOf(NotFoundException.class);
        verify(tenantUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("usuário de outra barbearia: 404")
    void otherTenantUser() {
        UUID other = UUID.randomUUID();
        when(tenantUserRepository.findById(other)).thenReturn(Optional.of(
                TenantUser.builder().id(other).barbershopId(UUID.randomUUID()).email("z@x").build()));

        assertThatThrownBy(() -> service.linkProfessional(other, prof)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("profissional já ligado a outro login: 409")
    void alreadyLinked() {
        when(tenantUserRepository.findByBarbershopIdAndProfessionalId(shop, prof)).thenReturn(Optional.of(
                TenantUser.builder().id(UUID.randomUUID()).barbershopId(shop).email("a@x").name("Ana").build()));

        assertThatThrownBy(() -> service.linkProfessional(userId, prof)).isInstanceOf(ConflictException.class);
    }
}
