package com.razor.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
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

    /**
     * {@code visible} so e gravado quando falso: ausente ja significa "aparece". {@code steps} so
     * existe em "Como agendar" (os 3 passos); a posicao na lista e o numero do passo.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Section(Boolean visible, String eyebrow, String title, String text, List<Step> steps) {

        /** Secao sem passos — todas, menos "Como agendar". */
        public Section(Boolean visible, String eyebrow, String title, String text) {
            this(visible, eyebrow, title, text, null);
        }
    }

    /** Um passo de "Como agendar". Campo nulo = texto padrao daquele passo. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Step(String title, String text) {
    }
}
