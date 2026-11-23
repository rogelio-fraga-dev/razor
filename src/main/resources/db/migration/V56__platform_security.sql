-- V56: acesso ao console da plataforma mais facil e mais seguro (Parte 3c).
--
-- 1. platform_users.totp_pending_enc: a chave do 2FA em configuracao fica SEPARADA da ativa e
--    estavel entre tentativas (antes cada login gerava uma nova e a cadastrada no app deixava de
--    valer). So vira a ativa quando a pessoa confirma com um codigo.
-- 2. platform_users.mfa_deadline_at: prazo de carencia. Sem 2FA, entra so com a senha ate esta
--    data (o console avisa "faltam X dias"); depois o 2FA passa a ser obrigatorio no login.
-- 3. platform_recovery_codes: codigos de uso unico para quem perder o celular (so o hash SHA-256).
-- 4. platform_sessions: cada sessao do console (para expirar por inatividade, listar e encerrar).
-- 5. platform_trusted_devices: "lembrar este aparelho por 30 dias" (pula o codigo no mesmo
--    navegador). So o hash do token fica no banco; o token vai num cookie HttpOnly.

ALTER TABLE platform_users
    ADD COLUMN totp_pending_enc VARCHAR(255),
    ADD COLUMN mfa_deadline_at  TIMESTAMP;

CREATE TABLE platform_recovery_codes (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    code_hash   VARCHAR(64) NOT NULL,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP   NOT NULL
);
CREATE INDEX idx_platform_recovery_user ON platform_recovery_codes (user_id) WHERE used_at IS NULL;

CREATE TABLE platform_sessions (
    id            UUID PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    created_at    TIMESTAMP    NOT NULL,
    last_seen_at  TIMESTAMP    NOT NULL,
    expires_at    TIMESTAMP    NOT NULL,
    ip            VARCHAR(64),
    user_agent    VARCHAR(200),
    method        VARCHAR(20)  NOT NULL,
    revoked_at    TIMESTAMP,
    CONSTRAINT chk_platform_session_method CHECK (method IN ('PASSWORD', 'TOTP', 'RECOVERY', 'TRUSTED_DEVICE'))
);
CREATE INDEX idx_platform_sessions_user ON platform_sessions (user_id, created_at DESC);

CREATE TABLE platform_trusted_devices (
    id            UUID PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    token_hash    VARCHAR(64)  NOT NULL UNIQUE,
    created_at    TIMESTAMP    NOT NULL,
    expires_at    TIMESTAMP    NOT NULL,
    last_used_at  TIMESTAMP,
    ip            VARCHAR(64),
    user_agent    VARCHAR(200),
    revoked_at    TIMESTAMP
);
CREATE INDEX idx_platform_devices_user ON platform_trusted_devices (user_id);

-- Login direto pelo aparelho lembrado tambem entra na trilha.
ALTER TABLE platform_access_log ALTER COLUMN action TYPE VARCHAR(40);
