package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Professional;
import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.TenantUserRepository;
import com.razor.api.security.SessionValidator;
import java.util.List;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PanelUserServiceTest {

    @Mock private TenantUserRepository tenantUserRepository;
    @Mock private ProfessionalRepository professionalRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SessionValidator sessionValidator;
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
        when(tenantUserRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenAnswer(i -> "hash:" + i.getArgument(0));
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

    // ---- Equipe & acesso: cadastro, papel, ativo, senha ---------------------------------------

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherOwnerId = UUID.randomUUID();

    private TenantUser owner(UUID id) {
        TenantUser u = TenantUser.builder().id(id).barbershopId(shop).email(id + "@x").role(UserRole.OWNER).build();
        when(tenantUserRepository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    @DisplayName("cria login com email normalizado, senha com hash e no tenant atual")
    void createUser() {
        when(tenantUserRepository.existsByEmail("ana@barbearia.com")).thenReturn(false);

        TenantUser created = service.create(" Ana ", "  Ana@Barbearia.COM ", UserRole.BARBER, "segredo123", prof);

        assertThat(created.getEmail()).isEqualTo("ana@barbearia.com");
        assertThat(created.getName()).isEqualTo("Ana");
        assertThat(created.getBarbershopId()).isEqualTo(shop);
        assertThat(created.getPasswordHash()).isEqualTo("hash:segredo123");
        assertThat(created.getProfessionalId()).isEqualTo(prof);
        assertThat(created.isActive()).isTrue();
    }

    @Test
    @DisplayName("email já usado (em qualquer barbearia): 409")
    void createDuplicateEmail() {
        when(tenantUserRepository.existsByEmail("ana@x.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create("Ana", "ana@x.com", UserRole.RECEPTION, "segredo123", null))
                .isInstanceOf(ConflictException.class);
        verify(tenantUserRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("senha curta, email inválido, sem nome ou sem papel: 400")
    void createValidation() {
        assertThatThrownBy(() -> service.create("Ana", "ana@x.com", UserRole.BARBER, "1234567", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("8");
        assertThatThrownBy(() -> service.create("Ana", "ana-sem-arroba", UserRole.BARBER, "segredo123", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(" ", "ana@x.com", UserRole.BARBER, "segredo123", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create("Ana", "ana@x.com", null, "segredo123", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tenantUserRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("recepção não é ligada a profissional")
    void receptionCannotLink() {
        assertThatThrownBy(() -> service.create("Bia", "bia@x.com", UserRole.RECEPTION, "segredo123", prof))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("não desativa nem rebaixa a si mesmo")
    void cannotTouchSelf() {
        owner(ownerId);
        owner(otherOwnerId);

        assertThatThrownBy(() -> service.setActive(ownerId, ownerId, false)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.changeRole(ownerId, ownerId, UserRole.RECEPTION))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.resetPassword(ownerId, ownerId, "segredo123"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tenantUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("último dono ativo não pode ser desativado nem rebaixado")
    void lastActiveOwner() {
        owner(ownerId);
        TenantUser other = owner(otherOwnerId);
        when(tenantUserRepository.lockActiveByRole(shop, UserRole.OWNER)).thenReturn(List.of(other));

        assertThatThrownBy(() -> service.setActive(ownerId, otherOwnerId, false)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.changeRole(ownerId, otherOwnerId, UserRole.BARBER))
                .isInstanceOf(ConflictException.class);
        verify(tenantUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("com outro dono ativo, desativa e derruba as sessões do usuário")
    void deactivateOwnerWithAnotherActive() {
        TenantUser me = owner(ownerId);
        TenantUser other = owner(otherOwnerId);
        when(tenantUserRepository.lockActiveByRole(shop, UserRole.OWNER)).thenReturn(List.of(me, other));

        TenantUser saved = service.setActive(ownerId, otherOwnerId, false);

        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getTokenVersion()).isEqualTo(1);
        verify(sessionValidator).invalidate(otherOwnerId);
    }

    @Test
    @DisplayName("reativar não passa pela regra do último dono")
    void reactivate() {
        owner(ownerId);
        user.setActive(false);

        assertThat(service.setActive(ownerId, userId, true).isActive()).isTrue();
        verify(tenantUserRepository, never()).lockActiveByRole(any(), any());
    }

    @Test
    @DisplayName("virar recepção desfaz o vínculo com o profissional e derruba as sessões")
    void demoteToReception() {
        owner(ownerId);
        user.setProfessionalId(prof);

        TenantUser saved = service.changeRole(ownerId, userId, UserRole.RECEPTION);

        assertThat(saved.getRole()).isEqualTo(UserRole.RECEPTION);
        assertThat(saved.getProfessionalId()).isNull();
        verify(sessionValidator).invalidate(userId);
    }

    @Test
    @DisplayName("redefine a senha de outro usuário com hash e revoga as sessões dele")
    void resetPassword() {
        owner(ownerId);

        TenantUser saved = service.resetPassword(ownerId, userId, "novaSenha99");

        assertThat(saved.getPasswordHash()).isEqualTo("hash:novaSenha99");
        assertThat(saved.getTokenVersion()).isEqualTo(1);
        verify(sessionValidator).invalidate(userId);
        assertThatThrownBy(() -> service.resetPassword(ownerId, userId, "curta"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("mexer em usuário de outra barbearia: 404")
    void otherTenantOnRules() {
        UUID alien = UUID.randomUUID();
        when(tenantUserRepository.findById(alien)).thenReturn(Optional.of(
                TenantUser.builder().id(alien).barbershopId(UUID.randomUUID()).email("z@x").role(UserRole.OWNER).build()));

        assertThatThrownBy(() -> service.setActive(ownerId, alien, false)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.changeRole(ownerId, alien, UserRole.BARBER)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.resetPassword(ownerId, alien, "segredo123")).isInstanceOf(NotFoundException.class);
        verify(passwordEncoder, never()).encode(eq("segredo123"));
    }

    // ---- Nome e e-mail ------------------------------------------------------------------------

    @Test
    @DisplayName("troca de nome: grava aparado e NÃO derruba as sessões")
    void updateIdentity_nameOnly_keepsSessions() {
        TenantUser saved = service.updateIdentity(userId, "  Rafael Lima  ", null);

        assertThat(saved.getName()).isEqualTo("Rafael Lima");
        assertThat(saved.getTokenVersion()).isZero();
        verify(sessionValidator, never()).invalidate(any());
    }

    @Test
    @DisplayName("troca de e-mail: normaliza (minúsculas), grava e derruba as sessões")
    void updateIdentity_email_lowercasedAndRevokes() {
        when(tenantUserRepository.existsByEmail("novo@loja.com")).thenReturn(false);

        TenantUser saved = service.updateIdentity(userId, null, "  Novo@Loja.COM ");

        assertThat(saved.getEmail()).isEqualTo("novo@loja.com");
        assertThat(saved.getTokenVersion()).isEqualTo(1);
        verify(sessionValidator).invalidate(userId);
    }

    @Test
    @DisplayName("mesmo e-mail com outra caixa não é troca: nada de derrubar sessão")
    void updateIdentity_sameEmailDifferentCase_noop() {
        user.setEmail("b@x.com");

        TenantUser saved = service.updateIdentity(userId, null, "B@X.com");

        assertThat(saved.getTokenVersion()).isZero();
        verify(tenantUserRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("e-mail já usado por outro login: 409 e nada gravado")
    void updateIdentity_duplicateEmail_conflict() {
        when(tenantUserRepository.existsByEmail("ana@loja.com")).thenReturn(true);

        assertThatThrownBy(() -> service.updateIdentity(userId, "Outro", "ana@loja.com"))
                .isInstanceOf(ConflictException.class);
        verify(tenantUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("e-mail inválido ou nome vazio: 400")
    void updateIdentity_invalid() {
        assertThatThrownBy(() -> service.updateIdentity(userId, null, "sem-arroba"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateIdentity(userId, "   ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateIdentity(userId, "x".repeat(PanelUserService.MAX_NAME + 1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("usuário de outra barbearia: 404 ao editar nome/e-mail")
    void updateIdentity_otherTenant_notFound() {
        UUID other = UUID.randomUUID();
        when(tenantUserRepository.findById(other)).thenReturn(Optional.of(
                TenantUser.builder().id(other).barbershopId(UUID.randomUUID()).email("z@x").build()));

        assertThatThrownBy(() -> service.updateIdentity(other, "X", null)).isInstanceOf(NotFoundException.class);
    }
}
