-- V41: Versao do token dos usuarios do painel.
--
-- O JWT vale 12h e, ate aqui, era aceito so pela assinatura: usuario desativado ou com a senha
-- trocada continuava entrando ate o token vencer. Agora o token carrega a versao vigente
-- (claim "ver") e o filtro confere contra esta coluna (com cache curto). Trocar a senha
-- incrementa a versao e derruba todas as sessoes anteriores daquele usuario.
--
-- DEFAULT 0 casa com tokens emitidos antes desta migracao (sem a claim, lidos como 0): ninguem
-- e deslogado pelo deploy.

ALTER TABLE tenant_users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
