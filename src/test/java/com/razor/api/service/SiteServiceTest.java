package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.dto.SitePayload;
import com.razor.api.model.BarbershopSite;
import com.razor.api.repository.BarbershopSiteRepository;
import com.razor.api.tenant.TenantContext;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Editor do site: conteúdo por seção e fotos próprias (V45)")
class SiteServiceTest {

    @Mock private BarbershopSiteRepository repository;
    @Mock private SiteImageService imageService;
    @InjectMocks private SiteService service;

    private static final UUID SHOP = UUID.randomUUID();

    @BeforeEach
    void tenant() {
        TenantContext.set(SHOP);
        when(repository.findById(SHOP)).thenReturn(Optional.empty());
        when(repository.save(any(BarbershopSite.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private static SitePayload payload(String logo, String gallery, Map<String, Object> content) {
        return new SitePayload(null, null, null, "Título", null, null, logo, null, gallery,
                null, null, null, null, null, null, null, null, content);
    }

    @Test
    @DisplayName("salva o conteúdo normalizado e devolve no GET do editor")
    void savesContent() {
        BarbershopSite saved = service.update(payload(null, null, Map.of(
                "sections", Map.of("cta", Map.of("title", "  Vem ", "visible", false)),
                "motion", "subtle")));

        assertThat(saved.getContentJson()).contains("\"title\":\"Vem\"").contains("subtle");
        Map<String, Object> back = SitePayload.from(saved).content();
        assertThat(back).containsEntry("motion", "subtle");
    }

    @Test
    @DisplayName("conteúdo inválido recusa antes de gravar")
    void invalidContentRejected() {
        assertThatThrownBy(() -> service.update(payload(null, null, Map.of("sections", Map.of("x", Map.of())))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("conteúdo ausente mantém o que já estava; objeto vazio volta ao padrão")
    void absentContentKeeps() {
        BarbershopSite existing = BarbershopSite.defaultsFor(SHOP);
        existing.setContentJson("{\"bookLabel\":\"Bora\"}");
        when(repository.findById(SHOP)).thenReturn(Optional.of(existing));

        assertThat(service.update(payload(null, null, null)).getContentJson()).contains("Bora");
        assertThat(service.update(payload(null, null, Map.of())).getContentJson()).isNull();
    }

    @Test
    @DisplayName("aceita foto própria (/api/site/img/<uuid>) e confere que é desta barbearia")
    void ownImageAccepted() {
        UUID logo = UUID.randomUUID();
        UUID g1 = UUID.randomUUID();
        service.update(payload("/api/site/img/" + logo, "/api/site/img/" + g1 + "\nhttps://cdn.exemplo.com/x.jpg", null));

        verify(imageService).requireOwned(SHOP, Set.of(logo, g1));
        verify(imageService).pruneOrphans(SHOP, Set.of(logo, g1));
    }

    @Test
    @DisplayName("foto de outra barbearia: recusa e nada é gravado")
    void foreignImageRejected() {
        UUID foreign = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Uma das fotos não foi encontrada."))
                .when(imageService).requireOwned(eq(SHOP), anySet());

        assertThatThrownBy(() -> service.update(payload("/api/site/img/" + foreign, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("caminho relativo que não é foto nossa continua recusado")
    void otherRelativeRejected() {
        assertThatThrownBy(() -> service.update(payload("/api/admin/site", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(payload(null, "/api/site/img/../../admin", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
