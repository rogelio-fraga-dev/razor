package com.razor.api.service;

import com.razor.api.dto.PublicSiteResponse;
import com.razor.api.model.Barbershop;
import com.razor.api.model.BarbershopSite;
import com.razor.api.model.Professional;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.repository.BarbershopSiteRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.repository.SubscriptionPlanRepository;
import com.razor.api.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monta o site white-label para o visitante — endpoint publico, sem autenticacao.
 *
 * <p><b>Nao usa o {@link TenantContext}.</b> Todas as consultas recebem o {@code barbershopId}
 * resolvido pelo slug da URL, explicitamente. O contexto de tenant existe para requisicao
 * autenticada; aqui nao ha usuario, e deixar o resolvedor implicito escolher a barbearia num
 * endereco publico seria a porta exata do vazamento entre tenants que a V32 fechou no painel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicSiteService {

    private final BarbershopRepository barbershopRepository;
    private final BarbershopSiteRepository siteRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final ProfessionalRepository professionalRepository;
    private final SubscriptionPlanRepository planRepository;
    private final BarbershopSettingsService settingsService;

    /**
     * O site publicado da barbearia do slug.
     *
     * @throws NotFoundException quando o slug nao existe, a barbearia esta inativa ou o site
     *         ainda nao foi publicado. As tres respondem 404 <b>iguais</b>, de proposito: um erro
     *         diferente para "existe mas nao publicou" contaria a estranhos que a barbearia
     *         existe e esta montando o site.
     */
    @Transactional(readOnly = true)
    public PublicSiteResponse bySlug(String slug) {
        Barbershop shop = barbershopRepository.findBySlug(slug)
                .filter(Barbershop::isActive)
                .orElseThrow(() -> new NotFoundException("Site nao encontrado."));

        BarbershopSite site = siteRepository.findById(shop.getId())
                .filter(BarbershopSite::isPublished)
                .orElseThrow(() -> new NotFoundException("Site nao encontrado."));

        return assemble(shop, site);
    }

    /**
     * Previa para o painel: monta o site mesmo sem estar publicado.
     *
     * <p>Separado do {@link #bySlug} porque a regra e outra — aqui quem pede ja se autenticou
     * como dono daquela barbearia, e ver o proprio rascunho e o ponto.
     */
    @Transactional(readOnly = true)
    public PublicSiteResponse preview(UUID barbershopId) {
        Barbershop shop = barbershopRepository.findById(barbershopId)
                .orElseThrow(() -> new NotFoundException("Barbearia nao encontrada."));
        BarbershopSite site = siteRepository.findById(barbershopId)
                .orElseGet(() -> BarbershopSite.defaultsFor(barbershopId));
        return assemble(shop, site);
    }

    private PublicSiteResponse assemble(Barbershop shop, BarbershopSite site) {
        UUID id = shop.getId();
        var cfg = settingsService.resolveFor(id);

        // Preco so sai quando o dono liga: barbearia que trabalha com orcamento nao quer tabela
        // publica, e publicar assim mesmo seria decidir o negocio dela.
        List<PublicSiteResponse.Service> services = serviceRepository.findByBarbershopIdAndActiveTrue(id)
                .stream()
                .map(s -> new PublicSiteResponse.Service(
                        s.getName(),
                        site.isShowPrices() ? s.getPrice() : null,
                        s.getDurationMinutes()))
                .toList();

        // So barbeiro ATIVO e so campos de vitrine (V37). Telefone, comissao, id e servicos que
        // atende nao tem por que sair daqui — o mapeamento campo a campo e a fronteira.
        List<PublicSiteResponse.TeamMember> team = site.isShowTeam()
                ? professionalRepository.findByBarbershopIdAndActiveTrue(id).stream()
                        .filter(Professional::isActive)
                        .map(p -> new PublicSiteResponse.TeamMember(
                                p.getName(),
                                blankToNull(p.getNickname()),
                                blankToNull(p.getPhotoData()),
                                blankToNull(p.getBio()),
                                p.specialtyList(),
                                blankToNull(p.getInstagramUrl())))
                        .toList()
                : List.of();

        List<PublicSiteResponse.Plan> plans = site.isShowPlans()
                ? planRepository.findByBarbershopIdAndActiveTrue(id).stream()
                        .map(p -> new PublicSiteResponse.Plan(p.getName(), p.getPrice(), p.getDescription()))
                        .toList()
                : List.of();

        return new PublicSiteResponse(
                shop.getSlug(),
                cfg.displayName() != null ? cfg.displayName() : shop.getName(),
                site.getTemplateId(),
                site.getThemeMode(),
                site.getAccentColor(),
                site.getHeadline(),
                site.getSubheadline(),
                site.getAboutText(),
                site.getLogoUrl(),
                site.getHeroImageUrl(),
                site.galleryList(),
                site.getWhatsappNumber(),
                site.getInstagramUrl(),
                site.getMapsUrl(),
                cfg.addressText(),
                // Derivada dos dias estruturados (V32): o site nunca pode anunciar um expediente
                // diferente do que a agenda aceita.
                cfg.workingDaysText() + ", das " + cfg.openingTime() + " às " + cfg.closingTime(),
                services,
                team,
                plans);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
