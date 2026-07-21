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
import java.util.UUID;
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
    @DisplayName("updateProfile altera nome e instância")
    void updateProfile_updates() {
        Barbershop s = shop();
        when(barbershopRepository.findById(TenantContext.DEFAULT_BARBERSHOP_ID)).thenReturn(Optional.of(s));
        when(barbershopRepository.findByWhatsappInstance("nova-inst")).thenReturn(Optional.empty());
        when(barbershopRepository.save(any(Barbershop.class))).thenAnswer(inv -> inv.getArgument(0));

        Barbershop saved = service.updateProfile("Nova", "nova-inst");

        assertThat(saved.getName()).isEqualTo("Nova");
        assertThat(saved.getWhatsappInstance()).isEqualTo("nova-inst");
    }

    @Test
    @DisplayName("updateProfile rejeita instância já usada por outro tenant")
    void updateProfile_instanceInUse_throws() {
        when(barbershopRepository.findById(TenantContext.DEFAULT_BARBERSHOP_ID)).thenReturn(Optional.of(shop()));
        when(barbershopRepository.findByWhatsappInstance("nova-inst"))
                .thenReturn(Optional.of(Barbershop.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> service.updateProfile("Nova", "nova-inst"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(barbershopRepository, never()).save(any());
    }

    @Test
    @DisplayName("current lança NotFound quando o tenant não existe")
    void current_notFound_throws() {
        when(barbershopRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.current()).isInstanceOf(NotFoundException.class);
    }
}
