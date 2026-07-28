-- V30: Rede de seguranca contra saldo de estoque negativo.
--
-- PROBLEMA: `StockService.requireAvailable` le o saldo e decide se pode vender; a gravacao vem
-- depois. Duas vendas simultaneas do mesmo produto (comanda fechando no caixa 1 e venda de
-- balcao no caixa 2) podem ambas passar na conferencia antes de qualquer commit — o isolamento
-- READ COMMITTED do Postgres nao impede isso. Resultado: saldo negativo, que nao existe no
-- mundo fisico.
--
-- Duas defesas, deliberadamente redundantes:
--
-- 1. `version` para lock otimista do JPA: a segunda transacao a gravar leva
--    OptimisticLockException em vez de sobrescrever a primeira.
--
-- 2. CHECK no banco: mesmo que alguem escreva por fora do JPA (script, correcao manual), o
--    Postgres recusa saldo negativo. Regra de dinheiro/estoque nao pode viver so na aplicacao.

ALTER TABLE products
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Produtos sem controle de estoque tem saldo 0 e nunca sao decrementados, entao o CHECK
-- vale para todos sem exigir tratamento especial.
ALTER TABLE products
    ADD CONSTRAINT chk_product_stock_not_negative CHECK (stock_quantity >= 0);
