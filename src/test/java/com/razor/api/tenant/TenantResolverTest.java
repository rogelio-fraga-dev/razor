package com.razor.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.razor.api.model.Barbershop;
import com.razor.api.repository.BarbershopRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantResolverTest {

    @Mock private BarbershopRepository barbershopRepository;
    @InjectMocks private TenantResolver resolver;

    @Test
    @DisplayName("instância conhecida resolve para a barbearia correspondente")
    void knownInstance_resolvesToShop() {
        UUID shopId = UUID.randomUUID();
        when(barbershopRepository.findByWhatsappInstance("figaro-wa"))
                .thenReturn(Optional.of(Barbershop.builder().id(shopId).build()));

        assertThat(resolver.resolveByInstance("figaro-wa")).isEqualTo(shopId);
    }

    @Test
    @DisplayName("instância desconhecida recai no tenant padrão")
    void unknownInstance_fallsBackToDefault() {
        when(barbershopRepository.findByWhatsappInstance("nao-existe")).thenReturn(Optional.empty());

        assertThat(resolver.resolveByInstance("nao-existe")).isEqualTo(TenantContext.DEFAULT_BARBERSHOP_ID);
    }

    @Test
    @DisplayName("instância nula/vazia recai no padrão sem consultar o banco")
    void nullOrBlank_defaultWithoutQuery() {
        assertThat(resolver.resolveByInstance(null)).isEqualTo(TenantContext.DEFAULT_BARBERSHOP_ID);
        assertThat(resolver.resolveByInstance("   ")).isEqualTo(TenantContext.DEFAULT_BARBERSHOP_ID);
        verifyNoInteractions(barbershopRepository);
    }
}
