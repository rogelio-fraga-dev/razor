package com.razor.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("sem tenant definido, recai no tenant padrão (V4)")
    void default_whenUnset() {
        assertThat(TenantContext.isSet()).isFalse();
        assertThat(TenantContext.getCurrent()).isEqualTo(TenantContext.DEFAULT_BARBERSHOP_ID);
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
    @DisplayName("clear volta ao tenant padrão")
    void clear_resetsToDefault() {
        TenantContext.set(UUID.randomUUID());

        TenantContext.clear();

        assertThat(TenantContext.isSet()).isFalse();
        assertThat(TenantContext.getCurrent()).isEqualTo(TenantContext.DEFAULT_BARBERSHOP_ID);
    }
}
