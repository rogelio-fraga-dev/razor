-- V8: Admins por tenant (Fase 1). Cada barbearia passa a ter seus proprios telefones
-- administrativos, base para o roteamento admin vs cliente e para as notificacoes por tenant.
--
-- A tabela nasce VAZIA de proposito: enquanto um tenant nao tiver admins cadastrados aqui,
-- o TenantAdminService recai nos telefones do config global (razor.admin.phones) — assim o
-- tenant padrao continua se comportando exatamente como hoje (transicao sem regressao).
-- Novos tenants (onboarding) cadastrarao seus proprios admins nesta tabela.

CREATE TABLE barbershop_admins (
    id            UUID PRIMARY KEY,
    barbershop_id UUID NOT NULL REFERENCES barbershops(id),
    phone         VARCHAR(20) NOT NULL,
    name          VARCHAR(255),
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    CONSTRAINT uq_barbershop_admins_shop_phone UNIQUE (barbershop_id, phone)
);
CREATE INDEX idx_barbershop_admins_shop ON barbershop_admins(barbershop_id);
