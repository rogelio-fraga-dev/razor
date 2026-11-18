-- V35: Varias formas de pagamento por atendimento (split) e taxa por forma de pagamento.
--
-- 1) SPLIT. Ate aqui o fechamento da comanda lancava UM movimento com o total e uma forma de
--    pagamento so. Cliente que paga R$ 50 no dinheiro e o resto no cartao nao tinha como ser
--    registrado — a recepcao escolhia uma forma e a conferencia da gaveta saia errada (ou
--    sobrava dinheiro "de credito", ou faltava). Agora cada parte vira o SEU movimento de caixa,
--    com a sua forma. Como a gaveta esperada ja soma so as VENDAs em DINHEIRO, a conferencia
--    continua certa sem mudar uma linha da regra: so a parte em dinheiro entra na gaveta.
--
--    `comanda_id` amarra as partes a comanda. `comandas.cash_movement_id` continua existindo e
--    aponta para a parte PRINCIPAL (a maior) — e o movimento que os produtos da comanda
--    referenciam em `product_sales`, como sempre foi.
--
-- 2) TAXA. Maquininha cobra; PIX as vezes tambem. `payment_method_fees` guarda o percentual por
--    forma de pagamento de cada barbearia, e cada VENDA grava em `fee_amount` a taxa calculada
--    NO MOMENTO da venda. Snapshot de proposito: mudar a taxa hoje nao pode reescrever o
--    liquido do mes passado — relatorio de dinheiro nao muda retroativamente (mesma regra da
--    V21 para preco).
--
-- 3) OUTRO. Nova forma de pagamento (vale, transferencia, permuta). VARCHAR(12) ja comporta; nao
--    ha CHECK de dominio nas colunas `payment_method`, entao nada a alterar alem do comentario.

ALTER TABLE cash_movements
    ADD COLUMN comanda_id UUID REFERENCES comandas(id) ON DELETE SET NULL,
    ADD COLUMN fee_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

ALTER TABLE cash_movements
    ADD CONSTRAINT ck_cash_movement_fee CHECK (fee_amount >= 0 AND fee_amount <= amount);

CREATE INDEX idx_cash_movements_comanda ON cash_movements(comanda_id) WHERE comanda_id IS NOT NULL;
-- Relatorio de recebimentos por forma de pagamento le VENDAs de um intervalo do tenant.
CREATE INDEX idx_cash_movements_shop_created ON cash_movements(barbershop_id, created_at);

-- Comandas fechadas antes da V35 tem um movimento so: amarra para o historico listar a parte.
UPDATE cash_movements m
   SET comanda_id = c.id
  FROM comandas c
 WHERE c.cash_movement_id = m.id
   AND m.comanda_id IS NULL;

CREATE TABLE payment_method_fees (
    barbershop_id  UUID NOT NULL REFERENCES barbershops(id) ON DELETE CASCADE,
    payment_method VARCHAR(12) NOT NULL,
    fee_percent    NUMERIC(5,2) NOT NULL,
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),

    PRIMARY KEY (barbershop_id, payment_method),
    CONSTRAINT ck_payment_fee_percent CHECK (fee_percent >= 0 AND fee_percent < 100),
    -- Dinheiro nao tem taxa: e o unico meio que entra inteiro na gaveta.
    CONSTRAINT ck_payment_fee_method CHECK (payment_method IN ('PIX', 'DEBITO', 'CREDITO', 'OUTRO'))
);

COMMENT ON COLUMN cash_movements.payment_method IS
    'DINHEIRO | PIX | DEBITO | CREDITO | OUTRO (so em VENDA). So DINHEIRO afeta a gaveta.';
COMMENT ON COLUMN cash_movements.comanda_id IS
    'Comanda de origem da VENDA. Uma comanda paga em split tem um movimento por parte.';
COMMENT ON COLUMN cash_movements.fee_amount IS
    'Taxa do meio de pagamento calculada na hora da venda (snapshot). Liquido = amount - fee_amount.';
