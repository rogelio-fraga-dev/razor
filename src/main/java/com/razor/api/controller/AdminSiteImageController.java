package com.razor.api.controller;

import com.razor.api.service.SiteImageService;
import com.razor.api.service.SiteImageService.SiteImageView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Fotos do site (V45) no painel. Papel: OWNER, pelo mesmo fecho de {@code /api/admin/**} do
 * SecurityConfig que cobre o resto do editor do site.
 *
 * <p>Upload em JSON com a imagem em base64 (data URI), e nao multipart: o cliente HTTP do painel
 * ja manda JSON com o token CSRF em toda escrita, e a foto chega pequena — o navegador
 * redimensiona antes (lado maior 1600px). Um caminho de upload a parte seria mais um lugar para
 * esquecer o CSRF.
 */
@RestController
@RequestMapping("/api/admin/site/images")
@RequiredArgsConstructor
public class AdminSiteImageController {

    private final SiteImageService imageService;

    /** {@code data}: data URI ou base64 puro. O tipo declarado e ignorado — vale o dos bytes. */
    public record UploadRequest(@NotBlank(message = "Nenhuma foto recebida.") String data) {
    }

    @GetMapping
    public List<SiteImageView> list() {
        return imageService.list();
    }

    @PostMapping
    public ResponseEntity<SiteImageView> upload(@Valid @RequestBody UploadRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(imageService.upload(req.data()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        imageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
