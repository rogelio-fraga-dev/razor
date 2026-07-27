-- V19: Meta de faturamento mensal por barbearia.
--
-- O painel mostrava uma meta fixa no codigo (R$ 50.000) — numero que nao era de ninguem. A meta
-- passa a ser do dono: ele define no painel e o progresso do mes e calculado em cima do
-- faturamento real. Coluna opcional: sem meta definida, o painel simplesmente nao mostra o card.
--
-- Fica em barbershop_settings (1:1 com barbershops) porque e configuracao do tenant, mas com
-- endpoint proprio (/api/admin/settings/goals) — o PUT das configuracoes do bot troca o registro
-- inteiro e nao pode zerar a meta sem querer.

ALTER TABLE barbershop_settings
    ADD COLUMN monthly_revenue_goal NUMERIC(12, 2);

-- Meta zero ou negativa nao significa nada (o progresso dividiria por zero): ou tem meta, ou e nula.
ALTER TABLE barbershop_settings
    ADD CONSTRAINT chk_monthly_revenue_goal_positive
        CHECK (monthly_revenue_goal IS NULL OR monthly_revenue_goal > 0);
