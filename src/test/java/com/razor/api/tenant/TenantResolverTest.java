package com.razor.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.razor.api.config.RazorProperties;
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
    @Mock private RazorProperties properties;
    @InjectMocks private TenantResolver resolver;

    private RazorProperties.Evolution evolutionCfg(String instanceName) {
        RazorProperties.Evolution evo = new RazorProperties.Evolution();
        evo.setInstanceName(instanceName);
        return evo;
    }

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

    // ---------- roteamento de saída (outboundInstance) ----------

    @Test
    @DisplayName("saída usa a instância da barbearia do tenant atual")
    void outboundInstance_usesBarbershopInstance() {
        when(barbershopRepository.findById(TenantContext.DEFAULT_BARBERSHOP_ID))
                .thenReturn(Optional.of(Barbershop.builder().whatsappInstance("loja2-wa").build()));

        assertThat(resolver.outboundInstance()).isEqualTo("loja2-wa");
    }

    @Test
    @DisplayName("saída recai na instância do config quando a barbearia não tem instância")
    void outboundInstance_fallsBackToConfig_whenBlankInstance() {
        when(barbershopRepository.findById(TenantContext.DEFAULT_BARBERSHOP_ID))
                .thenReturn(Optional.of(Barbershop.builder().whatsappInstance("  ").build()));
        when(properties.getEvolution()).thenReturn(evolutionCfg("BarberBotOficial"));

        assertThat(resolver.outboundInstance()).isEqualTo("BarberBotOficial");
    }

    @Test
    @DisplayName("saída recai no config quando o tenant não é encontrado")
    void outboundInstance_fallsBackToConfig_whenTenantUnknown() {
        when(barbershopRepository.findById(TenantContext.DEFAULT_BARBERSHOP_ID)).thenReturn(Optional.empty());
        when(properties.getEvolution()).thenReturn(evolutionCfg("BarberBotOficial"));

        assertThat(resolver.outboundInstance()).isEqualTo("BarberBotOficial");
    }
}
