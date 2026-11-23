package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.BarbershopSite;
import com.razor.api.model.SiteImage;
import com.razor.api.repository.BarbershopSiteRepository;
import com.razor.api.repository.SiteImageRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.SiteImagePolicy;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Fotos do site: upload, isolamento por barbearia e faxina (V45)")
class SiteImageServiceTest {

    @Mock private SiteImageRepository repository;
    @Mock private BarbershopSiteRepository siteRepository;
    private SiteImageService service;
    /** Storage "remoto" falso (faz o papel do S3) para provar que o servico nao depende do banco. */
    private final FakeRemoteStorage remote = new FakeRemoteStorage();

    private static final UUID SHOP = UUID.randomUUID();
    private static final UUID OTHER_SHOP = UUID.randomUUID();
    private static final String JPEG_B64 = "data:image/png;base64,"
            + Base64.getEncoder().encodeToString(SiteImagePolicyTestBytes.JPEG);

    /** Bytes minimos com assinatura JPEG (o tipo declarado acima e mentira de proposito). */
    static final class SiteImagePolicyTestBytes {
        static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
    }

    /** Guarda os bytes num mapa, como um bucket. */
    static final class FakeRemoteStorage implements com.razor.api.service.storage.ImageStorage {
        final java.util.Map<UUID, byte[]> objects = new java.util.HashMap<>();
        final List<UUID> deleted = new java.util.ArrayList<>();

        public String backend() { return "S3"; }

        public void put(SiteImage image, byte[] bytes) {
            objects.put(image.getId(), bytes);
            image.setBytes(null);
        }

        public Optional<byte[]> get(SiteImage image) { return Optional.ofNullable(objects.get(image.getId())); }

        public void delete(UUID shop, UUID id) {
            objects.remove(id);
            deleted.add(id);
        }
    }

    private SiteImageService serviceWith(String type) {
        var props = new com.razor.api.config.RazorProperties();
        props.getStorage().setType(type);
        var registry = new com.razor.api.service.storage.ImageStorageRegistry(
                List.of(new com.razor.api.service.storage.DbImageStorage(), remote), props);
        return new SiteImageService(repository, siteRepository, registry);
    }

    @BeforeEach
    void tenant() {
        service = serviceWith("db");
        TenantContext.set(SHOP);
        when(repository.save(any(SiteImage.class))).thenAnswer(inv -> {
            SiteImage i = inv.getArgument(0);
            i.setCreatedAt(LocalDateTime.now());
            return i;
        });
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private static SiteImageRepository.Info info(UUID id, LocalDateTime createdAt) {
        return new SiteImageRepository.Info() {
            public UUID getId() { return id; }
            public String getContentType() { return "image/jpeg"; }
            public Integer getSizeBytes() { return 10; }
            public LocalDateTime getCreatedAt() { return createdAt; }
            public String getStorageBackend() { return "DB"; }
        };
    }

    @Test
    @DisplayName("upload grava na barbearia do login, com o tipo DETECTADO e id aleatório")
    void uploadStoresInTenant() {
        var view = service.upload(JPEG_B64);

        ArgumentCaptor<SiteImage> cap = ArgumentCaptor.forClass(SiteImage.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getBarbershopId()).isEqualTo(SHOP);
        assertThat(cap.getValue().getContentType()).isEqualTo("image/jpeg");
        assertThat(cap.getValue().getSizeBytes()).isEqualTo(7);
        assertThat(view.url()).isEqualTo("/api/site/img/" + cap.getValue().getId());
        assertThat(view.inUse()).isFalse();
    }

    @Test
    @DisplayName("upload recusa quando a barbearia já tem o teto de fotos")
    void uploadRespectsCap() {
        when(repository.countByBarbershopId(SHOP)).thenReturn((long) SiteImagePolicy.MAX_IMAGES_PER_SHOP);
        assertThatThrownBy(() -> service.upload(JPEG_B64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(SiteImagePolicy.MAX_IMAGES_PER_SHOP));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("upload de arquivo que não é imagem nem chega a gravar")
    void uploadRejectsInvalid() {
        String html = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString("<html>".getBytes());
        assertThatThrownBy(() -> service.upload(html)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("apagar foto de OUTRA barbearia responde 404 e não apaga nada")
    void deleteOtherTenantIsNotFound() {
        UUID foreign = UUID.randomUUID();
        when(repository.existsByIdAndBarbershopId(foreign, SHOP)).thenReturn(false);
        when(repository.existsByIdAndBarbershopId(foreign, OTHER_SHOP)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(foreign)).isInstanceOf(NotFoundException.class);
        verify(repository, never()).deleteByIdAndShop(any(), any());
    }

    @Test
    @DisplayName("apagar foto que está no site salvo responde 409")
    void deleteInUseIsConflict() {
        UUID id = UUID.randomUUID();
        BarbershopSite site = BarbershopSite.defaultsFor(SHOP);
        site.setGalleryUrls("https://cdn.exemplo.com/a.jpg\n/api/site/img/" + id);
        when(repository.existsByIdAndBarbershopId(id, SHOP)).thenReturn(true);
        when(siteRepository.findById(SHOP)).thenReturn(Optional.of(site));

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ConflictException.class);
        verify(repository, never()).deleteByIdAndShop(any(), any());
    }

    @Test
    @DisplayName("apagar foto própria sem uso apaga escopado no tenant")
    void deleteOwnUnused() {
        UUID id = UUID.randomUUID();
        when(repository.existsByIdAndBarbershopId(id, SHOP)).thenReturn(true);
        when(siteRepository.findById(SHOP)).thenReturn(Optional.empty());

        service.delete(id);
        verify(repository).deleteByIdAndShop(id, SHOP);
    }

    @Test
    @DisplayName("listagem marca as fotos que estão no site salvo")
    void listMarksInUse() {
        UUID used = UUID.randomUUID();
        UUID free = UUID.randomUUID();
        BarbershopSite site = BarbershopSite.defaultsFor(SHOP);
        site.setLogoUrl("/api/site/img/" + used);
        when(siteRepository.findById(SHOP)).thenReturn(Optional.of(site));
        when(repository.listInfo(SHOP)).thenReturn(List.of(info(used, LocalDateTime.now()), info(free, LocalDateTime.now())));

        var list = service.list();
        assertThat(list).extracting(SiteImageService.SiteImageView::inUse).containsExactly(true, false);
    }

    @Test
    @DisplayName("site não aceita foto de outra barbearia")
    void requireOwned() {
        UUID mine = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        when(repository.existsByIdAndBarbershopId(mine, SHOP)).thenReturn(true);
        when(repository.existsByIdAndBarbershopId(foreign, SHOP)).thenReturn(false);

        assertThatCode(() -> service.requireOwned(SHOP, Set.of(mine))).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireOwned(SHOP, Set.of(mine, foreign)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("faxina: só apaga foto sem uso e mais velha que a folga, sempre no próprio tenant")
    void pruneOrphans() {
        UUID keep = UUID.randomUUID();
        UUID oldOrphan = UUID.randomUUID();
        UUID freshOrphan = UUID.randomUUID();
        LocalDateTime old = LocalDateTime.now().minusHours(SiteImageService.ORPHAN_GRACE_HOURS + 1);
        when(repository.listInfo(SHOP)).thenReturn(List.of(
                info(keep, old), info(oldOrphan, old), info(freshOrphan, LocalDateTime.now())));
        when(repository.deleteByIdAndShop(eq(oldOrphan), eq(SHOP))).thenReturn(1);

        assertThat(service.pruneOrphans(SHOP, Set.of(keep))).isEqualTo(1);
        verify(repository).deleteByIdAndShop(oldOrphan, SHOP);
        verify(repository, never()).deleteByIdAndShop(eq(keep), any());
        verify(repository, never()).deleteByIdAndShop(eq(freshOrphan), any());
    }

    @Test
    @DisplayName("leitura pública não serve tipo fora da lista")
    void publicImageWhitelist() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(
                SiteImage.builder().id(id).barbershopId(SHOP).contentType("text/html").bytes(new byte[] {1}).sizeBytes(1).build()));
        assertThat(service.publicImage(id)).isEmpty();
    }

    @Test
    @DisplayName("banco (padrão): os bytes vão no próprio registro, marcado como DB")
    void dbStorageKeepsBytesInRow() {
        service.upload(JPEG_B64);
        ArgumentCaptor<SiteImage> cap = ArgumentCaptor.forClass(SiteImage.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getStorageBackend()).isEqualTo("DB");
        assertThat(cap.getValue().getBytes()).containsExactly(SiteImagePolicyTestBytes.JPEG);
    }

    @Test
    @DisplayName("storage remoto (S3): registro sem bytes, leitura pública e remoção pelo backend da foto")
    void remoteStorageRoundTrip() {
        service = serviceWith("s3");
        var view = service.upload(JPEG_B64);

        ArgumentCaptor<SiteImage> cap = ArgumentCaptor.forClass(SiteImage.class);
        verify(repository).save(cap.capture());
        SiteImage row = cap.getValue();
        assertThat(row.getStorageBackend()).isEqualTo("S3");
        assertThat(row.getBytes()).isNull();
        assertThat(remote.objects).containsKey(view.id());
        // A URL pública não muda com o backend.
        assertThat(view.url()).isEqualTo("/api/site/img/" + view.id());

        when(repository.findById(view.id())).thenReturn(Optional.of(row));
        var pub = service.publicImage(view.id()).orElseThrow();
        assertThat(pub.bytes()).containsExactly(SiteImagePolicyTestBytes.JPEG);
        assertThat(pub.contentType()).isEqualTo("image/jpeg");

        when(repository.existsByIdAndBarbershopId(view.id(), SHOP)).thenReturn(true);
        when(repository.findBackend(view.id(), SHOP)).thenReturn(Optional.of("S3"));
        service.delete(view.id());
        verify(repository).deleteByIdAndShop(view.id(), SHOP);
        assertThat(remote.deleted).containsExactly(view.id());
    }

    @Test
    @DisplayName("ligar o S3 não quebra as fotos antigas: foto gravada como DB continua lida do banco")
    void legacyDbImagesStillServedWithS3Active() {
        service = serviceWith("s3");
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(SiteImage.builder().id(id).barbershopId(SHOP)
                .contentType("image/png").bytes(new byte[] {9, 9}).sizeBytes(2).storageBackend("DB").build()));
        assertThat(service.publicImage(id).orElseThrow().bytes()).containsExactly(9, 9);
    }

    @Test
    @DisplayName("arquivo sumiu do storage: 404 (vazio), nunca erro 500")
    void missingRemoteObjectIsEmpty() {
        service = serviceWith("s3");
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(SiteImage.builder().id(id).barbershopId(SHOP)
                .contentType("image/png").sizeBytes(2).storageBackend("S3").build()));
        assertThat(service.publicImage(id)).isEmpty();
    }

    @Test
    @DisplayName("tipo de storage desconhecido falha no boot")
    void unknownStorageTypeFails() {
        assertThatThrownBy(() -> serviceWith("ftp")).isInstanceOf(IllegalStateException.class);
    }
}
