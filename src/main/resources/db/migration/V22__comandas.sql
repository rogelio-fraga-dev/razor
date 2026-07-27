-- V22: Comanda por atendimento — servicos + produtos na mesma conta, fechada de uma vez.
--
-- Fecha o ciclo do balcao: hoje o produto avulso ja entra no caixa (V21), mas nao havia como
-- juntar "corte + barba + pomada" numa conta so e cobrar de uma vez.
--
-- REGRA CENTRAL — dinheiro entra UMA vez. Ao fechar, a comanda cria UM movimento de caixa com
-- o total. Os itens de PRODUTO viram linhas em `product_sales` (para historico e comissao
-- continuarem com uma fonte de verdade so), mas apontando para o MESMO movimento de caixa da
-- comanda — nunca criam movimento proprio, senao o valor entraria duas vezes na gaveta.
--
-- Preco e descricao sao COPIADOS do catalogo no momento em que o item entra na comanda, pelo
-- mesmo motivo da V21: reajuste de preco nao pode reescrever conta ja aberta.

CREATE TABLE comandas (
    id               UUID PRIMARY KEY,
    barbershop_id    UUID NOT NULL REFERENCES barbershops(id),

    customer_name    VARCHAR(255),
    customer_phone   VARCHAR(20),
    -- Barbeiro do atendimento; e ele quem recebe a comissao dos produtos da comanda.
    professional_id  UUID REFERENCES professionals(id),

    status           VARCHAR(10) NOT NULL,   -- ABERTA | FECHADA | CANCELADA

    opened_at        TIMESTAMP NOT NULL,
    closed_at        TIMESTAMP,
    comanda_date     DATE NOT NULL,          -- relatorio por dia sem depender de fuso

    -- Preenchidos so no fechamento.
    total            NUMERIC(12,2),
    payment_method   VARCHAR(12),
    cash_movement_id UUID REFERENCES cash_movements(id) ON DELETE SET NULL,

    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,

    CONSTRAINT chk_comanda_total CHECK (total IS NULL OR total > 0),
    -- Comanda fechada tem que ter total e forma de pagamento; aberta nao pode ter.
    CONSTRAINT chk_comanda_closed_fields CHECK (
        (status = 'FECHADA' AND total IS NOT NULL AND payment_method IS NOT NULL AND closed_at IS NOT NULL)
        OR (status <> 'FECHADA' AND total IS NULL AND payment_method IS NULL)
    )
);

CREATE INDEX idx_comandas_shop_date ON comandas (barbershop_id, comanda_date);
-- A tela de comandas abertas e a consulta mais frequente do dia.
CREATE INDEX idx_comandas_open ON comandas (barbershop_id) WHERE status = 'ABERTA';

CREATE TABLE comanda_items (
    id            UUID PRIMARY KEY,
    comanda_id    UUID NOT NULL REFERENCES comandas(id) ON DELETE CASCADE,
    barbershop_id UUID NOT NULL REFERENCES barbershops(id),

    kind          VARCHAR(10) NOT NULL,   -- SERVICO | PRODUTO
    -- Aponta para services.id ou products.id. Sem FK de proposito: o item guarda a copia do
    -- preco e da descricao, entao a conta sobrevive se o item sair do catalogo.
    reference_id  UUID,

    description   VARCHAR(255) NOT NULL,  -- copia: a descricao no momento em que entrou
    unit_price    NUMERIC(10,2) NOT NULL, -- copia: o preco no momento em que entrou
    quantity      INTEGER NOT NULL,
    total         NUMERIC(12,2) NOT NULL,

    created_at    TIMESTAMP,

    CONSTRAINT chk_comanda_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_comanda_item_unit_price CHECK (unit_price > 0),
    CONSTRAINT chk_comanda_item_total CHECK (total > 0)
);

CREATE INDEX idx_comanda_items_comanda ON comanda_items (comanda_id);

-- A venda de produto passa a saber de que comanda veio (NULL = venda avulsa de balcao, V21).
ALTER TABLE product_sales
    ADD COLUMN comanda_id UUID REFERENCES comandas(id) ON DELETE SET NULL;
