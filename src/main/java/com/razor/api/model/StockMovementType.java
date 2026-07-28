package com.razor.api.model;

/**
 * Tipo de movimento de estoque (V24).
 *
 * <ul>
 *   <li>{@link #ENTRADA} — reposicao: chegou mercadoria.</li>
 *   <li>{@link #SAIDA} — venda (baixa automatica) ou perda/quebra.</li>
 *   <li>{@link #AJUSTE} — correcao apos contagem fisica; {@code quantity} guarda a diferenca
 *       aplicada, e o sentido se le comparando com o saldo resultante.</li>
 * </ul>
 */
public enum StockMovementType {
    ENTRADA,
    SAIDA,
    AJUSTE
}
