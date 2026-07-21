-- V5: Semeia o catalogo de servicos do tenant padrao (Fase 1).
-- Precos/duracoes alinhados a logica financeira do AgendaService (combo=60, barba=30,
-- demais=35). Novos tenants terao o catalogo semeado no provisionamento (fase futura).

INSERT INTO services (id, barbershop_id, name, price, duration_minutes, active, created_at, updated_at)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'Corte de Cabelo', 35.00, 30, TRUE, now(), now()),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'Barba', 30.00, 30, TRUE, now(), now()),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'Corte + Barba (Combo)', 60.00, 60, TRUE, now(), now());
