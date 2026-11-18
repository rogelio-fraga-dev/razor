package com.razor.api.service;

import com.razor.api.model.TenantUser;
import com.razor.api.model.UserRole;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.TenantUserRepository;
import com.razor.api.security.SessionValidator;
import com.razor.api.tenant.TenantContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logins do painel do tenant ("Equipe & acesso"): cadastro, papel, ativo/inativo, senha
 * provisoria e o vinculo login → profissional (V44). Escopado no tenant atual: usuario ou
 * profissional de outra barbearia responde 404, como se nao existisse.
 *
 * <p>Regras que protegem o proprio dono de se trancar do lado de fora:
 * <ul>
 *   <li>ninguem desativa nem rebaixa a si mesmo (o proprio papel muda so por outro dono);</li>
 *   <li>a barbearia nunca fica sem dono ativo — desativar ou rebaixar o ultimo e recusado;</li>
 *   <li>senha alheia so e redefinida aqui; a propria se troca em "Meu perfil" (pede a atual).</li>
 * </ul>
 * Toda mudanca de papel, ativo ou senha incrementa a {@code token_version} do usuario: as
 * sessoes abertas dele caem na hora (ver {@link SessionValidator}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PanelUserService {

    /** Mesma regra da troca de senha em "Meu perfil" e do onboarding (min. 8). */
    static final int MIN_PASSWORD = 8;
    static final int MAX_PASSWORD = 100;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final TenantUserRepository tenantUserRepository;
    private final ProfessionalRepository professionalRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionValidator sessionValidator;

    public List<TenantUser> list() {
        return tenantUserRepository.findByBarbershopIdOrderByNameAsc(TenantContext.getCurrent());
    }

    /** Cria um login do painel na barbearia atual, com senha inicial informada pelo dono. */
    @Transactional
    public TenantUser create(String name, String email, UserRole role, String rawPassword, UUID professionalId) {
        UUID shop = TenantContext.getCurrent();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Informe o nome.");
        }
        if (role == null) {
            throw new IllegalArgumentException("Escolha o papel.");
        }
        String normalized = AuthService.normalizeEmail(email);
        if (normalized.length() > 255 || !EMAIL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("E-mail inválido.");
        }
        validatePassword(rawPassword);
        // O email e o login: unico na plataforma inteira, nao so no tenant.
        if (tenantUserRepository.existsByEmail(normalized)) {
            throw new ConflictException("Já existe um login com o e-mail " + normalized + ".");
        }
        if (professionalId != null) {
            checkLinkable(shop, role, professionalId, null);
        }

        TenantUser user = TenantUser.builder()
                .barbershopId(shop)
                .name(name.trim())
                .email(normalized)
                .role(role)
                .active(true)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .professionalId(professionalId)
                .build();
        try {
            TenantUser saved = tenantUserRepository.saveAndFlush(user);
            log.info("[PANEL-USER] Login {} ({}) criado no tenant {}", saved.getEmail(), role, shop);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Corrida: outro cadastro com o mesmo email (ou o mesmo profissional) entre a checagem
            // e o insert. O indice unico do banco decide; aqui so vira mensagem.
            throw new ConflictException("Esse e-mail (ou profissional) acabou de ser usado em outro login.");
        }
    }

    /** Troca o papel. Nao vale para si mesmo nem para rebaixar o ultimo dono ativo. */
    @Transactional
    public TenantUser changeRole(UUID actorId, UUID userId, UserRole role) {
        if (role == null) {
            throw new IllegalArgumentException("Escolha o papel.");
        }
        TenantUser user = findInTenant(userId);
        if (user.getRole() == role) {
            return user;
        }
        if (user.getId().equals(actorId)) {
            throw new ConflictException("Você não pode mudar o seu próprio papel. Peça a outro dono.");
        }
        if (user.getRole() == UserRole.OWNER && user.isActive()) {
            ensureAnotherActiveOwner(user);
        }
        user.setRole(role);
        // Recepcao nao atende cliente: nao tem "Meu dia" nem comissao, entao perde o vinculo.
        if (role == UserRole.RECEPTION) {
            user.setProfessionalId(null);
        }
        return saveAndRevoke(user, "papel → " + role);
    }

    /** Desativa (bloqueia o login e derruba as sessoes) ou reativa um usuario. */
    @Transactional
    public TenantUser setActive(UUID actorId, UUID userId, boolean active) {
        TenantUser user = findInTenant(userId);
        if (user.isActive() == active) {
            return user;
        }
        if (!active) {
            if (user.getId().equals(actorId)) {
                throw new ConflictException("Você não pode desativar o seu próprio acesso.");
            }
            if (user.getRole() == UserRole.OWNER) {
                ensureAnotherActiveOwner(user);
            }
        }
        user.setActive(active);
        return saveAndRevoke(user, active ? "reativado" : "desativado");
    }

    /** Define uma senha provisoria para outro usuario (que esqueceu a dele). */
    @Transactional
    public TenantUser resetPassword(UUID actorId, UUID userId, String rawPassword) {
        TenantUser user = findInTenant(userId);
        if (user.getId().equals(actorId)) {
            throw new IllegalArgumentException("Para trocar a sua própria senha, use \"Meu perfil\".");
        }
        validatePassword(rawPassword);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return saveAndRevoke(user, "senha redefinida");
    }

    /** Liga (ou desliga, com {@code professionalId} nulo) o login a um cadastro de profissional. */
    @Transactional
    public TenantUser linkProfessional(UUID userId, UUID professionalId) {
        UUID shop = TenantContext.getCurrent();
        TenantUser user = findInTenant(userId);
        if (professionalId != null) {
            checkLinkable(shop, user.getRole(), professionalId, userId);
        }
        user.setProfessionalId(professionalId);
        return tenantUserRepository.save(user);
    }

    // ------------------------------------------------------------------------------------------

    private TenantUser findInTenant(UUID userId) {
        UUID shop = TenantContext.getCurrent();
        return tenantUserRepository.findById(userId)
                .filter(u -> Objects.equals(u.getBarbershopId(), shop))
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
    }

    private void checkLinkable(UUID shop, UserRole role, UUID professionalId, UUID userId) {
        if (role == UserRole.RECEPTION) {
            throw new IllegalArgumentException("Recepção não é ligada a um profissional.");
        }
        professionalRepository.findById(professionalId)
                .filter(p -> Objects.equals(p.getBarbershopId(), shop))
                .orElseThrow(() -> new NotFoundException("Profissional não encontrado."));
        tenantUserRepository.findByBarbershopIdAndProfessionalId(shop, professionalId)
                .filter(other -> !other.getId().equals(userId))
                .ifPresent(other -> {
                    throw new ConflictException("Esse profissional já está ligado ao login de "
                            + (other.getName() != null ? other.getName() : other.getEmail()) + ".");
                });
    }

    /**
     * Recusa se {@code leaving} for o ultimo dono ativo. A consulta trava (FOR UPDATE) as linhas
     * dos donos ativos: dois donos rebaixando um ao outro ao mesmo tempo sao serializados, e o
     * segundo enxerga o resultado do primeiro.
     */
    private void ensureAnotherActiveOwner(TenantUser leaving) {
        boolean another = tenantUserRepository.lockActiveByRole(leaving.getBarbershopId(), UserRole.OWNER).stream()
                .anyMatch(u -> !u.getId().equals(leaving.getId()));
        if (!another) {
            throw new ConflictException("A barbearia precisa de pelo menos um dono ativo.");
        }
    }

    private static void validatePassword(String raw) {
        if (raw == null || raw.length() < MIN_PASSWORD) {
            throw new IllegalArgumentException("A senha precisa de pelo menos " + MIN_PASSWORD + " caracteres.");
        }
        if (raw.length() > MAX_PASSWORD) {
            throw new IllegalArgumentException("A senha pode ter no máximo " + MAX_PASSWORD + " caracteres.");
        }
    }

    /** Grava e derruba as sessoes abertas do usuario (versao do token + cache do validador). */
    private TenantUser saveAndRevoke(TenantUser user, String what) {
        user.setTokenVersion(user.getTokenVersion() + 1);
        TenantUser saved = tenantUserRepository.save(user);
        sessionValidator.invalidate(saved.getId());
        log.info("[PANEL-USER] {} — {} (tenant {})", saved.getEmail(), what, saved.getBarbershopId());
        return saved;
    }
}
