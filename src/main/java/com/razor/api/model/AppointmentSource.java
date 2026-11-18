package com.razor.api.model;

/**
 * De onde veio um agendamento (V38). Alimenta a metrica de ROI da IA: quanto da agenda o bot
 * trouxe sozinho, contra o que entrou pelo painel ou pela pagina publica do site.
 */
public enum AppointmentSource {
    /** Conversa no WhatsApp (IA ou fluxo guiado) e o lote de agenda enviado pelo admin. */
    BOT,
    /** Criado por alguem da barbearia no painel web. */
    PAINEL,
    /** Pagina publica de agendamento online ({@code /b/{slug}/agendar}). */
    SITE
}
