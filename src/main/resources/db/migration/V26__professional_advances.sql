-- V26: Vales e adiantamentos do barbeiro, descontados na comissao.
--
-- Fecha o ciclo do acerto (V25): o barbeiro pede R$ 100 no meio da quinzena, e no dia do
-- pagamento esse valor tem que sair do que ele tem a receber. Sem registro, ou o dono perde o
-- dinheiro ou depende da memoria dos dois — que nunca batem.
--
-- DECISOES:
--
-- 1. O vale pertence a uma DATA, nao a um vale "em aberto" com maquina de estados. Na apuracao
--    de um periodo, descontam-se os vales daquele intervalo. Como as janelas de acerto nao se
--    sobrepoem na pratica (quinzena, mes), cada vale entra em exatamente um acerto — e nao
--    existe estado para dessincronizar.
--
-- 2. Sair do caixa e OPCIONAL, igual ao acerto: vale em especie vira sangria; vale por PIX nao
--    mexe na conferencia da gaveta.

CREATE TABLE professional_advances (
    id               UUID PRIMARY KEY,
    barbershop_id    UUID NOT NULL REFERENCES barbershops(id),
    professional_id  UUID NOT NULL REFERENCES professionals(id),

    amount           NUMERIC(12,2) NOT NULL,
    -- Data que define em qual acerto este vale sera descontado.
    advance_date     DATE NOT NULL,
    notes            VARCHAR(255),

    -- Sangria gerada quando o vale sai da gaveta. NULL = adiantado por fora do caixa.
    cash_movement_id UUID REFERENCES cash_movements(id) ON DELETE SET NULL,

    granted_by_name  VARCHAR(255),
    created_at       TIMESTAMP NOT NULL,

    CONSTRAINT chk_advance_amount CHECK (amount > 0)
);

-- "Quanto de vale o Rafael pegou nesta quinzena?" — a consulta do acerto.
CREATE INDEX idx_advances_prof_date ON professional_advances (professional_id, advance_date);
CREATE INDEX idx_advances_shop_date ON professional_advances (barbershop_id, advance_date DESC);
