-- V51: Console da plataforma (dono do Razor, fora dos tenants).
--
-- 1. platform_users: quem opera a plataforma. Login proprio (cookie e caminho separados do painel
--    das barbearias) com 2FA obrigatorio (TOTP). O segredo do TOTP fica CIFRADO (AES-GCM com
--    RAZOR_SECRETS_KEY); last_totp_step impede reusar o mesmo codigo.
-- 2. platform_settings: linha unica com a chave da OpenAI (cifrada; so os 4 ultimos em claro para
--    exibir) e os modelos. Vazio = vale o que esta no ambiente (OPENAI_API_KEY / OPENAI_MODEL).
-- 3. ai_usage_daily: chamadas, tokens e falhas da IA por barbearia/dia/modelo/tipo — base do
--    custo estimado. Somado em memoria e gravado em lote (upsert), nunca por mensagem.
-- 4. conversation_events: o que deu errado numa conversa (IA fora -> contingencia; agendamento que
--    falhou) — a contingencia nao e gravada como fala do bot, entao sem isto nao ha como filtrar.
-- 5. conversation_flags: conversa marcada para revisao no console.
-- 6. platform_access_log: quem da plataforma leu qual conversa (LGPD) e mudou o que (chave da IA).
-- 7. platform_revenue: "Caixa do Razor" — receita lancada a mao ate existir cobrança.

CREATE TABLE platform_users (
    id               UUID PRIMARY KEY,
    email            VARCHAR(255) NOT NULL UNIQUE,
    name             VARCHAR(120) NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    totp_secret_enc  VARCHAR(255),
    totp_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    last_totp_step   BIGINT,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    token_version    INTEGER      NOT NULL DEFAULT 0,
    last_login_at    TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL
);

CREATE TABLE platform_settings (
    id                     SMALLINT PRIMARY KEY CHECK (id = 1),
    openai_key_enc         TEXT,
    openai_key_last4       VARCHAR(4),
    chat_model             VARCHAR(60),
    escalation_model       VARCHAR(60),
    transcribe_model       VARCHAR(60),
    updated_at             TIMESTAMP,
    updated_by             UUID REFERENCES platform_users(id) ON DELETE SET NULL
);

CREATE TABLE ai_usage_daily (
    barbershop_id  UUID        NOT NULL REFERENCES barbershops(id) ON DELETE CASCADE,
    day            DATE        NOT NULL,
    model          VARCHAR(60) NOT NULL,
    kind           VARCHAR(12) NOT NULL,
    calls          INTEGER     NOT NULL DEFAULT 0,
    failures       INTEGER     NOT NULL DEFAULT 0,
    input_tokens   BIGINT      NOT NULL DEFAULT 0,
    output_tokens  BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (barbershop_id, day, model, kind),
    CONSTRAINT chk_ai_usage_kind CHECK (kind IN ('CHAT', 'TRANSCRIBE', 'VISION'))
);
CREATE INDEX idx_ai_usage_day ON ai_usage_daily (day);

CREATE TABLE conversation_events (
    id             UUID PRIMARY KEY,
    barbershop_id  UUID         NOT NULL REFERENCES barbershops(id) ON DELETE CASCADE,
    customer_phone VARCHAR(20)  NOT NULL,
    kind           VARCHAR(20)  NOT NULL,
    detail         VARCHAR(200),
    created_at     TIMESTAMP    NOT NULL,
    CONSTRAINT chk_conversation_event_kind CHECK (kind IN ('AI_ERROR', 'BOOKING_FAILED'))
);
CREATE INDEX idx_conversation_events_shop ON conversation_events (barbershop_id, created_at DESC);

CREATE TABLE conversation_flags (
    id             UUID PRIMARY KEY,
    barbershop_id  UUID         NOT NULL REFERENCES barbershops(id) ON DELETE CASCADE,
    customer_id    UUID         NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    note           VARCHAR(500),
    created_by     UUID         REFERENCES platform_users(id) ON DELETE SET NULL,
    created_at     TIMESTAMP    NOT NULL,
    resolved_at    TIMESTAMP
);
CREATE UNIQUE INDEX uq_conversation_flag_open ON conversation_flags (customer_id) WHERE resolved_at IS NULL;

CREATE TABLE platform_access_log (
    id                UUID PRIMARY KEY,
    platform_user_id  UUID         REFERENCES platform_users(id) ON DELETE SET NULL,
    action            VARCHAR(40)  NOT NULL,
    barbershop_id     UUID,
    target            VARCHAR(100),
    ip                VARCHAR(64),
    created_at        TIMESTAMP    NOT NULL
);
CREATE INDEX idx_platform_access_log_at ON platform_access_log (created_at DESC);

CREATE TABLE platform_revenue (
    id             UUID PRIMARY KEY,
    month          DATE           NOT NULL,
    barbershop_id  UUID           REFERENCES barbershops(id) ON DELETE SET NULL,
    plan           VARCHAR(40),
    amount         NUMERIC(10, 2) NOT NULL,
    note           VARCHAR(200),
    created_at     TIMESTAMP      NOT NULL,
    CONSTRAINT chk_platform_revenue_month CHECK (EXTRACT(DAY FROM month) = 1),
    CONSTRAINT chk_platform_revenue_amount CHECK (amount >= 0)
);
CREATE INDEX idx_platform_revenue_month ON platform_revenue (month);
