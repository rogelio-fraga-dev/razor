-- V16: folgas e bloqueios de agenda. Sem isso a IA oferece (e marca) horario em folga,
-- almoco, feriado ou manutencao — o buraco mais grave da agenda hoje.
--
-- Um bloqueio pode ser:
--   * da barbearia inteira (professional_id NULL) — ex.: feriado, reforma
--   * de um profissional        (professional_id) — ex.: folga do Diego, almoco
-- e pode ser:
--   * pontual   (block_date preenchido)            — ex.: 25/12 o dia todo
--   * recorrente (weekday 0..6, block_date NULL)   — ex.: toda segunda-feira
-- Horario NULL em start/end = dia inteiro.

CREATE TABLE schedule_blocks (
    id              UUID PRIMARY KEY,
    barbershop_id   UUID NOT NULL REFERENCES barbershops(id),
    professional_id UUID REFERENCES professionals(id) ON DELETE CASCADE,
    block_date      DATE,
    weekday         SMALLINT,
    start_time      TIME,
    end_time        TIME,
    reason          VARCHAR(255),
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,

    -- ou e pontual (data) ou e recorrente (dia da semana) — nunca os dois, nunca nenhum
    CONSTRAINT ck_schedule_blocks_when CHECK (
        (block_date IS NOT NULL AND weekday IS NULL)
        OR (block_date IS NULL AND weekday BETWEEN 0 AND 6)
    ),
    -- intervalo coerente: ou dia inteiro (ambos nulos) ou fim depois do inicio
    CONSTRAINT ck_schedule_blocks_range CHECK (
        (start_time IS NULL AND end_time IS NULL)
        OR (start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time)
    )
);

CREATE INDEX idx_schedule_blocks_shop_date ON schedule_blocks(barbershop_id, block_date);
CREATE INDEX idx_schedule_blocks_shop_weekday ON schedule_blocks(barbershop_id, weekday);
