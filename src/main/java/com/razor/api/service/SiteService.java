package com.razor.api.service;

import com.razor.api.dto.SitePayload;
import com.razor.api.model.BarbershopSite;
import com.razor.api.repository.BarbershopSiteRepository;
import com.razor.api.tenant.TenantContext;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Edicao do site white-label pelo dono (V33). O visitante e servido pelo PublicSiteService. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteService {

    private final BarbershopSiteRepository repository;

    /** O site do tenant, ou um rascunho com os padroes do sistema (sem persistir). */
    public BarbershopSite getOrDefaults() {
        UUID shop = TenantContext.getCurrent();
        return repository.findById(shop).orElseGet(() -> BarbershopSite.defaultsFor(shop));
    }

    @Transactional
    public BarbershopSite update(SitePayload p) {
        BarbershopSite s = getOrDefaults();

        if (p.templateId() != null) s.setTemplateId(p.templateId());
        if (p.themeMode() != null) s.setThemeMode(p.themeMode());
        if (p.accentColor() != null && !p.accentColor().isBlank()) s.setAccentColor(p.accentColor().trim());

        s.setHeadline(trim(p.headline()));
        s.setSubheadline(trim(p.subheadline()));
        s.setAboutText(trim(p.aboutText()));
        s.setLogoUrl(trim(p.logoUrl()));
        s.setHeroImageUrl(trim(p.heroImageUrl()));
        s.setGalleryUrls(trim(p.galleryUrls()));
        s.setWhatsappNumber(trim(p.whatsappNumber()));
        s.setInstagramUrl(trim(p.instagramUrl()));
        s.setMapsUrl(trim(p.mapsUrl()));

        if (p.showTeam() != null) s.setShowTeam(p.showTeam());
        if (p.showPlans() != null) s.setShowPlans(p.showPlans());
        if (p.showPrices() != null) s.setShowPrices(p.showPrices());

        // Publicar carimba a data uma vez; despublicar nao apaga o carimbo — saber QUANDO o site
        // foi ao ar pela primeira vez e util, e regravar a cada salvamento perderia isso.
        if (Boolean.TRUE.equals(p.published())) {
            if (!s.isPublished()) {
                s.setPublished(true);
                if (s.getPublishedAt() == null) s.setPublishedAt(LocalDateTime.now());
                log.info("[SITE] Site publicado para o tenant {}", TenantContext.getCurrent());
            }
        } else if (Boolean.FALSE.equals(p.published())) {
            s.setPublished(false);
        }

        return repository.save(s);
    }

    private String trim(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
