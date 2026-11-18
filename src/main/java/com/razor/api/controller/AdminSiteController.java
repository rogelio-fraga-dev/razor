package com.razor.api.controller;

import com.razor.api.dto.PublicSiteResponse;
import com.razor.api.dto.SitePayload;
import com.razor.api.service.PublicSiteService;
import com.razor.api.service.SiteService;
import com.razor.api.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Editor do site white-label no painel (V33). Papel exigido: OWNER — o fecho de
 * {@code /api/admin/**} no SecurityConfig ja cobre, e a cara publica da barbearia nao e decisao
 * de operacao.
 */
@RestController
@RequestMapping("/api/admin/site")
@RequiredArgsConstructor
public class AdminSiteController {

    private final SiteService siteService;
    private final PublicSiteService publicSiteService;

    @GetMapping
    public SitePayload get() {
        return SitePayload.from(siteService.getOrDefaults());
    }

    @PutMapping
    public SitePayload update(@Valid @RequestBody SitePayload payload) {
        return SitePayload.from(siteService.update(payload));
    }

    /**
     * Previa: o site montado como o visitante veria, mesmo sem publicar.
     *
     * <p>E o que permite o editor mostrar o resultado antes de o endereco existir — sem isso o
     * dono publicaria as cegas para descobrir como ficou.
     */
    @GetMapping("/preview")
    public PublicSiteResponse preview(@RequestParam(name = "completo", defaultValue = "false") boolean completo) {
        // ?completo=true: dados sem os interruptores do site salvo — a previa ao vivo aplica os
        // do rascunho por cima (ver PublicSiteService#preview).
        return publicSiteService.preview(TenantContext.getCurrent(), completo);
    }
}
