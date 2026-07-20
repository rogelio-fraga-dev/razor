package com.razor.api.tenant;

import java.util.UUID;

/**
 * Portador do tenant (barbearia) ativo no fluxo atual, por thread.
 *
 * <p>Fase 1 — fundacao multi-tenant. Enquanto o resolvedor de tenant (por instancia
 * da Evolution / usuario do painel) nao esta ligado em todos os pontos de entrada,
 * {@link #getCurrent()} recai no {@link #DEFAULT_BARBERSHOP_ID} — o tenant semeado na
 * migracao V4, que representa a barbearia unica herdada do single-tenant.
 *
 * <p>Isolamento de tenant deve ser aplicado na camada de acesso a dados (nunca confiar
 * no cliente). Este contexto e a fonte do {@code barbershop_id} para essa camada.
 */
public final class TenantContext {

    /** Tenant padrao semeado pela V4 (bridge single -> multi-tenant). */
    public static final UUID DEFAULT_BARBERSHOP_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID barbershopId) {
        CURRENT.set(barbershopId);
    }

    /** Tenant ativo, ou o {@link #DEFAULT_BARBERSHOP_ID} se nenhum foi definido. */
    public static UUID getCurrent() {
        UUID id = CURRENT.get();
        return id != null ? id : DEFAULT_BARBERSHOP_ID;
    }

    /** True se um tenant foi explicitamente definido nesta thread. */
    public static boolean isSet() {
        return CURRENT.get() != null;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
