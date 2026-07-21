-- V4: Fundacao multi-tenant (Fase 1)
-- Cria a tabela de barbearias (tenant), semeia um tenant padrao para os dados
-- existentes (transicao single -> multi), adiciona barbershop_id em todas as
-- tabelas (backfill via DEFAULT + FK), troca o UNIQUE global de phone_number por
-- UNIQUE(barbershop_id, phone_number) e cria os catalogos de profissionais e servicos.
--
-- Ponte de transicao: barbershop_id tem DEFAULT no tenant padrao para os INSERTs
-- atuais (que ainda nao conhecem tenant) continuarem funcionando. O DEFAULT sera
-- removido num incremento futuro, quando o TenantContext popular a coluna explicitamente.

CREATE TABLE barbershops (
    id                UUID PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    slug              VARCHAR(120) NOT NULL UNIQUE,
    whatsapp_instance VARCHAR(120),
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP
);

-- Tenant padrao (bridge single-tenant -> multi-tenant)
INSERT INTO barbershops (id, name, slug, whatsapp_instance, active, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'Barbearia Ficticia Figaro', 'figaro', 'BarberBotOficial', TRUE, now(), now());

-- barbershop_id em todas as tabelas existentes (NOT NULL com DEFAULT no tenant padrao)
ALTER TABLE customers        ADD COLUMN barbershop_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE interactions     ADD COLUMN barbershop_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE appointments     ADD COLUMN barbershop_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE scheduled_tasks  ADD COLUMN barbershop_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE waitlist_entries ADD COLUMN barbershop_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';

-- Chaves estrangeiras para o tenant
ALTER TABLE customers        ADD CONSTRAINT fk_customers_shop    FOREIGN KEY (barbershop_id) REFERENCES barbershops(id);
ALTER TABLE interactions     ADD CONSTRAINT fk_interactions_shop FOREIGN KEY (barbershop_id) REFERENCES barbershops(id);
ALTER TABLE appointments     ADD CONSTRAINT fk_appointments_shop FOREIGN KEY (barbershop_id) REFERENCES barbershops(id);
ALTER TABLE scheduled_tasks  ADD CONSTRAINT fk_tasks_shop        FOREIGN KEY (barbershop_id) REFERENCES barbershops(id);
ALTER TABLE waitlist_entries ADD CONSTRAINT fk_waitlist_shop     FOREIGN KEY (barbershop_id) REFERENCES barbershops(id);

-- Telefone deixa de ser unico globalmente: passa a ser unico POR barbearia.
-- (V1 criou phone_number UNIQUE -> constraint implicito customers_phone_number_key.)
ALTER TABLE customers DROP CONSTRAINT customers_phone_number_key;
ALTER TABLE customers ADD CONSTRAINT uq_customers_shop_phone UNIQUE (barbershop_id, phone_number);

CREATE INDEX idx_customers_shop ON customers(barbershop_id);
CREATE INDEX idx_appointments_shop ON appointments(barbershop_id);

-- Catalogo de profissionais (agenda multiprofissional)
CREATE TABLE professionals (
    id            UUID PRIMARY KEY,
    barbershop_id UUID NOT NULL REFERENCES barbershops(id),
    name          VARCHAR(255) NOT NULL,
    phone         VARCHAR(20),
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);
CREATE INDEX idx_professionals_shop ON professionals(barbershop_id);

-- Catalogo de servicos (preco + duracao)
CREATE TABLE services (
    id               UUID PRIMARY KEY,
    barbershop_id    UUID NOT NULL REFERENCES barbershops(id),
    name             VARCHAR(255) NOT NULL,
    price            NUMERIC(10,2) NOT NULL DEFAULT 0,
    duration_minutes INTEGER NOT NULL DEFAULT 30,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP
);
CREATE INDEX idx_services_shop ON services(barbershop_id);
