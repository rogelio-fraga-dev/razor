package com.razor.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.dto.BarbershopResponse;
import com.razor.api.dto.BarbershopUpdateRequest;
import com.razor.api.model.Barbershop;
import com.razor.api.service.BarbershopService;
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
class AdminBarbershopControllerTest {

    @Mock private BarbershopService barbershopService;
    @InjectMocks private AdminBarbershopController controller;

    private Barbershop shop() {
        return Barbershop.builder().id(UUID.randomUUID()).name("Loja").slug("loja")
                .whatsappInstance("inst").active(true).build();
    }

    @Test
    @DisplayName("get devolve o perfil da barbearia")
    void get_returnsProfile() {
        when(barbershopService.current()).thenReturn(shop());
        BarbershopResponse resp = controller.get();
        assertThat(resp.slug()).isEqualTo("loja");
    }

    @Test
    @DisplayName("update chama updateProfile com nome e instância")
    void update_delegates() {
        when(barbershopService.updateProfile("Nova", "nova-inst")).thenReturn(shop());
        controller.update(new BarbershopUpdateRequest("Nova", "nova-inst"));
        verify(barbershopService).updateProfile("Nova", "nova-inst");
    }
}
