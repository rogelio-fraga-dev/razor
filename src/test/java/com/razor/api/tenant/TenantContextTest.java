package com.razor.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("sem tenant definido, getCurrent recusa em vez de assumir uma barbearia")
    void getCurrent_whenUnset_throws() {
        // A DefaultTenantExtension define o tenant antes de cada teste; aqui a ausencia dele e
        // justamente o que esta sob teste, entao o contexto e limpo de proposito.
        TenantContext.clear();

        assertThat(TenantContext.isSet()).isFalse();
        assertThatThrownBy(TenantContext::getCurrent)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nenhuma barbearia no contexto");
    }

    @Test
    @DisplayName("set define o tenant atual da thread")
    void set_overridesCurrent() {
        UUID id = UUID.randomUUID();
        TenantContext.set(id);

        assertThat(TenantContext.isSet()).isTrue();
        assertThat(TenantContext.getCurrent()).isEqualTo(id);
    }

    @Test
    @DisplayName("clear devolve a thread ao estado sem tenant, nao a um tenant padrão")
    void clear_leavesNoTenant() {
        TenantContext.set(UUID.randomUUID());

        TenantContext.clear();

        assertThat(TenantContext.isSet()).isFalse();
        assertThatThrownBy(TenantContext::getCurrent).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("o id do tenant herdado (V4) continua disponível como constante")
    void defaultBarbershopId_stillAvailableAsConstant() {
        // Continua existindo — deixou de ser fallback, nao deixou de ser o id da barbearia
        // semeada na V4, que o onboarding e os scripts de seed ainda referenciam.
        assertThat(TenantContext.DEFAULT_BARBERSHOP_ID)
                .hasToString("00000000-0000-0000-0000-000000000001");
    }
}
