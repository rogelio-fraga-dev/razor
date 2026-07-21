-- V7: Agenda multiprofissional (Fase 1) — vincula agendamentos a um profissional.
-- professional_id e NULLABLE: agendamentos legados (e os criados antes da escolha de
-- barbeiro ser ligada no fluxo) ficam sem profissional ate serem atribuidos.

ALTER TABLE appointments ADD COLUMN professional_id UUID REFERENCES professionals(id);
CREATE INDEX idx_appointments_professional ON appointments(barbershop_id, professional_id);

-- Profissional padrao do tenant padrao (a barbearia herdada passa a ter ao menos 1 barbeiro).
INSERT INTO professionals (id, barbershop_id, name, phone, active, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000001',
        'Barbeiro', NULL, TRUE, now(), now());
