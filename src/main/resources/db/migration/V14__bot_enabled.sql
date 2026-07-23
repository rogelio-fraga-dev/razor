-- V14: liga/desliga do bot por tenant (controle total do dono no painel). Default TRUE:
-- tenants existentes seguem atendendo normalmente. Quando FALSE, o webhook ignora mensagens
-- de CLIENTES (admins continuam funcionando) — o dono pode "puxar o freio" a qualquer momento.

ALTER TABLE barbershop_settings
    ADD COLUMN bot_enabled BOOLEAN NOT NULL DEFAULT TRUE;
