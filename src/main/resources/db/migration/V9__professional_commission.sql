-- V9: Comissoes por profissional (Fase 2 — paridade de gestao). Percentual de comissao do
-- barbeiro sobre o valor dos servicos que ele atende. Default 0: cada barbearia configura a
-- taxa dos seus barbeiros (via ProfessionalService.updateCommission / painel futuro).

ALTER TABLE professionals ADD COLUMN commission_percent NUMERIC(5,2) NOT NULL DEFAULT 0;
