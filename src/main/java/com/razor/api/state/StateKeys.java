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

    // ---------- conversa do CLIENTE ----------

    /** Passo atual da conversa (ex.: ESPERANDO_PAGAMENTO). */
    public static String convState(String phone) {
        return "conv:state:" + TenantContext.getCurrent() + ":" + phone;
    }

    /** Opcoes numeradas de cancelamento aguardando a escolha do cliente. */
    public static String cancelOptions(String phone) {
        return "conv:cancel:" + TenantContext.getCurrent() + ":" + phone;
    }

    /** Vaga da lista de espera oferecida e aguardando resposta. */
    public static String waitlistOffer(String phone) {
        return "conv:waitlist:" + TenantContext.getCurrent() + ":" + phone;
    }

    /** Agendamento aguardando o cliente escolher o profissional. */
    public static String pendingBooking(String phone) {
        return "conv:booking:" + TenantContext.getCurrent() + ":" + phone;
    }

    // ---------- conversa do ADMIN ----------

    public static String adminState(String phone) {
        return "admin:state:" + TenantContext.getCurrent() + ":" + phone;
    }

    public static String adminResumeOptions(String phone) {
        return "admin:resume:" + TenantContext.getCurrent() + ":" + phone;
    }

    public static String adminDeleteOptions(String phone) {
        return "admin:delete:" + TenantContext.getCurrent() + ":" + phone;
    }

    public static String adminScheduledMsg(String phone) {
        return "admin:sched:" + TenantContext.getCurrent() + ":" + phone;
    }
}
