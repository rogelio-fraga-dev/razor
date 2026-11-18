package com.razor.api.dto;

import com.razor.api.model.BarbershopSite;
import com.razor.api.model.SiteTemplate;
import com.razor.api.model.SiteTheme;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Conteudo do site como o painel edita. Mesmo record na ida e na volta: o editor carrega,
 * altera e devolve o objeto inteiro — como o resto das configuracoes do painel.
 *
 * <p>Os tamanhos ficam aqui; o FORMATO das URLs (so https, Instagram e Maps nos dominios certos)
 * e checado no {@code SiteUrlPolicy}, chamado pelo SiteService. Isso importa porque o site e
 * servido na mesma origem do painel: um {@code javascript:} num link do site rodaria com a sessao
 * de quem estiver logado.
 */
public record SitePayload(
        SiteTemplate templateId,
        SiteTheme themeMode,
        /** Unica cor que o dono escolhe. Hex invalido quebraria o CSS do site inteiro. */
        @Pattern(regexp = "^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$", message = "Cor inválida — use o formato #RRGGBB.")
        String accentColor,
        @Size(max = 120) String headline,
        @Size(max = 255) String subheadline,
        @Size(max = 5000, message = "O texto sobre a casa pode ter até 5000 caracteres.") String aboutText,
        @Size(max = 2048, message = "Endereço da logo longo demais.") String logoUrl,
        @Size(max = 2048, message = "Endereço da capa longo demais.") String heroImageUrl,
        /** Uma URL por linha; ate 12 linhas de 2048 — o limite por linha e checado no SiteUrlPolicy. */
        @Size(max = 12 * 2049, message = "Galeria longa demais.") String galleryUrls,
        @Size(max = 20) String whatsappNumber,
        @Size(max = 2048, message = "Endereço do Instagram longo demais.") String instagramUrl,
        @Size(max = 2048, message = "Endereço do mapa longo demais.") String mapsUrl,
        Boolean showTeam,
        Boolean showPlans,
        Boolean showPrices,
        Boolean published,
        /** Pagina publica de agendamento online (V38). */
        Boolean onlineBookingEnabled) {

    public static SitePayload from(BarbershopSite s) {
        return new SitePayload(
                s.getTemplateId(), s.getThemeMode(), s.getAccentColor(),
                s.getHeadline(), s.getSubheadline(), s.getAboutText(),
                s.getLogoUrl(), s.getHeroImageUrl(), s.getGalleryUrls(),
                s.getWhatsappNumber(), s.getInstagramUrl(), s.getMapsUrl(),
                s.isShowTeam(), s.isShowPlans(), s.isShowPrices(), s.isPublished(),
                s.isOnlineBookingEnabled());
    }
}
