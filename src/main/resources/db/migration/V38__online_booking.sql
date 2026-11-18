-- V38: Agendamento online pelo site (pagina publica /b/{slug}/agendar).
--
-- 1. `appointments.source`: de onde veio o agendamento — BOT (WhatsApp/IA), PAINEL (recepcao ou
--    dono no painel) ou SITE (pagina publica). Alimenta a metrica de ROI da IA ("quanto da agenda
--    o bot trouxe sozinho"). Tudo que existe hoje nasceu do bot ou do lote do admin pelo WhatsApp,
--    entao o backfill e BOT — a mesma resposta que o DEFAULT da para as linhas antigas.
--
--    Texto validado por CHECK, nao tipo do Postgres: acrescentar origem (ex.: APP) nao pode exigir
--    recriar tipo.
ALTER TABLE appointments
    ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'BOT';

ALTER TABLE appointments
    ADD CONSTRAINT chk_appointment_source CHECK (source IN ('BOT', 'PAINEL', 'SITE'));

COMMENT ON COLUMN appointments.source IS
    'Origem do agendamento: BOT (WhatsApp/IA), PAINEL ou SITE (agendamento online).';

-- 2. Liga/desliga do agendamento online, por barbearia. Mora no site porque e uma decisao sobre a
--    cara publica da casa e e editada no mesmo lugar. Nasce DESLIGADO: agendar sem login e uma
--    porta nova para a agenda do dono, e quem abre a porta e ele, nao a migracao.
ALTER TABLE barbershop_site
    ADD COLUMN online_booking_enabled BOOLEAN NOT NULL DEFAULT FALSE;
