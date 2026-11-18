-- V42: confirmacao de presenca (anti-falta), sinal para quem falta muito e o registro do que a IA
-- fez pela barbearia (card "O que a IA fez por voce").

-- 1) Confirmacao do horario pelo cliente (pergunta "responde 1 pra confirmar ou 2 pra desmarcar",
--    enviada na vespera). NULL = nao foi perguntado (confirmacao desligada, ou horario marcado em
--    cima da hora — ver AgendaTaskService). PENDING = perguntado, sem resposta; CONFIRMED;
--    DECLINED (o cliente desmarcou — na pratica a linha e apagada pelo cancelamento, mas o valor
--    existe para o caso de o painel guardar o historico no futuro); NO_RESPONSE = passou do prazo.
ALTER TABLE appointments
    ADD COLUMN confirmation_status VARCHAR(20);

ALTER TABLE appointments
    ADD CONSTRAINT chk_appointment_confirmation_status
        CHECK (confirmation_status IN ('PENDING', 'CONFIRMED', 'DECLINED', 'NO_RESPONSE'));

-- 2) Sinal (deposito) exigido de cliente com faltas repetidas. NULL = nao exigido; PENDING = o bot
--    mandou o PIX e o dono ainda nao confirmou o recebimento; PAID = confirmado no painel.
ALTER TABLE appointments
    ADD COLUMN deposit_status VARCHAR(20);

ALTER TABLE appointments
    ADD CONSTRAINT chk_appointment_deposit_status CHECK (deposit_status IN ('PENDING', 'PAID'));

-- 3) Configuracoes por barbearia. A confirmacao nasce LIGADA (e o produto); liberar a vaga sozinho
--    nasce DESLIGADO (quem decide tirar o horario de um cliente e o dono); o sinal nasce desligado.
ALTER TABLE barbershop_settings
    ADD COLUMN confirmation_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN confirmation_hour SMALLINT NOT NULL DEFAULT 18,
    ADD COLUMN confirmation_deadline_hours SMALLINT NOT NULL DEFAULT 3,
    ADD COLUMN confirmation_auto_release BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN no_show_deposit_after INTEGER,
    ADD COLUMN no_show_deposit_amount NUMERIC(10, 2);

ALTER TABLE barbershop_settings
    ADD CONSTRAINT chk_confirmation_hour CHECK (confirmation_hour BETWEEN 0 AND 23),
    ADD CONSTRAINT chk_confirmation_deadline CHECK (confirmation_deadline_hours BETWEEN 1 AND 23),
    ADD CONSTRAINT chk_no_show_deposit_after CHECK (no_show_deposit_after IS NULL OR no_show_deposit_after > 0);

-- 4) Fatos que o agendamento nao guarda: cancelar APAGA o agendamento, entao "o cliente desmarcou
--    pela confirmacao" ou "a lista de espera ocupou a vaga" sumiriam junto. Sem FK de proposito:
--    o evento precisa sobreviver ao DELETE do agendamento.
CREATE TABLE ai_impact_events (
    id             UUID PRIMARY KEY,
    barbershop_id  UUID        NOT NULL REFERENCES barbershops(id),
    event_type     VARCHAR(30) NOT NULL,
    appointment_id BIGINT,
    customer_phone VARCHAR(30),
    created_at     TIMESTAMP   NOT NULL,
    CONSTRAINT chk_ai_impact_event_type
        CHECK (event_type IN ('CONFIRMED', 'DECLINED', 'NO_RESPONSE', 'AUTO_RELEASED', 'WAITLIST_REFILL'))
);

CREATE INDEX idx_ai_impact_events_shop_time ON ai_impact_events(barbershop_id, created_at);
