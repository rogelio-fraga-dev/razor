package com.razor.api.model;

/**
 * Tipos de incidente de saude (V50). Cada tipo diz como avisar e o texto que o dono le no painel.
 *
 * <p>{@code ownerWhatsApp}: se o dono recebe aviso pelo WhatsApp. Fica desligado quando o
 * proprio problema e o canal (WhatsApp desconectado, envios falhando) — o aviso nao chegaria — e
 * na IA, que ja tem o alerta proprio no atendimento ({@code CustomerConversationService}); dois
 * avisos para a mesma queda seria spam. O painel mostra TODOS, sempre.
 *
 * <p>{@code platform}: problema da plataforma (vale para todas as barbearias), registrado com
 * {@code barbershop_id} nulo e mostrado em todos os paineis.
 */
public enum IncidentType {

    WHATSAPP_DISCONNECTED(false, false,
            "WhatsApp desconectado — reconecte em Configurações → WhatsApp. Enquanto isso o bot não responde nem envia lembretes."),
    AI_FAILURE(false, false,
            "A inteligência artificial está falhando — os clientes recebem a mensagem de contingência."),
    SEND_FAILURE(false, false,
            "Mensagens do WhatsApp não estão saindo (lembretes e respostas podem não chegar)."),
    QUEUE_STUCK(true, false,
            "Lembretes e avisos agendados estão atrasados na fila de envio."),
    SCHEDULER_STALLED(false, true,
            "O motor de lembretes da plataforma está parado — já estamos verificando."),
    EVOLUTION_UNAVAILABLE(false, true,
            "O serviço de WhatsApp da plataforma está fora do ar — já estamos verificando.");

    private final boolean ownerWhatsApp;
    private final boolean platform;
    private final String ownerMessage;

    IncidentType(boolean ownerWhatsApp, boolean platform, String ownerMessage) {
        this.ownerWhatsApp = ownerWhatsApp;
        this.platform = platform;
        this.ownerMessage = ownerMessage;
    }

    public boolean ownerWhatsApp() {
        return ownerWhatsApp;
    }

    public boolean platform() {
        return platform;
    }

    /** Texto para o dono (painel e WhatsApp) — sem detalhe tecnico. */
    public String ownerMessage() {
        return ownerMessage;
    }

    /** O problema e o proprio canal de WhatsApp: nao adianta avisar por ele. */
    public boolean breaksWhatsApp() {
        return this == WHATSAPP_DISCONNECTED || this == SEND_FAILURE || this == EVOLUTION_UNAVAILABLE;
    }
}
