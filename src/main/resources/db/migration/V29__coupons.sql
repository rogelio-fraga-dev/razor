-- V29: Cupons de desconto e fidelidade de verdade.
--
-- PROBLEMA QUE ISTO RESOLVE: o cartao fidelidade ja mandava um codigo ao cliente
-- ("FIDELIDADE10-A3F9C") ao completar 10 pontos, mas esse codigo NUNCA ERA GRAVADO. O cliente
-- chegava com um cupom que nenhum sistema conhecia: o barbeiro nao tinha como validar, nada
-- impedia usar duas vezes e nao havia registro de quanto a barbearia deu de desconto.
--
-- DECISOES:
--
-- 1. O cupom pode ser de PERCENTUAL ou VALOR FIXO. Fidelidade costuma dar percentual; promocao
--    de campanha costuma dar valor fixo ("R$ 10 off").
--
-- 2. `customer_phone` preenchido = cupom pessoal (o da fidelidade, que e daquele cliente);
--    nulo = cupom aberto de campanha, que qualquer um usa.
--
-- 3. O uso vira linha em `coupon_redemptions`, nao so um contador. Sem historico nao da para
--    responder "quanto demos de desconto neste mes?" — que e a pergunta que o dono faz.

CREATE TABLE coupons (
    id             UUID PRIMARY KEY,
    barbershop_id  UUID NOT NULL REFERENCES barbershops(id),

    code           VARCHAR(40) NOT NULL,
    description    VARCHAR(255),

    -- PERCENT | FIXED
    discount_type  VARCHAR(10) NOT NULL,
    discount_value NUMERIC(10,2) NOT NULL,

    -- MANUAL (criado pelo dono) | FIDELIDADE (emitido pelo cartao fidelidade)
    origin         VARCHAR(12) NOT NULL DEFAULT 'MANUAL',

    -- Cupom pessoal: so este telefone usa. NULL = campanha aberta.
    customer_phone VARCHAR(20),

    valid_from     DATE,
    valid_until    DATE,
    -- Quantas vezes pode ser usado no total. NULL = ilimitado.
    max_uses       INTEGER,
    used_count     INTEGER NOT NULL DEFAULT 0,
    -- Valor minimo da conta para o cupom valer. NULL = sem minimo.
    min_amount     NUMERIC(12,2),

    active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,

    CONSTRAINT chk_coupon_discount_type CHECK (discount_type IN ('PERCENT', 'FIXED')),
    CONSTRAINT chk_coupon_origin CHECK (origin IN ('MANUAL', 'FIDELIDADE')),
    CONSTRAINT chk_coupon_value_positive CHECK (discount_value > 0),
    -- Percentual acima de 100 nao existe; valor fixo nao tem teto aqui (o desconto e limitado
    -- ao total da conta na hora de aplicar).
    CONSTRAINT chk_coupon_percent_range CHECK (discount_type <> 'PERCENT' OR discount_value <= 100),
    CONSTRAINT chk_coupon_max_uses CHECK (max_uses IS NULL OR max_uses > 0),
    CONSTRAINT chk_coupon_period CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from)
);

-- Codigo unico por barbearia (case-insensitive): dois cupons "VOLTA10" seria ambiguo no caixa.
CREATE UNIQUE INDEX uq_coupon_code ON coupons (barbershop_id, UPPER(code));
CREATE INDEX idx_coupons_shop_active ON coupons (barbershop_id) WHERE active;

CREATE TABLE coupon_redemptions (
    id            UUID PRIMARY KEY,
    barbershop_id UUID NOT NULL REFERENCES barbershops(id),
    coupon_id     UUID NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,

    -- Comanda em que o cupom foi aplicado.
    comanda_id    UUID REFERENCES comandas(id) ON DELETE SET NULL,
    customer_phone VARCHAR(20),

    -- Quanto de desconto o cupom deu de fato, congelado no uso.
    amount        NUMERIC(12,2) NOT NULL,
    redeemed_at   TIMESTAMP NOT NULL,
    redemption_date DATE NOT NULL,

    CONSTRAINT chk_redemption_amount CHECK (amount > 0)
);

CREATE INDEX idx_redemptions_coupon ON coupon_redemptions (coupon_id);
CREATE INDEX idx_redemptions_shop_date ON coupon_redemptions (barbershop_id, redemption_date DESC);

-- A comanda guarda o desconto aplicado, para o recibo e o relatorio baterem.
ALTER TABLE comandas
    ADD COLUMN coupon_id       UUID REFERENCES coupons(id) ON DELETE SET NULL,
    ADD COLUMN discount_amount NUMERIC(12,2);

ALTER TABLE comandas
    ADD CONSTRAINT chk_comanda_discount CHECK (discount_amount IS NULL OR discount_amount >= 0);
