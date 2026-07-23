-- V13: Assinantes (Fase 2). Liga um cliente a um plano de assinatura (clube). A coluna e opcional:
-- cliente sem plano e "avulso"; com plano e "assinante". A cobranca recorrente (gateway) vem na
-- Fase 4 — aqui apenas identificamos assinante x avulso no painel e na agenda.
-- ON DELETE SET NULL: remover um plano do catalogo apenas desvincula os clientes (nao os apaga).

ALTER TABLE customers
    ADD COLUMN subscription_plan_id UUID REFERENCES subscription_plans(id) ON DELETE SET NULL;

CREATE INDEX idx_customers_plan ON customers(subscription_plan_id);
