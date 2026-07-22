package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.BarbershopSettings;
import com.razor.api.repository.BarbershopSettingsRepository;
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
class BarbershopSettingsServiceTest {

    @Mock private BarbershopSettingsRepository repository;
    @Mock private RazorProperties properties;

    @InjectMocks private BarbershopSettingsService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void givenConfigMenu() {
        RazorProperties.Menu menu = new RazorProperties.Menu();
        menu.setAddressText("Endereço config");
        menu.setServicesText("Serviços config");
        menu.setInstagramUrl("insta-config");
        when(properties.getMenu()).thenReturn(menu);
    }

    @Test
    @DisplayName("resolve sem linha do tenant cai no config global")
    void resolve_noRow_usesConfig() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        givenConfigMenu();

        var bot = service.resolve();

        assertThat(bot.addressText()).isEqualTo("Endereço config");
        assertThat(bot.servicesText()).isEqualTo("Serviços config");
        assertThat(bot.instagramUrl()).isEqualTo("insta-config");
    }

    @Test
    @DisplayName("resolve usa o valor do tenant e cai no config só nos campos vazios")
    void resolve_perFieldFallback() {
        when(repository.findById(any())).thenReturn(Optional.of(BarbershopSettings.builder()
                .barbershopId(TenantContext.DEFAULT_BARBERSHOP_ID)
                .servicesText("Serviços da loja")
                .build())); // address vazio -> cai no config
        givenConfigMenu();

        var bot = service.resolve();

        assertThat(bot.servicesText()).isEqualTo("Serviços da loja"); // do tenant
        assertThat(bot.addressText()).isEqualTo("Endereço config");   // fallback
    }

    @Test
    @DisplayName("update faz upsert com os campos informados")
    void update_upserts() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        when(repository.save(any(BarbershopSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        BarbershopSettings saved = service.update("End", "maps", "Serv", "sched", "insta");

        assertThat(saved.getAddressText()).isEqualTo("End");
        assertThat(saved.getInstagramUrl()).isEqualTo("insta");
        assertThat(saved.getBarbershopId()).isEqualTo(TenantContext.DEFAULT_BARBERSHOP_ID);
    }
}
