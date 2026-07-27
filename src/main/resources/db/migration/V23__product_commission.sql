-- V23: Percentual de comissao proprio para produto.
--
-- Ate aqui a comissao de produto usava o mesmo percentual do servico (V21). Muitas barbearias
-- pagam menos em produto — 40% no corte e 10% na pomada, por exemplo —, entao o percentual
-- passa a ser configuravel por barbeiro.
--
-- A coluna nasce NULA de proposito: NULL significa "usa o mesmo percentual do servico", que e
-- exatamente o comportamento de hoje. Assim ninguem tem a comissao alterada pelo simples ato de
-- subir esta versao — mexer no bolso do barbeiro tem que ser uma decisao explicita do dono.
-- Default 0 seria pior: zeraria a comissao de produto de todo mundo silenciosamente.

ALTER TABLE professionals
    ADD COLUMN product_commission_percent NUMERIC(5, 2);

ALTER TABLE professionals
    ADD CONSTRAINT chk_product_commission_percent_range
        CHECK (product_commission_percent IS NULL
               OR (product_commission_percent >= 0 AND product_commission_percent <= 100));
