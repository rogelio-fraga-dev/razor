package com.razor.api.service;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.BarbershopAdmin;
import com.razor.api.repository.BarbershopAdminRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.PhoneNumbers;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Diretorio de admins por tenant (Fase 1). Resolve quais telefones sao administrativos para a
 * barbearia atual e se um remetente e admin — base do roteamento admin vs cliente e das
 * notificacoes internas.
 *
 * <p><b>Fallback so no tenant padrao:</b> enquanto o tenant padrao
 * ({@link TenantContext#DEFAULT_BARBERSHOP_ID}) nao tiver admins cadastrados em
 * {@code barbershop_admins}, recai nos telefones do config global ({@code razor.admin.phones}) —
 * assim a instalacao single-tenant original continua funcionando. Qualquer OUTRO tenant sem
 * admins fica sem admin nenhum: aplicar o fallback a ele daria ao dono da plataforma (ou a quem
 * estiver no config) poder de admin dentro de uma barbearia alheia. Para nao chegar a esse
 * estado pelo painel, remover o ultimo admin de um tenant e recusado (409).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantAdminService {

    private final BarbershopAdminRepository barbershopAdminRepository;
    private final RazorProperties properties;

    /**
     * Telefones admin do tenant atual. Sem cadastro: o tenant padrao usa o config global
     * (fallback); os demais nao tem admin.
     */
    public List<String> adminPhones() {
        UUID tenant = TenantContext.getCurrent();
        List<BarbershopAdmin> rows = barbershopAdminRepository.findByBarbershopId(tenant);
        if (rows == null || rows.isEmpty()) {
            if (!TenantContext.DEFAULT_BARBERSHOP_ID.equals(tenant)) {
                return List.of();
            }
            List<String> fallback = properties.getAdmin().getPhones();
            return fallback != null ? fallback : List.of();
        }
        return rows.stream().map(BarbershopAdmin::getPhone).collect(Collectors.toList());
    }

    /** true se o telefone (normalizado) e admin do tenant atual. */
    public boolean isAdmin(String phone) {
        String normalized = PhoneNumbers.normalizeBrazilian(phone);
        return adminPhones().stream()
                .anyMatch(a -> PhoneNumbers.normalizeBrazilian(a).equals(normalized));
    }

    /** Cadastra um telefone administrativo para o tenant atual (usado no onboarding e no painel). */
    public BarbershopAdmin add(String phone, String name) {
        if (barbershopAdminRepository.existsByBarbershopIdAndPhone(TenantContext.getCurrent(), phone)) {
            throw new IllegalArgumentException("Este telefone ja e administrador.");
        }
        BarbershopAdmin created = barbershopAdminRepository.save(BarbershopAdmin.builder()
                .barbershopId(TenantContext.getCurrent())
                .phone(phone)
                .name(name)
                .build());
        log.info("[ADMIN] Telefone administrativo cadastrado para o tenant {}", TenantContext.getCurrent());
        return created;
    }

    /** Admins cadastrados no tenant atual (as linhas reais, sem o fallback do config). */
    public List<BarbershopAdmin> listAdmins() {
        return barbershopAdminRepository.findByBarbershopId(TenantContext.getCurrent());
    }

    /**
     * Remove um admin do tenant atual (isolamento: rejeita admin de outro tenant). Recusa remover o
     * ultimo: o tenant ficaria sem ninguem para comandar o bot e receber alertas.
     */
    public void remove(UUID id) {
        BarbershopAdmin admin = barbershopAdminRepository.findById(id)
                .filter(a -> TenantContext.getCurrent().equals(a.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Administrador nao encontrado."));
        List<BarbershopAdmin> current = barbershopAdminRepository.findByBarbershopId(TenantContext.getCurrent());
        if (current == null || current.size() <= 1) {
            throw new ConflictException(
                    "Nao e possivel remover o ultimo administrador. Cadastre outro telefone antes de remover este.");
        }
        barbershopAdminRepository.delete(admin);
        log.info("[ADMIN] Administrador removido do tenant {}", TenantContext.getCurrent());
    }
}
