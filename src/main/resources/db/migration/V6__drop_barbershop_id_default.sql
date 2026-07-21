-- V6: Remove o DEFAULT-ponte de barbershop_id (Fase 1).
-- A ponte da V4 permitia INSERTs sem tenant cairem no tenant padrao durante a
-- transicao single -> multi-tenant. Agora todos os INSERTs do app setam o tenant
-- explicitamente, entao removemos o DEFAULT: a coluna segue NOT NULL e um insert
-- que esqueca o tenant passa a falhar (fail-fast) em vez de silenciosamente
-- gravar na barbearia errada.

ALTER TABLE customers        ALTER COLUMN barbershop_id DROP DEFAULT;
ALTER TABLE interactions     ALTER COLUMN barbershop_id DROP DEFAULT;
ALTER TABLE appointments     ALTER COLUMN barbershop_id DROP DEFAULT;
ALTER TABLE scheduled_tasks  ALTER COLUMN barbershop_id DROP DEFAULT;
ALTER TABLE waitlist_entries ALTER COLUMN barbershop_id DROP DEFAULT;
