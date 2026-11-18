package com.razor.api.service;

import com.razor.api.model.TenantUser;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.TenantUserRepository;
import com.razor.api.tenant.TenantContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logins do painel do tenant e o vinculo login → profissional (V44). Escopado no tenant atual:
 * usuario ou profissional de outra barbearia responde 404, como se nao existisse.
 */
@Service
@RequiredArgsConstructor
public class PanelUserService {

    private final TenantUserRepository tenantUserRepository;
    private final ProfessionalRepository professionalRepository;

    public List<TenantUser> list() {
        return tenantUserRepository.findByBarbershopIdOrderByNameAsc(TenantContext.getCurrent());
    }

    /** Liga (ou desliga, com {@code professionalId} nulo) o login a um cadastro de profissional. */
    @Transactional
    public TenantUser linkProfessional(UUID userId, UUID professionalId) {
        UUID shop = TenantContext.getCurrent();
        TenantUser user = tenantUserRepository.findById(userId)
                .filter(u -> Objects.equals(u.getBarbershopId(), shop))
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));

        if (professionalId != null) {
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
        user.setProfessionalId(professionalId);
        return tenantUserRepository.save(user);
    }
}
