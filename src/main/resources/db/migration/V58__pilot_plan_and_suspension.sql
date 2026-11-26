-- Piloto (26/11): plano PILOTO no cadastro por convite e situação SUSPENDED pelo console.
-- Sem isto o banco recusava o cadastro do convite e a suspensão (regras da V49).

alter table barbershops drop constraint if exists chk_barbershops_signup_plan;
alter table barbershops add constraint chk_barbershops_signup_plan
    check (signup_plan is null or signup_plan in ('SOLO', 'EQUIPE', 'PRO', 'PILOTO'));

alter table barbershops drop constraint if exists chk_barbershops_status;
alter table barbershops add constraint chk_barbershops_status
    check (status in ('ACTIVE', 'TRIAL', 'SUSPENDED'));
