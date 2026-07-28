package com.razor.api.tenant;

import java.util.UUID;

/**
 * Portador do tenant (barbearia) ativo no fluxo atual, por thread.
 *
 * <p><b>Fail-closed.</b> {@link #getCurrent()} lanca quando ninguem definiu o tenant. Ate a
 * V30 ele recaia no {@link #DEFAULT_BARBERSHOP_ID}, e esse silencio era o problema: um caminho
 * que esquecesse de resolver o tenant nao quebrava — passava a ler e <b>gravar</b> na barbearia
 * padrao. Com um cliente so isso nunca apareceu, porque a barbearia padrao era a unica que
 * existia; com dois, o sintoma seria dado de um aparecendo no painel do outro, sem erro nenhum
 * no log para investigar. Estourar aqui troca um vazamento silencioso por uma falha barulhenta.
 *
 * <p>Todo ponto de entrada real ja define o tenant: {@code JwtAuthenticationFilter} (painel),
 * {@code WebhookRouter} (WhatsApp), {@code RazorScheduler} (laco por barbearia ativa) e
 * {@code BarbershopProvisioningService} (onboarding). Se {@code getCurrent()} lancar, o bug esta
 * em um caminho novo que nao passou por nenhum deles — nao neste arquivo.
 *
 * <p>Isolamento de tenant deve ser aplicado na camada de acesso a dados (nunca confiar
 * no cliente). Este contexto e a fonte do {@code barbershop_id} para essa camada.
 */
public final class TenantContext {

    /**
     * Tenant semeado pela V4 (bridge single -> multi-tenant). Continua existindo como o id da
     * barbearia herdada, mas <b>nao e mais fallback</b>: quem precisa dele agora o define.
     */
    public static final UUID DEFAULT_BARBERSHOP_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID barbershopId) {
        CURRENT.set(barbershopId);
    }

    /**
     * Tenant ativo nesta thread.
     *
     * @throws IllegalStateException se nenhum tenant foi definido — ver a nota de fail-closed
     *         na documentacao da classe.
     */
    public static UUID getCurrent() {
        UUID id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException(
                    "Nenhuma barbearia no contexto desta thread. Toda operacao escopada por tenant "
                    + "precisa vir de um ponto de entrada que chame TenantContext.set(...): painel "
                    + "(JWT), webhook do WhatsApp, scheduler ou onboarding.");
        }
        return id;
    }

    /** True se um tenant foi explicitamente definido nesta thread. */
    public static boolean isSet() {
        return CURRENT.get() != null;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
