package com.razor.api.model;

/**
 * Os tres templates de site inclusos em todo plano (V33).
 *
 * <p>O nome descreve o <b>layout</b>, nao a cor — os tres funcionam em claro e escuro. Foi por
 * isso que o antigo "Dark Premium" virou "Estudio": um nome de template que promete uma cor
 * confunde quem so quer escolher a estrutura da pagina.
 */
public enum SiteTemplate {
    /** Elegante e direto: servicos, equipe, avaliacoes e o botao de agendar acima da dobra. */
    CLASSICO("Clássico", "Elegante e direto: serviços, equipe e o botão de agendar."),
    /** Foco em marca e atmosfera — tipografia grande, muito respiro, poucas imagens. */
    ESTUDIO("Estúdio", "Sofisticado, com foco em marca e atmosfera."),
    /** Foco nas fotos dos cortes: galeria grande, prova social, Instagram. */
    VITRINE("Vitrine", "Foco em fotos dos cortes: galeria grande e Instagram.");

    private final String label;
    private final String description;

    SiteTemplate(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
