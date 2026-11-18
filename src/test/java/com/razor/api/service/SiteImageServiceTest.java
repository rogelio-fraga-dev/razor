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
import org.mockito.InjectMocks;
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
    @InjectMocks private SiteImageService service;

    private static final UUID SHOP = UUID.randomUUID();
    private static final UUID OTHER_SHOP = UUID.randomUUID();
    private static final String JPEG_B64 = "data:image/png;base64,"
            + Base64.getEncoder().encodeToString(SiteImagePolicyTestBytes.JPEG);

    /** Bytes minimos com assinatura JPEG (o tipo declarado acima e mentira de proposito). */
    static final class SiteImagePolicyTestBytes {
        static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
    }

    @BeforeEach
    void tenant() {
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
}
