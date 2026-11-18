-- V36: Limite de agendamentos por cliente + grupos de clientes por tags.
--
-- 1. LIMITE DE AGENDAMENTOS
--    Sem limite, um cliente pode "garantir" tres ou quatro horarios na semana e desmarcar em cima
--    da hora — a agenda parece cheia e nao esta. `max_future_bookings_per_customer` e quantos
--    horarios FUTUROS um mesmo telefone pode ter ao mesmo tempo. NULL = sem limite.
--
--    O padrao 2 vale para quem ja existe tambem: e o numero que o prompt da IA ja anunciava
--    ("limite de 2"), so que ate aqui ninguem conferia de verdade. Barbearia que nao quer limite
--    limpa o campo no painel.
--
-- 2. TAGS DE CLIENTE
--    Grupos livres definidos pelo dono ("VIP", "Barba", "Indicou amigo") para segmentar a base e
--    mirar os disparos. N:N entre clientes e tags. O STATUS (novo/ativo/em risco/sumido) NAO e
--    gravado: e calculado do historico de visitas, com a mesma regra do relatorio de retencao —
--    gravado, ficaria velho no dia seguinte.

ALTER TABLE barbershop_settings
    ADD COLUMN max_future_bookings_per_customer INTEGER DEFAULT 2;

ALTER TABLE barbershop_settings
    ADD CONSTRAINT chk_max_future_bookings_positive
        CHECK (max_future_bookings_per_customer IS NULL OR max_future_bookings_per_customer > 0);

CREATE TABLE customer_tags (
    id            UUID PRIMARY KEY,
    barbershop_id UUID NOT NULL REFERENCES barbershops(id),
    name          VARCHAR(40) NOT NULL,
    -- Cor do chip no painel, "#RRGGBB".
    color         VARCHAR(7)  NOT NULL DEFAULT '#6B7280',
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,

    CONSTRAINT chk_customer_tag_color CHECK (color ~ '^#[0-9A-Fa-f]{6}$')
);

-- "VIP" e "vip" sao a mesma tag: duas iguais dividiriam o grupo ao meio sem ninguem perceber.
CREATE UNIQUE INDEX uq_customer_tags_shop_name ON customer_tags (barbershop_id, lower(name));

CREATE TABLE customer_tag_links (
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES customer_tags(id) ON DELETE CASCADE,
    created_at  TIMESTAMP,
    PRIMARY KEY (customer_id, tag_id)
);

-- O PK ja cobre "tags do cliente"; este cobre "clientes da tag" (filtro e disparo por grupo).
CREATE INDEX idx_customer_tag_links_tag ON customer_tag_links (tag_id);
