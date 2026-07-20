-- V3: Tipagem forte das datas de agendamento (Opcao A)
-- Converte appointment_date VARCHAR ("DD/MM" ou "hoje") -> DATE, inferindo o ano
-- (proxima ocorrencia futura), e appointment_time VARCHAR ("HH:mm"/"HHhMM"/"HHh") -> TIME.
-- Backfill best-effort de dados legados; linhas nao parseaveis ficam NULL.

ALTER TABLE appointments ADD COLUMN appointment_date_new DATE;
ALTER TABLE appointments ADD COLUMN appointment_time_new TIME;

-- Backfill da data
UPDATE appointments SET appointment_date_new = CASE
    WHEN appointment_date IS NULL THEN NULL
    WHEN lower(trim(appointment_date)) = 'hoje' THEN CURRENT_DATE
    WHEN trim(appointment_date) ~ '^[0-9]{1,2}/[0-9]{1,2}$' THEN
        CASE
            WHEN to_date(trim(appointment_date) || '/' || EXTRACT(YEAR FROM CURRENT_DATE)::int, 'DD/MM/YYYY') < CURRENT_DATE
                THEN to_date(trim(appointment_date) || '/' || (EXTRACT(YEAR FROM CURRENT_DATE)::int + 1), 'DD/MM/YYYY')
            ELSE to_date(trim(appointment_date) || '/' || EXTRACT(YEAR FROM CURRENT_DATE)::int, 'DD/MM/YYYY')
        END
    ELSE NULL
END;

-- Backfill da hora
UPDATE appointments SET appointment_time_new = CASE
    WHEN appointment_time IS NULL THEN NULL
    WHEN trim(appointment_time) ~ '^[0-9]{1,2}:[0-9]{2}$' THEN to_timestamp(trim(appointment_time), 'HH24:MI')::time
    WHEN trim(appointment_time) ~ '^[0-9]{1,2}h[0-9]{2}$' THEN to_timestamp(replace(trim(appointment_time), 'h', ':'), 'HH24:MI')::time
    WHEN trim(appointment_time) ~ '^[0-9]{1,2}h$' THEN to_timestamp(replace(trim(appointment_time), 'h', '') || ':00', 'HH24:MI')::time
    ELSE NULL
END;

-- Substitui as colunas antigas pelas tipadas
ALTER TABLE appointments DROP COLUMN appointment_date;
ALTER TABLE appointments DROP COLUMN appointment_time;
ALTER TABLE appointments RENAME COLUMN appointment_date_new TO appointment_date;
ALTER TABLE appointments RENAME COLUMN appointment_time_new TO appointment_time;

-- (Re)cria o indice de data agora que a coluna e um DATE tipado
-- (usado por AppointmentRepository.findByAppointmentDate*).
CREATE INDEX IF NOT EXISTS idx_appointments_date ON appointments(appointment_date);
