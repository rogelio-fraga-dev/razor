package com.razor.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.dto.AdminContactRequest;
import com.razor.api.model.BarbershopAdmin;
import com.razor.api.service.TenantAdminService;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminBarbershopAdminControllerTest {

    @Mock private TenantAdminService tenantAdminService;
    @InjectMocks private AdminBarbershopAdminController controller;

    private BarbershopAdmin admin(UUID id) {
        return BarbershopAdmin.builder().id(id).phone("5521970001111").name("Chefe").build();
    }

    @Test
    @DisplayName("listar mapeia os admins do tenant")
    void list_mapsAdmins() {
        when(tenantAdminService.listAdmins()).thenReturn(List.of(admin(UUID.randomUUID())));
        assertThat(controller.list()).hasSize(1);
    }

    @Test
    @DisplayName("adicionar retorna 201 com o admin criado")
    void add_returns201() {
        UUID id = UUID.randomUUID();
        when(tenantAdminService.add("5521970001111", "Chefe")).thenReturn(admin(id));

        var resp = controller.add(new AdminContactRequest("5521970001111", "Chefe"));

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody().id()).isEqualTo(id);
    }

    @Test
    @DisplayName("remover chama o serviço e responde 204")
    void remove_returns204() {
        UUID id = UUID.randomUUID();
        var resp = controller.remove(id);
        assertThat(resp.getStatusCode().value()).isEqualTo(204);
        verify(tenantAdminService).remove(id);
    }
}
