package com.razor.api.service;

import com.razor.api.model.BarbershopAdmin;
import com.razor.api.model.BarbershopSettings;
import com.razor.api.repository.BarbershopSettingsRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resumo de boas-vindas enviado ao dono pelo WhatsApp na 1a vez que ele conclui a configuracao
 * do bot. Explica, em uma mensagem, o que o assistente faz sozinho e o que o dono comanda no
 * painel — o "manual de 30 segundos" logo apos configurar.
 *
 * <p>Idempotente: dispara <b>uma unica vez</b> por tenant (marcado em
 * {@code welcome_brief_sent_at}). Sai pelo numero da barbearia (bot) para o numero pessoal do
 * dono (admin) — a "regra dos dois numeros". Se o tenant ainda nao tem admin cadastrado, nao
 * envia nem marca: tenta de novo na proxima vez que salvar (quando ja houver para quem enviar).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WelcomeBriefService {

    private final TenantAdminService tenantAdminService;
    private final BarbershopSettingsService barbershopSettingsService;
    private final BarbershopSettingsRepository settingsRepository;
    private final WhatsAppService whatsAppService;

    /**
     * Envia o resumo se ainda nao foi enviado para este tenant. Deve ser chamado apos salvar as
     * configuracoes do bot, com o {@code TenantContext} do tenant atual ativo.
     */
    public void maybeSendAfterConfig(BarbershopSettings saved) {
        if (saved == null || saved.getWelcomeBriefSentAt() != null) {
            return; // ja enviado (ou nada salvo)
        }

        List<BarbershopAdmin> admins = tenantAdminService.listAdmins();
        if (admins.isEmpty()) {
            return; // sem numero do dono ainda — tenta na proxima configuracao
        }

        String barbershopName = barbershopSettingsService.resolve().displayName();
        for (BarbershopAdmin admin : admins) {
            whatsAppService.sendTextMessage(admin.getPhone(), buildBrief(barbershopName, admin.getName()));
        }

        saved.setWelcomeBriefSentAt(LocalDateTime.now());
        settingsRepository.save(saved);
        log.info("[BOT] Resumo de boas-vindas enviado para {} admin(s) do tenant {}",
                admins.size(), saved.getBarbershopId());
    }

    /** Mensagem humanizada, formatada para WhatsApp (*negrito*), com o nome do dono quando houver. */
    private String buildBrief(String barbershopName, String adminName) {
        String hi = (adminName == null || adminName.isBlank())
                ? "👋 *Tudo pronto!*"
                : "👋 *Tudo pronto, " + firstName(adminName) + "!*";

        return hi + "\n\n"
                + "Seu assistente da *" + barbershopName + "* já está de pé. "
                + "A partir de agora ele atende seus clientes no WhatsApp sozinho:\n\n"
                + "💈 *Agenda* — marca, remarca e cancela horários conversando naturalmente\n"
                + "🎙️ *Áudio* — entende mensagem de voz do cliente\n"
                + "⏰ *Lembrete* — avisa o cliente 1h antes do corte\n"
                + "⭐ *Avaliação* — pede a nota depois do atendimento\n"
                + "🌴 *Folgas* — nunca marca em cima de folga, almoço ou feriado\n\n"
                + "*No painel você comanda tudo:* agenda do dia, base de clientes, serviços e "
                + "preços, barbeiros e comissões, planos do clube e os textos que o bot fala.\n\n"
                + "📱 *Dica dos dois números:* este número aqui é o *seu* (admin) — dá comandos e "
                + "recebe avisos. Quem atende os clientes é o número da *barbearia*, ligado pelo "
                + "QR Code no painel.\n\n"
                + "É só me chamar por aqui. Manda *menu* a qualquer momento pra ver o que dá pra fazer. 🚀";
    }

    private String firstName(String name) {
        String trimmed = name.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }
}
