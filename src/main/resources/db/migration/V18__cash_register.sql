-- V18: caixa (ritual diario da barbearia). Abertura com fundo de troco, movimentos ao longo
-- do dia (vendas, sangrias, suprimentos) e fechamento com conferencia da gaveta.
--
-- Regra central: no maximo UMA sessao ABERTA por barbearia ao mesmo tempo (garantida por
-- indice unico parcial). A "gaveta esperada" no fechamento e calculada dos movimentos:
--   esperado = fundo + suprimentos - sangrias + vendas_em_dinheiro
-- e a diferenca (contado - esperado) aponta sobra/falta.

CREATE TABLE cash_register_sessions (
    id                UUID PRIMARY KEY,
    barbershop_id     UUID NOT NULL REFERENCES barbershops(id),
    opened_by_user_id UUID,
    opened_by_name    VARCHAR(255),
    opened_at         TIMESTAMP NOT NULL,
    opening_amount    NUMERIC(12,2) NOT NULL,
    closed_by_user_id UUID,
    closed_by_name    VARCHAR(255),
    closed_at         TIMESTAMP,
    closing_counted   NUMERIC(12,2),   -- valor efetivamente contado na gaveta
    closing_expected  NUMERIC(12,2),   -- gaveta esperada, calculada no fechamento
    notes             VARCHAR(500),
    status            VARCHAR(10) NOT NULL,   -- OPEN | CLOSED
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP
);

-- No maximo uma sessao aberta por barbearia (o resto do sistema depende disso).
CREATE UNIQUE INDEX ux_cash_session_one_open
    ON cash_register_sessions(barbershop_id) WHERE status = 'OPEN';
CREATE INDEX idx_cash_session_shop ON cash_register_sessions(barbershop_id, opened_at);

CREATE TABLE cash_movements (
    id             UUID PRIMARY KEY,
    session_id     UUID NOT NULL REFERENCES cash_register_sessions(id) ON DELETE CASCADE,
    barbershop_id  UUID NOT NULL REFERENCES barbershops(id),
    type           VARCHAR(12) NOT NULL,   -- VENDA | SANGRIA | SUPRIMENTO
    payment_method VARCHAR(12),            -- DINHEIRO | PIX | DEBITO | CREDITO (so em VENDA)
    amount         NUMERIC(12,2) NOT NULL, -- sempre positivo
    description    VARCHAR(255),
    created_at     TIMESTAMP NOT NULL,

    CONSTRAINT ck_cash_movement_amount CHECK (amount > 0)
);
CREATE INDEX idx_cash_movements_session ON cash_movements(session_id);
