-- V47: automacoes — lista de espera automatica, campanhas automaticas (sumiu, aniversario, retorno
-- do corte), relatorio semanal do dono e o opt-out do cliente.

-- 1) Lista de espera: o desejo do cliente passa a ser estruturado. preferred_day continua (texto
--    livre que a IA mandou, ex.: "sexta"); os campos novos sao opcionais e, quando preenchidos,
--    restringem a oferta. status: WAITING = esperando; BOOKED = ocupou uma vaga; CANCELLED = saiu.
ALTER TABLE waitlist_entries
    ADD COLUMN preferred_date   DATE,
    ADD COLUMN preferred_period VARCHAR(10),
    ADD COLUMN service          VARCHAR(255),
    ADD COLUMN professional_id  UUID REFERENCES professionals(id) ON DELETE SET NULL,
    ADD COLUMN status           VARCHAR(12) NOT NULL DEFAULT 'WAITING',
    ADD COLUMN updated_at       TIMESTAMP;

ALTER TABLE waitlist_entries
    ADD CONSTRAINT chk_waitlist_period CHECK (preferred_period IS NULL OR preferred_period IN ('MANHA', 'TARDE', 'NOITE')),
    ADD CONSTRAINT chk_waitlist_status CHECK (status IN ('WAITING', 'BOOKED', 'CANCELLED'));

CREATE INDEX idx_waitlist_shop_status ON waitlist_entries(barbershop_id, status, created_at);

-- 2) Ofertas de vaga liberada: uma por vez, com prazo. Persistida (e nao so no estado da conversa)
--    para o prazo sobreviver a um restart e o "proximo da fila" nunca receber a mesma vaga duas vezes.
CREATE TABLE waitlist_offers (
    id                    UUID PRIMARY KEY,
    barbershop_id         UUID         NOT NULL REFERENCES barbershops(id),
    entry_id              UUID         REFERENCES waitlist_entries(id) ON DELETE SET NULL,
    customer_phone        VARCHAR(20)  NOT NULL,
    customer_name         VARCHAR(255),
    slot_date             DATE         NOT NULL,
    slot_time             TIME         NOT NULL,
    service               VARCHAR(255),
    professional_id       UUID,
    professional_required BOOLEAN      NOT NULL DEFAULT FALSE,
    status                VARCHAR(10)  NOT NULL,
    expires_at            TIMESTAMP    NOT NULL,
    responded_at          TIMESTAMP,
    appointment_id        BIGINT,
    created_at            TIMESTAMP    NOT NULL,
    CONSTRAINT chk_waitlist_offer_status
        CHECK (status IN ('OFFERED', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'FAILED'))
);

CREATE INDEX idx_waitlist_offers_slot ON waitlist_offers(barbershop_id, slot_date, slot_time);
CREATE INDEX idx_waitlist_offers_open ON waitlist_offers(status, expires_at);
-- Um cliente tem no maximo UMA oferta aberta por vez (o "sim" dele precisa ser inequivoco).
CREATE UNIQUE INDEX uq_waitlist_offer_open_customer
    ON waitlist_offers(barbershop_id, customer_phone) WHERE status = 'OFFERED';
-- E cada vaga (dia + hora + barbeiro) e oferecida a uma pessoa de cada vez.
CREATE UNIQUE INDEX uq_waitlist_offer_open_slot
    ON waitlist_offers(barbershop_id, slot_date, slot_time,
                       COALESCE(professional_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE status = 'OFFERED';

-- 3) Cliente: aniversario (opcional; ano desconhecido e gravado como 2000), quando o bot perguntou
--    pela ultima vez, e o opt-out de mensagens automaticas ("parar"/"sair") — para sempre, ate o
--    proprio cliente pedir para voltar.
ALTER TABLE customers
    ADD COLUMN birth_date           DATE,
    ADD COLUMN birthday_asked_at    TIMESTAMP,
    ADD COLUMN marketing_opt_out_at TIMESTAMP;

-- 4) Retorno do corte: intervalo em dias por servico. NULL = sem lembrete para aquele servico.
ALTER TABLE services
    ADD COLUMN return_reminder_days INTEGER;

ALTER TABLE services
    ADD CONSTRAINT chk_service_return_days
        CHECK (return_reminder_days IS NULL OR return_reminder_days BETWEEN 7 AND 180);

-- 5) Cupom de aniversario: nova origem.
ALTER TABLE coupons DROP CONSTRAINT chk_coupon_origin;
ALTER TABLE coupons
    ADD CONSTRAINT chk_coupon_origin CHECK (origin IN ('MANUAL', 'FIDELIDADE', 'ANIVERSARIO'));

-- 6) Configuracao das automacoes, 1:1 com a barbearia (tabela propria: o PUT das configuracoes do
--    bot troca o registro inteiro). Sem linha = padroes do sistema. Textos NULL = modelo padrao.
CREATE TABLE automation_settings (
    barbershop_id              UUID PRIMARY KEY REFERENCES barbershops(id),
    waitlist_enabled           BOOLEAN       NOT NULL DEFAULT TRUE,
    waitlist_window_minutes    INTEGER       NOT NULL DEFAULT 15,
    lapsed_enabled             BOOLEAN       NOT NULL DEFAULT FALSE,
    lapsed_days                INTEGER       NOT NULL DEFAULT 30,
    lapsed_template            TEXT,
    birthday_enabled           BOOLEAN       NOT NULL DEFAULT FALSE,
    birthday_template          TEXT,
    birthday_discount_percent  NUMERIC(5, 2) NOT NULL DEFAULT 10,
    birthday_coupon_valid_days INTEGER       NOT NULL DEFAULT 15,
    return_enabled             BOOLEAN       NOT NULL DEFAULT FALSE,
    return_template            TEXT,
    frequency_cap_days         INTEGER       NOT NULL DEFAULT 7,
    weekly_report_enabled      BOOLEAN       NOT NULL DEFAULT TRUE,
    weekly_report_day          INTEGER       NOT NULL DEFAULT 1,
    weekly_report_hour         INTEGER       NOT NULL DEFAULT 8,
    created_at                 TIMESTAMP,
    updated_at                 TIMESTAMP,
    CONSTRAINT chk_auto_waitlist_window CHECK (waitlist_window_minutes BETWEEN 5 AND 240),
    CONSTRAINT chk_auto_lapsed_days CHECK (lapsed_days BETWEEN 7 AND 365),
    CONSTRAINT chk_auto_birthday_discount CHECK (birthday_discount_percent > 0 AND birthday_discount_percent <= 100),
    CONSTRAINT chk_auto_birthday_valid CHECK (birthday_coupon_valid_days BETWEEN 1 AND 90),
    CONSTRAINT chk_auto_frequency_cap CHECK (frequency_cap_days BETWEEN 1 AND 60),
    CONSTRAINT chk_auto_report_day CHECK (weekly_report_day BETWEEN 1 AND 7),
    CONSTRAINT chk_auto_report_hour CHECK (weekly_report_hour BETWEEN 0 AND 23)
);

-- 7) O que as automacoes ja enviaram: idempotencia entre restarts (a chave unica impede o mesmo
--    envio duas vezes) e base do limite de frequencia. reference_key identifica a "ocasiao": a
--    data da ultima visita (sumiu), o ano (aniversario), o agendamento de origem (retorno) ou a
--    semana ISO (relatorio). PENDING = reservado e em envio; FAILED pode ser tentado de novo.
CREATE TABLE automation_sends (
    id             UUID PRIMARY KEY,
    barbershop_id  UUID         NOT NULL REFERENCES barbershops(id),
    kind           VARCHAR(20)  NOT NULL,
    customer_phone VARCHAR(20)  NOT NULL,
    reference_key  VARCHAR(60)  NOT NULL,
    status         VARCHAR(10)  NOT NULL,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    detail         VARCHAR(255),
    last_error     VARCHAR(255),
    created_at     TIMESTAMP    NOT NULL,
    sent_at        TIMESTAMP,
    CONSTRAINT uq_automation_send UNIQUE (barbershop_id, kind, customer_phone, reference_key),
    CONSTRAINT chk_automation_send_kind CHECK (kind IN ('LAPSED', 'BIRTHDAY', 'RETURN', 'WEEKLY_REPORT')),
    CONSTRAINT chk_automation_send_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_automation_sends_phone ON automation_sends(barbershop_id, customer_phone, sent_at);
