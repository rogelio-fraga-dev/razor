package com.razor.api.service;

import com.razor.api.model.BarbershopSite;
import com.razor.api.model.SiteImage;
import com.razor.api.repository.BarbershopSiteRepository;
import com.razor.api.repository.SiteImageRepository;
import com.razor.api.service.storage.ImageStorage;
import com.razor.api.service.storage.ImageStorageRegistry;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.SiteImagePolicy;
import com.razor.api.util.SiteUrlPolicy;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fotos do site white-label (V45): o dono sobe pelo painel, o visitante baixa por
 * {@code /api/site/img/{id}}.
 *
 * <p>Toda operacao do painel e escopada no tenant do login ({@link TenantContext}) e filtra por
 * {@code barbershopId} na propria consulta — o id da foto e publico, entao nunca basta sozinho.
 *
 * <p>Os bytes moram onde o {@link ImageStorageRegistry} mandar (V50): no banco (padrao) ou no S3.
 * Cada foto lembra o proprio backend, entao trocar a configuracao nao quebra as antigas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteImageService {

    /**
     * Foto enviada mas nunca salva no site some depois deste prazo (ver {@link #pruneOrphans}).
     * Um dia de folga: o dono pode ter subido a foto numa aba e ainda nao salvou.
     */
    static final long ORPHAN_GRACE_HOURS = 24;

    private final SiteImageRepository repository;
    private final BarbershopSiteRepository siteRepository;
    private final ImageStorageRegistry storages;

    /** A foto como o painel ve: {@code inUse} = esta no site SALVO (logo, capa ou galeria). */
    public record SiteImageView(UUID id, String url, String contentType, int sizeBytes,
                                LocalDateTime createdAt, boolean inUse) {
    }

    /** A foto para a rota publica: tipo (da lista fechada) e bytes, venham de onde vierem. */
    public record PublicImage(String contentType, byte[] bytes) {
    }

    @Transactional
    public SiteImageView upload(String data) {
        UUID shop = TenantContext.getCurrent();
        // Valida antes de contar: arquivo invalido responde pelo motivo certo.
        SiteImagePolicy.Decoded img = SiteImagePolicy.decode(data);
        if (repository.countByBarbershopId(shop) >= SiteImagePolicy.MAX_IMAGES_PER_SHOP) {
            throw new IllegalArgumentException("Você já tem " + SiteImagePolicy.MAX_IMAGES_PER_SHOP
                    + " fotos guardadas. Apague as que não usa mais em “Fotos enviadas”.");
        }
        ImageStorage storage = storages.active();
        SiteImage image = SiteImage.builder()
                .id(UUID.randomUUID())
                .barbershopId(shop)
                .contentType(img.contentType())
                .sizeBytes(img.bytes().length)
                .storageBackend(storage.backend())
                .build();
        // Arquivo antes do registro: se o S3 recusar, nao sobra registro apontando para o nada.
        storage.put(image, img.bytes());
        SiteImage saved = repository.save(image);
        log.info("[SITE] Foto {} enviada ({} bytes, {}) pelo tenant {}", saved.getId(), saved.getSizeBytes(),
                saved.getContentType(), shop);
        return new SiteImageView(saved.getId(), urlOf(saved.getId()), saved.getContentType(), saved.getSizeBytes(),
                saved.getCreatedAt(), false);
    }

    @Transactional(readOnly = true)
    public List<SiteImageView> list() {
        UUID shop = TenantContext.getCurrent();
        Set<UUID> inUse = referencedIds(siteRepository.findById(shop).orElse(null));
        return repository.listInfo(shop).stream()
                .map(i -> new SiteImageView(i.getId(), urlOf(i.getId()), i.getContentType(), i.getSizeBytes(),
                        i.getCreatedAt(), inUse.contains(i.getId())))
                .toList();
    }

    /**
     * Apaga uma foto da barbearia do login.
     *
     * @throws NotFoundException se a foto nao existe <b>ou e de outra barbearia</b> — a mesma
     *         resposta nos dois casos, para nao confirmar que o id existe.
     * @throws ConflictException se a foto esta no site salvo: apagar deixaria um buraco no site
     *         que esta no ar. Primeiro tira do site e salva.
     */
    @Transactional
    public void delete(UUID id) {
        UUID shop = TenantContext.getCurrent();
        if (!repository.existsByIdAndBarbershopId(id, shop)) {
            throw new NotFoundException("Foto não encontrada.");
        }
        if (referencedIds(siteRepository.findById(shop).orElse(null)).contains(id)) {
            throw new ConflictException("Esta foto está no seu site. Tire-a do site e salve antes de apagar.");
        }
        String backend = repository.findBackend(id, shop).orElse(null);
        repository.deleteByIdAndShop(id, shop);
        storages.forBackend(backend).delete(shop, id);
    }

    /** Leitura publica pelo id — sem tenant: a URL e publica por definicao. */
    @Transactional(readOnly = true)
    public Optional<PublicImage> publicImage(UUID id) {
        return repository.findById(id)
                .filter(i -> SiteImagePolicy.isServable(i.getContentType()))
                .flatMap(i -> storages.forBackend(i.getStorageBackend()).get(i)
                        .map(bytes -> new PublicImage(i.getContentType(), bytes)));
    }

    /**
     * Confere que toda foto NOSSA citada no site e desta barbearia. Sem isso o dono poderia
     * apontar para a foto de outra casa (o id aparece na pagina publica dela) — e o dono de
     * verdade, ao apaga-la, quebraria o site alheio.
     */
    @Transactional(readOnly = true)
    public void requireOwned(UUID shop, Set<UUID> ids) {
        for (UUID id : ids) {
            if (!repository.existsByIdAndBarbershopId(id, shop)) {
                throw new IllegalArgumentException("Uma das fotos não foi encontrada. Envie a foto de novo.");
            }
        }
    }

    /**
     * Faxina ao salvar o site: apaga as fotos que nao estao no site salvo e foram enviadas ha
     * mais de {@link #ORPHAN_GRACE_HOURS}h (rascunho abandonado, foto trocada). Sem isso o teto de
     * fotos por barbearia enchia com lixo e o dono teria que apagar uma a uma.
     */
    @Transactional
    public int pruneOrphans(UUID shop, Set<UUID> keep) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(ORPHAN_GRACE_HOURS);
        int removed = 0;
        for (SiteImageRepository.Info i : repository.listInfo(shop)) {
            if (!keep.contains(i.getId()) && i.getCreatedAt() != null && i.getCreatedAt().isBefore(cutoff)) {
                int n = repository.deleteByIdAndShop(i.getId(), shop);
                if (n > 0) storages.forBackend(i.getStorageBackend()).delete(shop, i.getId());
                removed += n;
            }
        }
        if (removed > 0) log.info("[SITE] {} foto(s) sem uso removida(s) do tenant {}", removed, shop);
        return removed;
    }

    /** Ids das fotos nossas citadas no site (logo, capa e galeria). */
    public static Set<UUID> referencedIds(BarbershopSite site) {
        if (site == null) return Set.of();
        return referencedIds(site.getLogoUrl(), site.getHeroImageUrl(), site.galleryList());
    }

    public static Set<UUID> referencedIds(String logoUrl, String heroUrl, List<String> gallery) {
        Set<UUID> ids = new HashSet<>();
        Stream.concat(Stream.of(logoUrl, heroUrl), gallery.stream())
                .map(SiteUrlPolicy::ownImageId)
                .forEach(o -> o.ifPresent(ids::add));
        return ids;
    }

    public static String urlOf(UUID id) {
        return "/api/site/img/" + id;
    }
}
