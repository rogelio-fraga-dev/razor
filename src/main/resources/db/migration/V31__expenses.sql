-- V31: Contas a pagar (despesas da barbearia).
--
-- Ate aqui o sistema so sabia o que ENTRA. "Fechar o mes" com metade da conta e pior que nao
-- fechar: o dono olha R$ 18 mil de faturamento e acha que teve R$ 18 mil de resultado. Aluguel,
-- energia, produto de revenda e imposto sao o outro lado, e sem eles nao existe DRE — so receita
-- com outro nome.
--
-- DECISOES:
--
-- 1. "A pagar" x "pago" e DERIVADO de paid_at IS NULL, nao um enum de status. Mesmo criterio do
--    vale (V26): status paralelo ao fato e mais uma coisa para dessincronizar. Aqui a pergunta
--    "esta paga?" tem uma resposta so, e ela e a data em que o dinheiro saiu.
--
-- 2. Vencimento e pagamento sao DATAS SEPARADAS, de proposito. O aluguel vence dia 5 e as vezes
--    e pago dia 8: o DRE do mes precisa da competencia (due_date), e a conferencia do caixa
--    precisa do desembolso (paid_at). Uma coluna so obrigaria a escolher qual dos dois mentir.
--
-- 3. Sair do caixa e OPCIONAL, igual ao acerto (V25) e ao vale (V26): despesa paga em especie
--    vira sangria; boleto ou PIX da conta da barbearia nao mexe na conferencia da gaveta.
--
-- 4. `recurring` e um MARCADOR, nao um gerador automatico. Criar as parcelas futuras sozinho
--    encheria a tela de divida que ainda nao existe e que ninguem conferiu. A repeticao e um
--    comando explicito e idempotente do dono (mesmo criterio de POST /plans/suggested).

CREATE TABLE expenses (
    id               UUID PRIMARY KEY,
    barbershop_id    UUID NOT NULL REFERENCES barbershops(id),

    description      VARCHAR(255) NOT NULL,
    -- Categoria como texto validado na aplicacao (enum Java), nao tipo do Postgres: acrescentar
    -- categoria nao pode exigir migracao, e o conjunto muda com o negocio.
    category         VARCHAR(40) NOT NULL,
    amount           NUMERIC(12,2) NOT NULL,

    -- Competencia: a qual periodo a despesa pertence.
    due_date         DATE NOT NULL,
    -- Desembolso. NULL = ainda nao paga (e o que faz a conta aparecer em "a pagar").
    paid_at          DATE,

    -- "Isso se repete todo mes" — usado para gerar o mes seguinte sob comando do dono.
    recurring        BOOLEAN NOT NULL DEFAULT FALSE,
    notes            VARCHAR(255),

    -- Sangria gerada quando a despesa sai da gaveta. NULL = paga por fora do caixa.
    cash_movement_id UUID REFERENCES cash_movements(id) ON DELETE SET NULL,

    created_at       TIMESTAMP NOT NULL,

    CONSTRAINT chk_expense_amount CHECK (amount > 0)
    -- Sem trava entre paid_at e due_date de proposito: adiantar boleto e pagar atrasado sao os
    -- dois normais, e qualquer janela que se escolhesse aqui seria numero inventado.
);

-- "Quanto tenho de despesa neste periodo?" — a consulta do DRE, por competencia.
CREATE INDEX idx_expenses_shop_due ON expenses (barbershop_id, due_date DESC);

-- "O que esta em aberto?" — indice parcial: so as nao pagas, que sao a minoria e a pergunta
-- que o dono faz todo dia.
CREATE INDEX idx_expenses_shop_unpaid ON expenses (barbershop_id, due_date) WHERE paid_at IS NULL;

-- Evita a despesa recorrente duplicada ao gerar o mes seguinte duas vezes. E o que torna o
-- comando de repeticao idempotente no banco, e nao so na aplicacao.
CREATE UNIQUE INDEX uq_expense_recurring_month
    ON expenses (barbershop_id, description, due_date) WHERE recurring = TRUE;
