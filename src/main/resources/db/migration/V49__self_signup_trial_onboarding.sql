-- V49: Cadastro sozinho pela landing (barbearia + dono) e assistente guiado de 10 minutos.
--
-- 1. status / trial_ends_at: quem se cadastra pela landing nasce em TRIAL com data de fim do
--    teste. Por enquanto e SO informacao (o painel mostra "teste gratis ate dd/mm"): a cobranca
--    da assinatura e decisao pendente (docs/MVP-PILOTO.md), entao nada e bloqueado quando o teste
--    acaba. Texto validado por CHECK, nao tipo do Postgres: status novo (ex.: SUSPENDED, quando
--    houver cobranca) nao pode exigir migracao de tipo. As barbearias que ja existem sao ACTIVE.
--
-- 2. city / signup_plan: o que o cadastro pergunta e ainda nao tinha onde morar. signup_plan e o
--    "?plano=" do botao da landing (solo|equipe|pro) — so registra o interesse, nao e contrato.
--
-- 3. onboarding_steps_done / onboarding_completed_at: o progresso do assistente fica no servidor
--    (e nao no navegador) para o dono poder parar no celular e continuar no computador. CSV dos
--    passos concluidos ou pulados, na ordem em que aconteceram. As barbearias antigas ja estao
--    configuradas: entram como concluidas para nao ganharem um "continue a configuracao" do nada.

ALTER TABLE barbershops ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE barbershops ADD COLUMN trial_ends_at TIMESTAMP;
ALTER TABLE barbershops ADD COLUMN city VARCHAR(120);
ALTER TABLE barbershops ADD COLUMN signup_plan VARCHAR(20);
ALTER TABLE barbershops ADD COLUMN onboarding_steps_done VARCHAR(200);
ALTER TABLE barbershops ADD COLUMN onboarding_completed_at TIMESTAMP;

ALTER TABLE barbershops
    ADD CONSTRAINT chk_barbershops_status CHECK (status IN ('ACTIVE', 'TRIAL'));
ALTER TABLE barbershops
    ADD CONSTRAINT chk_barbershops_signup_plan CHECK (signup_plan IS NULL OR signup_plan IN ('SOLO', 'EQUIPE', 'PRO'));
-- Teste sem data de fim seria um teste eterno escondido; a regra fica no banco.
ALTER TABLE barbershops
    ADD CONSTRAINT chk_barbershops_trial_end CHECK (status <> 'TRIAL' OR trial_ends_at IS NOT NULL);

UPDATE barbershops SET onboarding_completed_at = COALESCE(created_at, NOW())
 WHERE onboarding_completed_at IS NULL;

COMMENT ON COLUMN barbershops.status IS
    'ACTIVE | TRIAL. TRIAL = cadastro pela landing; so informativo enquanto nao houver cobranca.';
COMMENT ON COLUMN barbershops.onboarding_steps_done IS
    'Passos do assistente guiado ja concluidos/pulados (CSV). Nulo = nenhum.';
