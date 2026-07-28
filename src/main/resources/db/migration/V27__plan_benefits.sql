-- V27: Motor de beneficios de plano + comissao por servico.
--
-- Modelado a partir das telas do app do cliente (CashBarber): um plano como
-- "Corte BRONZE ILIMITADO - Segunda a Quarta" da 100% OFF em Corte e Acabamento com cota
-- mensal e restricao de dia, e 10% OFF nas categorias Extras e Cosmeticos.
--
-- DECISOES:
--
-- 1. O beneficio aponta para UM servico/produto especifico OU para TODOS de um tipo. A tela
--    mostra os dois casos ("Corte de cabelo completo: 100% OFF" e "Cosmeticos: 10% OFF"), e
--    tratar os dois como o mesmo registro evita duplicar a regra de calculo.
--
-- 2. Cota e restricao de dia sao OPCIONAIS (nulas = sem limite / todos os dias). Plano simples
--    de "10% em tudo" nao deve exigir preencher campo de cota.
--
-- 3. Preco final NUNCA fica negativo: 100% OFF zera, nao inverte.

-- ---------------------------------------------------------------------------
-- Comissao por servico: o dono define quanto o barbeiro ganha em cada servico.
-- NULL = usa o percentual geral do barbeiro (V23), preservando o comportamento atual.
-- ---------------------------------------------------------------------------
ALTER TABLE services
    ADD COLUMN commission_percent NUMERIC(5, 2);

ALTER TABLE services
    ADD CONSTRAINT chk_service_commission_range
        CHECK (commission_percent IS NULL
               OR (commission_percent >= 0 AND commission_percent <= 100));

-- ---------------------------------------------------------------------------
-- Beneficios do plano
-- ---------------------------------------------------------------------------
CREATE TABLE plan_benefits (
    id             UUID PRIMARY KEY,
    barbershop_id  UUID NOT NULL REFERENCES barbershops(id),
    plan_id        UUID NOT NULL REFERENCES subscription_plans(id) ON DELETE CASCADE,

    -- SERVICO | PRODUTO: a que o beneficio se aplica.
    target_type    VARCHAR(10) NOT NULL,
    -- Item especifico (services.id ou products.id). NULL = vale para TODOS do target_type,
    -- que e como a tela mostra "Cosmeticos: 10% OFF".
    target_id      UUID,

    -- 0–100. 100 = gratis.
    discount_percent NUMERIC(5,2) NOT NULL,

    -- Quantas vezes por mes o beneficio vale. NULL = ilimitado.
    monthly_quota  INTEGER,

    -- Dias da semana em que vale, como CSV ISO (1=segunda ... 7=domingo). NULL = todos.
    -- Ex.: "1,2,3" reproduz o "Segunda, terca ou quarta" do plano BRONZE.
    weekdays       VARCHAR(20),

    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,

    CONSTRAINT chk_benefit_target_type CHECK (target_type IN ('SERVICO', 'PRODUTO')),
    CONSTRAINT chk_benefit_discount_range CHECK (discount_percent >= 0 AND discount_percent <= 100),
    CONSTRAINT chk_benefit_quota CHECK (monthly_quota IS NULL OR monthly_quota > 0)
);

CREATE INDEX idx_plan_benefits_plan ON plan_benefits (plan_id);
-- Um beneficio por alvo dentro do plano: dois descontos para o mesmo item seria ambiguo.
CREATE UNIQUE INDEX uq_plan_benefit_target
    ON plan_benefits (plan_id, target_type, COALESCE(target_id, '00000000-0000-0000-0000-000000000000'::uuid));

-- Regra de agenda do plano (a "Periodicidade" da tela).
ALTER TABLE subscription_plans
    ADD COLUMN booking_window_days INTEGER;

COMMENT ON COLUMN subscription_plans.booking_window_days IS
    'Com quantos dias de antecedencia o assinante pode agendar. NULL = sem regra especial.';
