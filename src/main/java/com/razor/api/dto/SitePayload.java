package com.razor.api.dto;

import com.razor.api.model.BarbershopSite;
import com.razor.api.model.SiteTemplate;
import com.razor.api.model.SiteTheme;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Conteudo do site como o painel edita. Mesmo record na ida e na volta: o editor carrega,
 * altera e devolve o objeto inteiro — como o resto das configuracoes do painel.
 */
public record SitePayload(
        SiteTemplate templateId,
        SiteTheme themeMode,
        /** Unica cor que o dono escolhe. Hex invalido quebraria o CSS do site inteiro. */
        @Pattern(regexp = "^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$", message = "Cor inválida — use o formato #RRGGBB.")
        String accentColor,
        @Size(max = 120) String headline,
        @Size(max = 255) String subheadline,
        String aboutText,
        String logoUrl,
        String heroImageUrl,
        String galleryUrls,
        @Size(max = 20) String whatsappNumber,
        String instagramUrl,
        String mapsUrl,
        Boolean showTeam,
        Boolean showPlans,
        Boolean showPrices,
        Boolean published) {

    public static SitePayload from(BarbershopSite s) {
        return new SitePayload(
                s.getTemplateId(), s.getThemeMode(), s.getAccentColor(),
                s.getHeadline(), s.getSubheadline(), s.getAboutText(),
                s.getLogoUrl(), s.getHeroImageUrl(), s.getGalleryUrls(),
                s.getWhatsappNumber(), s.getInstagramUrl(), s.getMapsUrl(),
                s.isShowTeam(), s.isShowPlans(), s.isShowPrices(), s.isPublished());
    }
}
