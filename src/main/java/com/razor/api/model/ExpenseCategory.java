package com.razor.api.model;

/**
 * Categoria da despesa (V31). Guardada como texto na coluna {@code expenses.category} — validar
 * aqui e nao com um tipo do Postgres permite acrescentar categoria sem migracao.
 *
 * <p>A lista sai do que uma barbearia realmente paga; {@link #OUTROS} existe para o dono nao
 * ficar preso quando a despesa nao encaixa, em vez de forcar uma categoria errada e sujar o DRE.
 */
public enum ExpenseCategory {
    ALUGUEL("Aluguel"),
    ENERGIA("Energia"),
    AGUA("Água"),
    INTERNET("Internet e telefone"),
    PRODUTOS("Produtos e insumos"),
    SALARIOS("Salários e encargos"),
    IMPOSTOS("Impostos e taxas"),
    MARKETING("Marketing"),
    MANUTENCAO("Manutenção e reformas"),
    SOFTWARE("Software e assinaturas"),
    OUTROS("Outros");

    private final String label;

    ExpenseCategory(String label) {
        this.label = label;
    }

    /** Nome exibido no painel e nos relatorios. */
    public String label() {
        return label;
    }
}
