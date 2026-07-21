package com.razor.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.dto.ServiceOfferingResponse;
import com.razor.api.dto.ServiceRequest;
import com.razor.api.model.ServiceOffering;
import com.razor.api.service.ServiceCatalogService;
import java.math.BigDecimal;
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
class AdminServiceCatalogControllerTest {

    @Mock private ServiceCatalogService serviceCatalogService;
    @InjectMocks private AdminServiceCatalogController controller;

    private ServiceOffering svc(UUID id, String name) {
        return ServiceOffering.builder().id(id).name(name).price(new BigDecimal("35.00"))
                .durationMinutes(30).active(true).build();
    }

    @Test
    @DisplayName("listar mapeia o catálogo para a resposta")
    void list_mapsCatalog() {
        when(serviceCatalogService.listAll()).thenReturn(List.of(svc(UUID.randomUUID(), "Corte")));

        List<ServiceOfferingResponse> out = controller.list();

        assertThat(out).hasSize(1);
        assertThat(out.get(0).name()).isEqualTo("Corte");
    }

    @Test
    @DisplayName("criar retorna 201 com o serviço criado")
    void create_returns201() {
        UUID id = UUID.randomUUID();
        when(serviceCatalogService.create(eq("Corte"), eq(new BigDecimal("35.00")), eq(30)))
                .thenReturn(svc(id, "Corte"));

        var resp = controller.create(new ServiceRequest("Corte", new BigDecimal("35.00"), 30, null));

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody().id()).isEqualTo(id);
    }

    @Test
    @DisplayName("atualizar sem 'active' assume ativo")
    void update_defaultsActiveTrue() {
        UUID id = UUID.randomUUID();
        when(serviceCatalogService.update(eq(id), eq("Corte"), eq(new BigDecimal("35.00")), eq(30), eq(true)))
                .thenReturn(svc(id, "Corte"));

        controller.update(id, new ServiceRequest("Corte", new BigDecimal("35.00"), 30, null));

        verify(serviceCatalogService).update(eq(id), eq("Corte"), eq(new BigDecimal("35.00")), eq(30), eq(true));
    }
}
