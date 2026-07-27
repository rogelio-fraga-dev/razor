-- V20: Catalogo de produtos da barbearia (pomada, bebida, cera...).
--
-- Pre-requisito de duas coisas que estavam paradas no roadmap: a venda de balcao (que entra
-- no caixa V18 e gera comissao de produto) e a comanda por atendimento, que precisa somar
-- servicos + produtos. Espelha a estrutura de `services`, sem duracao — produto nao ocupa
-- cadeira.
--
-- Estoque fica de fora de proposito: o roadmap trata reposicao/saida como passo posterior, e
-- vender sem controlar estoque e o comportamento que a barbearia ja tem hoje.

CREATE TABLE products (
    id             UUID PRIMARY KEY,
    barbershop_id  UUID NOT NULL REFERENCES barbershops(id),
    name           VARCHAR(255) NOT NULL,
    price          NUMERIC(10, 2) NOT NULL,
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,

    -- Produto de graca nao existe; preco negativo seria estorno disfarcado de venda.
    CONSTRAINT chk_product_price_positive CHECK (price > 0)
);

-- Toda listagem do catalogo e por tenant (isolamento multi-tenant).
CREATE INDEX idx_products_barbershop ON products (barbershop_id);

-- Dois produtos com o mesmo nome na mesma barbearia so geram confusao na hora de vender.
CREATE UNIQUE INDEX uq_products_barbershop_name ON products (barbershop_id, LOWER(name));
