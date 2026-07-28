-- V25: Comissoes pagas — o historico de quitacao com o barbeiro.
--
-- Ate aqui o sistema calculava quanto cada barbeiro tinha a receber, mas nao guardava o que ja
-- havia sido pago. Sem isso, no fim do mes ninguem sabe dizer se a quinzena passada foi acertada
-- — e pagar duas vezes e um erro caro e constrangedor.
--
-- DECISOES:
--
-- 1. O pagamento guarda o PERIODO a que se refere, nao um vinculo com cada atendimento. Comissao
--    e apurada por intervalo ("primeira quinzena de julho"), e e assim que o dono paga.
--
-- 2. O valor e COPIADO no momento do pagamento. Se um agendamento antigo for corrigido depois,
--    o recibo do que foi efetivamente pago nao pode mudar sozinho.
--
-- 3. Sair do caixa e OPCIONAL (`cash_movement_id` nulavel). Pagar por PIX ou transferencia nao
--    tira dinheiro da gaveta; forcar uma sangria distorceria a conferencia do fechamento de quem
--    nao paga em especie.

CREATE TABLE commission_payments (
    id               UUID PRIMARY KEY,
    barbershop_id    UUID NOT NULL REFERENCES barbershops(id),
    professional_id  UUID NOT NULL REFERENCES professionals(id),

    -- Intervalo de apuracao a que este pagamento se refere (inclusive nas duas pontas).
    period_start     DATE NOT NULL,
    period_end       DATE NOT NULL,

    amount           NUMERIC(12,2) NOT NULL,
    notes            VARCHAR(255),

    -- Sangria gerada quando o dono paga em dinheiro da gaveta. NULL = pago por fora do caixa.
    cash_movement_id UUID REFERENCES cash_movements(id) ON DELETE SET NULL,

    paid_by_name     VARCHAR(255),
    paid_at          TIMESTAMP NOT NULL,
    payment_date     DATE NOT NULL,
    created_at       TIMESTAMP,

    CONSTRAINT chk_commission_payment_amount CHECK (amount > 0),
    CONSTRAINT chk_commission_payment_period CHECK (period_end >= period_start)
);

-- "Quanto ja paguei ao Rafael neste intervalo?" — a consulta que evita pagar duas vezes.
CREATE INDEX idx_commission_payments_prof_period
    ON commission_payments (professional_id, period_start, period_end);
CREATE INDEX idx_commission_payments_shop_date
    ON commission_payments (barbershop_id, payment_date DESC);
