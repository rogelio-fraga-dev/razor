-- V24: Estoque dos produtos do balcao — reposicao, baixa automatica na venda e alerta de minimo.
--
-- DUAS DECISOES QUE MOLDAM ESTA MIGRACAO:
--
-- 1. Controle de estoque e OPT-IN por produto (`track_stock` default FALSE). Barbearia que nao
--    conta estoque continua vendendo exatamente como hoje. Ligar o controle para todo mundo de
--    uma vez bloquearia venda de quem nunca cadastrou quantidade — regressao na cara do dono.
--
-- 2. O saldo fica em `products.stock_quantity`, mas TODA mudanca vira linha em `stock_movements`.
--    Sem historico, "meu estoque esta errado" e impossivel de investigar; com ele, da para
--    apontar exatamente qual venda ou reposicao levou ao saldo atual.

ALTER TABLE products
    ADD COLUMN track_stock     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN stock_quantity  INTEGER NOT NULL DEFAULT 0,
    -- Abaixo disto o painel avisa que esta na hora de repor. 0 = sem alerta.
    ADD COLUMN min_stock       INTEGER NOT NULL DEFAULT 0;

ALTER TABLE products
    ADD CONSTRAINT chk_product_min_stock_not_negative CHECK (min_stock >= 0);

CREATE TABLE stock_movements (
    id             UUID PRIMARY KEY,
    barbershop_id  UUID NOT NULL REFERENCES barbershops(id),
    product_id     UUID NOT NULL REFERENCES products(id),

    -- ENTRADA (reposicao) | SAIDA (venda ou perda) | AJUSTE (correcao de contagem)
    type           VARCHAR(10) NOT NULL,
    -- Sempre positivo; o sinal vem do tipo. AJUSTE guarda a diferenca aplicada.
    quantity       INTEGER NOT NULL,
    -- Saldo depois deste movimento: o extrato fecha sem precisar reprocessar a tabela toda.
    resulting_stock INTEGER NOT NULL,

    -- Venda que originou a saida (NULL em reposicao, perda e ajuste).
    product_sale_id UUID REFERENCES product_sales(id) ON DELETE SET NULL,
    note           VARCHAR(255),

    created_at     TIMESTAMP NOT NULL,

    CONSTRAINT chk_stock_movement_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_stock_movements_product ON stock_movements (product_id, created_at DESC);
CREATE INDEX idx_stock_movements_shop ON stock_movements (barbershop_id, created_at DESC);
