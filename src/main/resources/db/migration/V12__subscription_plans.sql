-- V12: Planos de assinatura (Fase 2 — clube de assinatura). Cada barbearia define seus planos
-- recorrentes (ex.: VIP, Silver). A cobranca recorrente em si (gateway) fica para a Fase 4;
-- aqui e o catalogo de planos que o painel gerencia e que depois identifica assinante x avulso.

CREATE TABLE subscription_plans (
    id            UUID PRIMARY KEY,
    barbershop_id UUID NOT NULL REFERENCES barbershops(id),
    name          VARCHAR(255) NOT NULL,
    price         NUMERIC(10,2) NOT NULL DEFAULT 0,
    description   TEXT,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);
CREATE INDEX idx_subscription_plans_shop ON subscription_plans(barbershop_id);
