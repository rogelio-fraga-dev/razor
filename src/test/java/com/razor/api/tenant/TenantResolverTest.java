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
    @DisplayName("SEGURANÇA: instância desconhecida NÃO cai no tenant padrão (devolve null)")
    void unknownInstance_isRejected() {
        when(barbershopRepository.findByWhatsappInstance("nao-existe")).thenReturn(Optional.empty());

        // Cair no tenant padrão permitiria injetar mensagem forjada (inclusive comando de admin)
        // numa barbearia real. A mensagem tem de ser descartada.
        assertThat(resolver.resolveByInstance("nao-existe")).isNull();
    }

    @Test
    @DisplayName("SEGURANÇA: instância nula/vazia é rejeitada sem consultar o banco")
    void nullOrBlank_isRejected() {
        assertThat(resolver.resolveByInstance(null)).isNull();
        assertThat(resolver.resolveByInstance("   ")).isNull();
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
