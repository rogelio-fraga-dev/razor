package com.razor.api.service;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.BarbershopAdmin;
import com.razor.api.repository.BarbershopAdminRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.PhoneNumbers;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Diretorio de admins por tenant (Fase 1). Resolve quais telefones sao administrativos para a
 * barbearia atual e se um remetente e admin — base do roteamento admin vs cliente e das
 * notificacoes internas.
 *
 * <p><b>Fallback sem regressao:</b> enquanto o tenant nao tiver admins cadastrados em
 * {@code barbershop_admins}, recai nos telefones do config global ({@code razor.admin.phones}).
 * Assim o tenant padrao (tabela vazia) se comporta exatamente como antes desta mudanca.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantAdminService {

    private final BarbershopAdminRepository barbershopAdminRepository;
    private final RazorProperties properties;

    /** Telefones admin do tenant atual; se nao houver cadastro, usa o config global (fallback). */
    public List<String> adminPhones() {
        List<BarbershopAdmin> rows = barbershopAdminRepository.findByBarbershopId(TenantContext.getCurrent());
        if (rows == null || rows.isEmpty()) {
            return properties.getAdmin().getPhones();
        }
        return rows.stream().map(BarbershopAdmin::getPhone).collect(Collectors.toList());
    }

    /** true se o telefone (normalizado) e admin do tenant atual. */
    public boolean isAdmin(String phone) {
        String normalized = PhoneNumbers.normalizeBrazilian(phone);
        return adminPhones().stream()
                .anyMatch(a -> PhoneNumbers.normalizeBrazilian(a).equals(normalized));
    }

    /** Cadastra um telefone administrativo para o tenant atual (usado no onboarding). */
    public BarbershopAdmin add(String phone, String name) {
        BarbershopAdmin created = barbershopAdminRepository.save(BarbershopAdmin.builder()
                .barbershopId(TenantContext.getCurrent())
                .phone(phone)
                .name(name)
                .build());
        log.info("[ADMIN] Telefone administrativo cadastrado para o tenant {}", TenantContext.getCurrent());
        return created;
    }
}
