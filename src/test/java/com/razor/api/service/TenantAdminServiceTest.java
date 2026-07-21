package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.BarbershopAdmin;
import com.razor.api.repository.BarbershopAdminRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Testes do {@link TenantAdminService}: fallback pro config global quando o tenant nao tem
 * admins cadastrados, uso dos admins do tenant quando existem, e reconhecimento por telefone
 * (normalizado) — tudo com o repositorio mockado.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantAdminServiceTest {

    @Mock private BarbershopAdminRepository barbershopAdminRepository;
    @Mock private RazorProperties properties;

    @InjectMocks private TenantAdminService tenantAdminService;

    private void givenConfigAdmins(String... phones) {
        RazorProperties.Admin admin = new RazorProperties.Admin();
        admin.setPhones(List.of(phones));
        when(properties.getAdmin()).thenReturn(admin);
    }

    private BarbershopAdmin admin(String phone) {
        return BarbershopAdmin.builder().id(UUID.randomUUID()).phone(phone).build();
    }

    @Test
    @DisplayName("sem admins no tenant: cai no config global (sem regressao para o tenant padrao)")
    void adminPhones_emptyTable_fallsBackToConfig() {
        when(barbershopAdminRepository.findByBarbershopId(any())).thenReturn(List.of());
        givenConfigAdmins("5511988887777");

        assertThat(tenantAdminService.adminPhones()).containsExactly("5511988887777");
    }

    @Test
    @DisplayName("repositorio nulo tambem cai no config global (nunca quebra)")
    void adminPhones_nullTable_fallsBackToConfig() {
        when(barbershopAdminRepository.findByBarbershopId(any())).thenReturn(null);
        givenConfigAdmins("5511988887777");

        assertThat(tenantAdminService.adminPhones()).containsExactly("5511988887777");
    }

    @Test
    @DisplayName("com admins cadastrados: usa os do tenant e ignora o config global")
    void adminPhones_withRows_usesTenantAdmins() {
        when(barbershopAdminRepository.findByBarbershopId(any()))
                .thenReturn(List.of(admin("5521970001111"), admin("5521970002222")));
        givenConfigAdmins("5511988887777"); // nao deve ser usado

        assertThat(tenantAdminService.adminPhones())
                .containsExactly("5521970001111", "5521970002222")
                .doesNotContain("5511988887777");
    }

    @Test
    @DisplayName("isAdmin reconhece o numero mesmo em formatos diferentes (normalizacao BR)")
    void isAdmin_matchesAcrossFormats() {
        when(barbershopAdminRepository.findByBarbershopId(any())).thenReturn(List.of());
        givenConfigAdmins("5511988887777"); // com DDI e nono digito

        // mesmo numero sem DDI e sem o nono digito
        assertThat(tenantAdminService.isAdmin("1188887777")).isTrue();
        assertThat(tenantAdminService.isAdmin("5511911112222")).isFalse();
    }

    @Test
    @DisplayName("add persiste o admin no tenant atual")
    void add_persists() {
        when(barbershopAdminRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BarbershopAdmin created = tenantAdminService.add("5511988887777", "Chefe");

        assertThat(created.getPhone()).isEqualTo("5511988887777");
        assertThat(created.getName()).isEqualTo("Chefe");
    }
}
