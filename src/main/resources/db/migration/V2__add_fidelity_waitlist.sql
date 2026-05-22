-- 1. Adiciona colunas para controle de fidelidade e última visita
ALTER TABLE customers ADD COLUMN fidelity_points INTEGER DEFAULT 0;
ALTER TABLE customers ADD COLUMN last_service_date TIMESTAMP;

-- 2. Tabela de Lista de Espera Inteligente
CREATE TABLE waitlist_entries (
    id UUID PRIMARY KEY,
    customer_phone VARCHAR(20) NOT NULL,
    customer_name VARCHAR(255),
    preferred_day VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- 3. Índices para performance na busca de lista de espera
CREATE INDEX idx_waitlist_day ON waitlist_entries(preferred_day);
CREATE INDEX idx_waitlist_phone ON waitlist_entries(customer_phone);
