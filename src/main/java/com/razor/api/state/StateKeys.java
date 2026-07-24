package com.razor.api.state;

import com.razor.api.tenant.TenantContext;

/**
 * Convencao de chaves do {@link StateStore}. Toda chave carrega o tenant — dois clientes com
 * o mesmo telefone em barbearias diferentes nunca compartilham estado.
 *
 * <p>Formato: {@code <dominio>:<tenant>:<id>}
 */
public final class StateKeys {

    private StateKeys() {
    }

    /** Deduplicacao de mensagem da Evolution (id global, nao depende de tenant). */
    public static String dedup(String messageId) {
        return "dedup:" + messageId;
    }

    /** Cliente com o bot pausado (atendimento humano). */
    public static String pause(String phone) {
        return "pause:" + TenantContext.getCurrent() + ":" + phone;
    }

    /** Prefixo para varrer todos os pausados do tenant atual. */
    public static String pausePrefix() {
        return "pause:" + TenantContext.getCurrent() + ":";
    }

    /** Telefone extraido de uma chave de pausa. */
    public static String phoneFromPauseKey(String key) {
        return key.substring(pausePrefix().length());
    }
}
