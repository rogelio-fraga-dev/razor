package com.razor.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.dto.ProfessionalRequest;
import com.razor.api.dto.ProfessionalResponse;
import com.razor.api.model.Professional;
import com.razor.api.service.ProfessionalService;
import java.math.BigDecimal;
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
class AdminProfessionalControllerTest {

    @Mock private ProfessionalService professionalService;
    @InjectMocks private AdminProfessionalController controller;

    private Professional prof(UUID id) {
        return Professional.builder().id(id).name("Rafael").active(true).commissionPercent(BigDecimal.ZERO).build();
    }

    @Test
    @DisplayName("criar sem comissão chama create e não mexe na comissão")
    void create_withoutCommission() {
        UUID id = UUID.randomUUID();
        when(professionalService.create("Rafael", "5511999998888")).thenReturn(prof(id));

        var resp = controller.create(new ProfessionalRequest("Rafael", "5511999998888", null, null, null));

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        verify(professionalService, never()).updateCommission(any(), any());
    }

    @Test
    @DisplayName("criar com comissão chama create e depois updateCommission")
    void create_withCommission() {
        UUID id = UUID.randomUUID();
        when(professionalService.create("Rafael", null)).thenReturn(prof(id));
        when(professionalService.updateCommission(eq(id), any())).thenReturn(prof(id));

        controller.create(new ProfessionalRequest("Rafael", null, null, new BigDecimal("40"), null));

        verify(professionalService).updateCommission(eq(id), eq(new BigDecimal("40")));
    }

    @Test
    @DisplayName("atualizar aplica active e comissão (defaults quando nulos)")
    void update_appliesFields() {
        UUID id = UUID.randomUUID();
        when(professionalService.update(eq(id), eq("Rafael"), eq("5511999998888"), eq(false), any(), any()))
                .thenReturn(prof(id));

        ProfessionalResponse resp = controller.update(id,
                new ProfessionalRequest("Rafael", "5511999998888", false, new BigDecimal("25"), null));

        assertThat(resp.name()).isEqualTo("Rafael");
        verify(professionalService).update(eq(id), eq("Rafael"), eq("5511999998888"), eq(false), eq(new BigDecimal("25")), isNull());
    }
}
