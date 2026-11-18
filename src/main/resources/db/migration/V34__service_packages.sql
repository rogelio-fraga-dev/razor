-- V34: Pacotes pre-pagos (ex.: "10 cortes por R$ 300") com baixa automatica no atendimento.
--
-- Diferente do clube de assinatura (V12/V27), que e RECORRENTE: o pacote e um CREDITO pago
-- de uma vez, que o cliente vai gastando. Tres tabelas:
--
-- 1. `service_packages` — o catalogo do dono (nome, servico coberto, usos, preco, validade).
-- 2. `customer_packages` — o pacote VENDIDO a um cliente. Nome, servico, preco e validade sao
--    COPIADOS na venda, pelo mesmo motivo da V21: reajustar o catalogo nao pode reescrever o
--    que o cliente ja pagou. A venda entra no caixa ABERTO como movimento de venda.
-- 3. `package_usages` — cada baixa. O saldo (`remaining_uses`) e um contador por conveniencia,
--    mas a historia esta aqui: sem ela, "o sistema comeu um corte meu" nao tem como ser
--    respondido. Uma linha por item de comanda (indice unico) — fechar duas vezes, ou
--    reprocessar, nunca desconta duas vezes.
--
-- A baixa acontece no FECHAMENTO da comanda: o servico coberto sai a R$ 0,00 e um uso e
-- consumido. Por isso as checagens de valor da comanda passam de "> 0" para ">= 0": um corte
-- pago pelo pacote e um item legitimo de valor zero, e uma comanda toda coberta fecha com
-- total zero (sem movimento de caixa — nao entrou dinheiro).

CREATE TABLE service_packages (
    id             UUID PRIMARY KEY,
    barbershop_id  UUID NOT NULL REFERENCES barbershops(id),

    name           VARCHAR(120) NOT NULL,
    -- Servico coberto. NULL = qualquer servico do catalogo.
    service_id     UUID REFERENCES services(id) ON DELETE SET NULL,
    quantity       INTEGER NOT NULL,          -- quantos usos o pacote da
    price          NUMERIC(10,2) NOT NULL,    -- preco do pacote inteiro
    validity_days  INTEGER NOT NULL,          -- prazo para usar, contado da venda

    active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,

    CONSTRAINT chk_package_quantity CHECK (quantity > 0),
    CONSTRAINT chk_package_price CHECK (price > 0),
    CONSTRAINT chk_package_validity CHECK (validity_days > 0)
);

-- Nome unico por barbearia: dois "Pacote 10 cortes" no select da venda seria ambiguo.
CREATE UNIQUE INDEX uq_service_package_name ON service_packages (barbershop_id, LOWER(name));

CREATE TABLE customer_packages (
    id               UUID PRIMARY KEY,
    barbershop_id    UUID NOT NULL REFERENCES barbershops(id),
    customer_id      UUID NOT NULL REFERENCES customers(id),
    package_id       UUID REFERENCES service_packages(id) ON DELETE SET NULL,

    -- Copias congeladas na venda.
    package_name     VARCHAR(120) NOT NULL,
    service_id       UUID,                    -- NULL = qualquer servico
    service_name     VARCHAR(255),
    total_uses       INTEGER NOT NULL,
    remaining_uses   INTEGER NOT NULL,
    price            NUMERIC(10,2) NOT NULL,
    -- price / total_uses, congelado: base da comissao do barbeiro em cada uso.
    unit_value       NUMERIC(10,2) NOT NULL,

    payment_method   VARCHAR(12) NOT NULL,
    cash_movement_id UUID REFERENCES cash_movements(id) ON DELETE SET NULL,
    purchased_at     TIMESTAMP NOT NULL,
    -- Ultimo dia em que o pacote vale (inclusive).
    expires_at       DATE NOT NULL,

    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,

    CONSTRAINT chk_customer_package_uses CHECK (total_uses > 0),
    -- O banco e a ultima barreira contra saldo negativo, mesmo com duas baixas simultaneas.
    CONSTRAINT chk_customer_package_remaining CHECK (remaining_uses >= 0 AND remaining_uses <= total_uses),
    CONSTRAINT chk_customer_package_price CHECK (price > 0)
);

CREATE INDEX idx_customer_packages_customer ON customer_packages (barbershop_id, customer_id);
-- A baixa no fechamento procura so pacotes com saldo.
CREATE INDEX idx_customer_packages_active ON customer_packages (barbershop_id) WHERE remaining_uses > 0;

CREATE TABLE package_usages (
    id                  UUID PRIMARY KEY,
    barbershop_id       UUID NOT NULL REFERENCES barbershops(id),
    customer_package_id UUID NOT NULL REFERENCES customer_packages(id),

    comanda_id          UUID REFERENCES comandas(id) ON DELETE SET NULL,
    comanda_item_id     UUID REFERENCES comanda_items(id) ON DELETE SET NULL,
    -- Agendamento da comanda (V28), quando houver: e por ele que a comissao acha o uso.
    appointment_id      BIGINT REFERENCES appointments(id) ON DELETE SET NULL,
    professional_id     UUID REFERENCES professionals(id) ON DELETE SET NULL,

    service_name        VARCHAR(255) NOT NULL,
    uses                INTEGER NOT NULL,
    unit_value          NUMERIC(10,2) NOT NULL,   -- copia de customer_packages.unit_value
    original_price      NUMERIC(10,2) NOT NULL,   -- preco de tabela que deixou de ser cobrado

    usage_date          DATE NOT NULL,
    used_at             TIMESTAMP NOT NULL,

    CONSTRAINT chk_package_usage_uses CHECK (uses > 0)
);

-- Idempotencia: um item de comanda baixa o pacote UMA vez so.
CREATE UNIQUE INDEX uq_package_usage_item ON package_usages (comanda_item_id) WHERE comanda_item_id IS NOT NULL;
CREATE INDEX idx_package_usages_package ON package_usages (customer_package_id);
CREATE INDEX idx_package_usages_appointment ON package_usages (appointment_id) WHERE appointment_id IS NOT NULL;

-- Servico pago pelo pacote entra na comanda a R$ 0,00 (e a comanda inteira pode fechar zerada).
ALTER TABLE comanda_items DROP CONSTRAINT chk_comanda_item_unit_price;
ALTER TABLE comanda_items DROP CONSTRAINT chk_comanda_item_total;
ALTER TABLE comanda_items ADD CONSTRAINT chk_comanda_item_unit_price CHECK (unit_price >= 0);
ALTER TABLE comanda_items ADD CONSTRAINT chk_comanda_item_total CHECK (total >= 0);

ALTER TABLE comandas DROP CONSTRAINT chk_comanda_total;
ALTER TABLE comandas ADD CONSTRAINT chk_comanda_total CHECK (total IS NULL OR total >= 0);
