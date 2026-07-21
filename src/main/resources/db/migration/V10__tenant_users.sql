-- V10: Usuarios do painel web (Fase 3). Login do admin da barbearia (e da equipe) para o
-- console web onde toda a configuracao e os relatorios acontecem. A autenticacao usa estes
-- registros (email + hash de senha bcrypt); o tenant do usuario vira o TenantContext da sessao.

CREATE TABLE tenant_users (
    id            UUID PRIMARY KEY,
    barbershop_id UUID NOT NULL REFERENCES barbershops(id),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(255),
    role          VARCHAR(30) NOT NULL DEFAULT 'OWNER',
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);
CREATE INDEX idx_tenant_users_shop ON tenant_users(barbershop_id);
