package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.config.RazorProperties;
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
import org.springframework.web.reactive.function.client.WebClient;

/** Lógica de resolução do nome da instância (o resto fala com a Evolution e é validado ponta a ponta). */
@ExtendWith(MockitoExtension.class)
class WhatsAppConnectionServiceTest {

    @Mock private WebClient webClient;
    @Mock private RazorProperties properties;
    @Mock private BarbershopRepository barbershopRepository;
    @InjectMocks private WhatsAppConnectionService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("usa a instância já definida no tenant, sem salvar")
    void ensureInstanceName_existing() {
        Barbershop shop = Barbershop.builder()
                .id(TenantContext.getCurrent()).slug("figaro").whatsappInstance("figaro-wa").build();
        when(barbershopRepository.findById(TenantContext.getCurrent())).thenReturn(Optional.of(shop));

        assertThat(service.ensureInstanceName()).isEqualTo("figaro-wa");
        verify(barbershopRepository, never()).save(any());
    }

    @Test
    @DisplayName("deriva a instância do slug e persiste quando ausente")
    void ensureInstanceName_derivesFromSlug() {
        Barbershop shop = Barbershop.builder()
                .id(TenantContext.getCurrent()).slug("figaro").whatsappInstance(null).build();
        when(barbershopRepository.findById(TenantContext.getCurrent())).thenReturn(Optional.of(shop));
        when(barbershopRepository.save(any(Barbershop.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.ensureInstanceName()).isEqualTo("figaro");
        assertThat(shop.getWhatsappInstance()).isEqualTo("figaro");
        verify(barbershopRepository).save(shop);
    }

    @Test
    @DisplayName("barbearia inexistente lança NotFound")
    void ensureInstanceName_notFound() {
        when(barbershopRepository.findById(TenantContext.getCurrent())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ensureInstanceName()).isInstanceOf(NotFoundException.class);
    }
}
