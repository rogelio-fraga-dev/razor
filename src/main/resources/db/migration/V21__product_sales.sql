-- V21: Venda de balcao (produto vendido junto do corte).
--
-- Amarra tres coisas que ja existiam soltas: o catalogo de produtos (V20), o caixa (V18) e a
-- comissao do barbeiro. Toda venda nasce dentro de uma sessao de caixa aberta e cria um
-- movimento de VENDA, para a gaveta esperada do fechamento continuar batendo.
--
-- Nome e preco sao COPIADOS para ca no momento da venda, de proposito: se o dono reajustar o
-- preco depois, a venda de ontem tem que continuar mostrando o que foi realmente cobrado.
-- Relatorio de dinheiro nao pode mudar retroativamente porque a tabela de precos mudou.

CREATE TABLE product_sales (
    id               UUID PRIMARY KEY,
    barbershop_id    UUID NOT NULL REFERENCES barbershops(id),

    product_id       UUID NOT NULL REFERENCES products(id),
    product_name     VARCHAR(255) NOT NULL,     -- copia: o nome no momento da venda
    unit_price       NUMERIC(10,2) NOT NULL,    -- copia: o preco no momento da venda
    quantity         INTEGER NOT NULL,
    total            NUMERIC(12,2) NOT NULL,    -- unit_price * quantity, congelado

    -- Opcional: venda no balcao pode nao ter barbeiro. Sem barbeiro, nao gera comissao.
    professional_id  UUID REFERENCES professionals(id),

    payment_method   VARCHAR(12) NOT NULL,      -- DINHEIRO | PIX | DEBITO | CREDITO
    cash_movement_id UUID REFERENCES cash_movements(id) ON DELETE SET NULL,

    sale_date        DATE NOT NULL,             -- relatorio por dia sem depender de fuso
    sold_at          TIMESTAMP NOT NULL,
    created_at       TIMESTAMP,

    CONSTRAINT chk_product_sale_quantity CHECK (quantity > 0),
    CONSTRAINT chk_product_sale_unit_price CHECK (unit_price > 0),
    CONSTRAINT chk_product_sale_total CHECK (total > 0)
);

-- Relatorios do dia e comissoes filtram por barbearia + data.
CREATE INDEX idx_product_sales_shop_date ON product_sales (barbershop_id, sale_date);
-- Comissao por barbeiro no periodo.
CREATE INDEX idx_product_sales_professional ON product_sales (professional_id, sale_date);
