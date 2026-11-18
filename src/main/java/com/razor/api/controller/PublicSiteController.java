package com.razor.api.controller;

import com.razor.api.dto.PublicSiteResponse;
import com.razor.api.service.PublicSiteService;
import com.razor.api.service.SiteImageService;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Site white-label da barbearia — <b>publico</b>, sem autenticacao.
 *
 * <p>Fica fora de {@code /api/admin/**} de proposito: aquele prefixo inteiro exige token e papel
 * (SecurityConfig), e um site que so abre com login nao e um site. O isolamento aqui vem do slug
 * da URL, e o que sai e limitado pelo {@link PublicSiteResponse} — nenhum dado de cliente, de
 * faturamento ou de comissao passa por aqui.
 *
 * <p>Hoje o endereco e {@code /api/site/{slug}}. Quando a infra de subdominio existir
 * (PLATFORM.md §6), o {@code Host} header resolve o slug e o caminho fica igual.
 */
@RestController
@RequestMapping("/api/site")
@RequiredArgsConstructor
public class PublicSiteController {

    private final PublicSiteService publicSiteService;
    private final SiteImageService imageService;

    /** O site publicado da barbearia. 404 quando o slug nao existe ou o site nao foi publicado. */
    @GetMapping("/{slug}")
    public PublicSiteResponse bySlug(@PathVariable String slug) {
        return publicSiteService.bySlug(slug);
    }

    /**
     * Foto do site enviada pelo dono (V45). Publica por UUID: o id e aleatorio e so aparece na
     * pagina de quem usa a foto.
     *
     * <ul>
     *   <li>Cache de um ano, {@code immutable}: a foto nunca muda sob o mesmo id (trocar = subir
     *       outra), entao o navegador e qualquer CDN no meio podem guardar para sempre.</li>
     *   <li>{@code nosniff} + content-type da lista fechada (o detectado no upload): o navegador
     *       nunca "adivinha" que aquilo e HTML e executa. Tipo fora da lista nao sai — 404.</li>
     *   <li>Id malformado ou inexistente: 404, igual — nao ha o que distinguir para um estranho.</li>
     * </ul>
     */
    @GetMapping("/img/{id}")
    public ResponseEntity<byte[]> image(@PathVariable String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        return imageService.publicImage(uuid)
                .map(img -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(img.getContentType()))
                        .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                        .header("X-Content-Type-Options", "nosniff")
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                        .body(img.getBytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
