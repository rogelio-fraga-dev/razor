-- V39: agenda interativa do painel.
--
-- 1) Comparecimento do cliente. Ate aqui um agendamento so tinha "existe" ou "foi apagado"
--    (cancelar = DELETE). O painel precisa registrar o que aconteceu de fato: o cliente veio
--    (ATTENDED) ou faltou (NO_SHOW). NULL = ainda agendado / nao informado — que e o estado de
--    todo agendamento existente, entao nao ha backfill.
ALTER TABLE appointments
    ADD COLUMN attendance_status VARCHAR(20);

COMMENT ON COLUMN appointments.attendance_status IS
    'NULL = agendado; ATTENDED = compareceu; NO_SHOW = faltou. Preenchido pelo painel.';

-- 2) Tarefa agendada (lembrete/avaliacao) presa ao AGENDAMENTO, e nao so ao telefone.
--
--    O bug que isto fecha: cancelar UM agendamento cancelava os lembretes de TODOS os horarios
--    do cliente, porque a unica chave da tarefa era o telefone. Quem tinha corte na terca e na
--    sexta e desmarcava a terca deixava de receber o lembrete da sexta. Com o vinculo, cancelar
--    ou remarcar mexe so nas tarefas daquele horario (e o lembrete acompanha a remarcacao).
--
--    ON DELETE SET NULL: cancelar apaga o agendamento (comportamento historico do bot); a
--    tarefa ja foi cancelada antes disso e fica como registro.
ALTER TABLE scheduled_tasks
    ADD COLUMN appointment_id BIGINT REFERENCES appointments(id) ON DELETE SET NULL;

CREATE INDEX idx_scheduled_tasks_appointment ON scheduled_tasks(appointment_id)
    WHERE appointment_id IS NOT NULL;
