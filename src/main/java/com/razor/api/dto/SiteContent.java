package com.razor.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Conteudo editavel por secao do site (V45) — espelha {@code SiteContent} de
 * {@code web/src/api/types.ts}.
 *
 * <p>Campo ausente/nulo = padrao do template (definido no frontend, em {@code content.ts}). Por
 * isso os nulos nem saem no JSON: o site so recebe o que o dono de fato escreveu.
 *
 * <p>So nasce pelo {@code SiteContentPolicy}, que garante chaves conhecidas, tamanhos e texto
 * sem caractere de controle — e o que sai daqui vai para a pagina publica.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SiteContent(Map<String, Section> sections, String bookLabel, String motion) {

    /** {@code visible} so e gravado quando falso: ausente ja significa "aparece". */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Section(Boolean visible, String eyebrow, String title, String text) {
    }
}
