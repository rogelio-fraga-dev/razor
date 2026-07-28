package com.razor.api.controller;

import com.razor.api.dto.PublicSiteResponse;
import com.razor.api.service.PublicSiteService;
import lombok.RequiredArgsConstructor;
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

    /** O site publicado da barbearia. 404 quando o slug nao existe ou o site nao foi publicado. */
    @GetMapping("/{slug}")
    public PublicSiteResponse bySlug(@PathVariable String slug) {
        return publicSiteService.bySlug(slug);
    }
}
