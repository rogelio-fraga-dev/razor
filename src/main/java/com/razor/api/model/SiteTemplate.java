package com.razor.api.model;

/**
 * Os tres templates de site inclusos em todo plano (V33).
 *
 * <p>O nome descreve o <b>layout</b>, nao a cor — os tres funcionam em claro e escuro. Foi por
 * isso que o antigo "Dark Premium" virou "Estudio": um nome de template que promete uma cor
 * confunde quem so quer escolher a estrutura da pagina.
 */
public enum SiteTemplate {
    // Os textos de vitrine (proposta, "ideal para", estrutura) vivem em web/src/site/catalog.ts,
    // que e o que o dono le no editor. Aqui fica so o rotulo curto, para logs e relatorios.
    /** Preco e agendamento logo de cara: capa, numeros e servicos. */
    CLASSICO("Clássico", "Preço e agendamento logo de cara."),
    /** Marca antes de preco: capa centralizada, texto grande, equipe em destaque. */
    ESTUDIO("Estúdio", "Marca antes de preço: texto grande, muito respiro."),
    /** O trabalho e o argumento: a galeria em mosaico abre a pagina. */
    VITRINE("Vitrine", "A galeria abre a página: o trabalho é o argumento.");

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
