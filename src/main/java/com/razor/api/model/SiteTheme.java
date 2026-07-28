package com.razor.api.model;

/**
 * Tema do site white-label (V33). Vale para qualquer template — todos vem com as duas paletas
 * prontas e testadas em contraste.
 */
public enum SiteTheme {
    LIGHT("Claro"),
    DARK("Escuro"),
    /** Segue o aparelho do visitante. Padrao: e a escolha certa para quem nao tem preferencia. */
    AUTO("Segue o aparelho");

    private final String label;

    SiteTheme(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
