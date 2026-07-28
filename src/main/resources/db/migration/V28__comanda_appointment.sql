-- V28: Vinculo entre comanda e agendamento.
--
-- Fecha a lacuna registrada na V21/financeiro: servico lancado numa comanda nao entrava no
-- `/reports/financial`, que conta agendamentos. Somar os itens de servico das comandas direto
-- era arriscado — se o dono registrasse o atendimento como agendamento E lancasse na comanda,
-- o mesmo servico seria contado duas vezes.
--
-- O vinculo resolve exatamente isso: com `appointment_id` preenchido, o relatorio sabe que
-- aquela comanda JA esta representada pelo agendamento e nao soma de novo. Sem vinculo
-- (comanda de balcao, cliente que entrou sem marcar), o servico e receita nova e entra.
--
-- Continua opcional de proposito: obrigar todo atendimento a nascer de um agendamento quebraria
-- o cliente que chega sem marcar, que e metade do movimento de uma barbearia de bairro.

ALTER TABLE comandas
    ADD COLUMN appointment_id UUID REFERENCES appointments(id) ON DELETE SET NULL;

-- Uma comanda por agendamento: duas comandas para o mesmo atendimento seria cobranca dupla.
CREATE UNIQUE INDEX uq_comanda_appointment
    ON comandas (appointment_id) WHERE appointment_id IS NOT NULL;

COMMENT ON COLUMN comandas.appointment_id IS
    'Agendamento que originou a comanda. NULL = atendimento de balcao, sem marcacao previa.';
