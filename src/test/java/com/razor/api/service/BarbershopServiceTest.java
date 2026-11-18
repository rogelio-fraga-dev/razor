package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Barbershop;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.tenant.TenantContext;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
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
class BarbershopServiceTest {

    @Mock private BarbershopRepository barbershopRepository;
    @InjectMocks private BarbershopService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Barbershop shop() {
        return Barbershop.builder()
                .id(TenantContext.DEFAULT_BARBERSHOP_ID).name("Antiga").slug("figaro")
                .whatsappInstance("inst-antiga").active(true).build();
    }

    @Test
    @DisplayName("current devolve a barbearia do tenant atual")
    void current_returnsTenantShop() {
        when(barbershopRepository.findById(TenantContext.DEFAULT_BARBERSHOP_ID)).thenReturn(Optional.of(shop()));
        assertThat(service.current().getSlug()).isEqualTo("figaro");
    }

    @Test
    @DisplayName("updateProfile altera o nome e preserva a instância (dono não escolhe instância)")
    void updateProfile_updatesNameOnly() {
        Barbershop s = shop();
        when(barbershopRepository.findById(TenantContext.DEFAULT_BARBERSHOP_ID)).thenReturn(Optional.of(s));
        when(barbershopRepository.save(any(Barbershop.class))).thenAnswer(inv -> inv.getArgument(0));

        Barbershop saved = service.updateProfile("Nova");

        assertThat(saved.getName()).isEqualTo("Nova");
        assertThat(saved.getWhatsappInstance()).isEqualTo("inst-antiga");
        verify(barbershopRepository, never()).findByWhatsappInstance(any());
    }

    @Test
    @DisplayName("current lança NotFound quando o tenant não existe")
    void current_notFound_throws() {
        when(barbershopRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.current()).isInstanceOf(NotFoundException.class);
    }
}
