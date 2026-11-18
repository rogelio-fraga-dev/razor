-- V46: avaliacao pos-atendimento pelo bot.
--
-- Depois que a comanda fecha (o cliente de fato foi atendido), o bot pede uma nota de 1 a 5 no
-- WhatsApp. Nota 4-5 recebe o link de avaliacao do Google da barbearia; nota 1-3 vai so para o
-- dono, como alerta (nota baixa nunca e mandada para o Google).

-- 1) Configuracoes por barbearia. Nasce LIGADO: ate aqui toda barbearia ja mandava, 1h depois do
--    horario, um pedido de avaliacao com o link do mapa — agora a nota vem antes do link.
ALTER TABLE barbershop_settings
    ADD COLUMN google_review_url      TEXT,
    ADD COLUMN review_request_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN review_delay_minutes   INTEGER NOT NULL DEFAULT 120;

ALTER TABLE barbershop_settings
    ADD CONSTRAINT chk_review_delay_minutes CHECK (review_delay_minutes BETWEEN 15 AND 1440);

-- 2) Um pedido de avaliacao por atendimento. A linha nasce SCHEDULED (programada), vira SENT quando
--    a pergunta sai, RATED com a nota. EXPIRED = ninguem respondeu; CANCELLED = nao chegou a sair
--    (pedido de outro atendimento mais recente, cliente virou admin...); OPTED_OUT = o cliente pediu
--    para nao receber mais pedidos de avaliacao (vale para sempre naquela barbearia).
--    Sem FK para comanda/agendamento de proposito: a nota precisa sobreviver ao DELETE do horario.
CREATE TABLE customer_reviews (
    id              UUID PRIMARY KEY,
    barbershop_id   UUID         NOT NULL REFERENCES barbershops(id),
    customer_phone  VARCHAR(20)  NOT NULL,
    customer_name   VARCHAR(255),
    comanda_id      UUID,
    appointment_id  BIGINT,
    professional_id UUID,
    status          VARCHAR(12)  NOT NULL,
    rating          INTEGER,
    comment         TEXT,
    scheduled_for   TIMESTAMP    NOT NULL,
    sent_at         TIMESTAMP,
    rated_at        TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP,
    CONSTRAINT chk_customer_review_status
        CHECK (status IN ('SCHEDULED', 'SENT', 'RATED', 'EXPIRED', 'CANCELLED', 'OPTED_OUT')),
    CONSTRAINT chk_customer_review_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5)
);

-- Motor de envio: so as programadas, por horario.
CREATE INDEX idx_customer_reviews_due ON customer_reviews (scheduled_for) WHERE status = 'SCHEDULED';
-- Regra "no maximo 1 pedido a cada 30 dias" e opt-out, por cliente.
CREATE INDEX idx_customer_reviews_phone ON customer_reviews (barbershop_id, customer_phone, created_at);
-- Card do painel: notas mais recentes.
CREATE INDEX idx_customer_reviews_rated ON customer_reviews (barbershop_id, rated_at) WHERE rating IS NOT NULL;
-- Uma comanda gera no maximo um pedido (fechamento repetido / corrida).
CREATE UNIQUE INDEX uq_customer_reviews_comanda ON customer_reviews (comanda_id) WHERE comanda_id IS NOT NULL;

-- 3) O pedido antigo (REVIEW_REQUEST, criado no AGENDAMENTO e mandado 1h depois do horario mesmo
--    para quem faltou) deixa de existir: os que ainda estao na fila sao cancelados.
UPDATE scheduled_tasks
   SET status = 'CANCELLED', updated_at = now()
 WHERE task_type = 'REVIEW_REQUEST' AND status = 'PENDING';
