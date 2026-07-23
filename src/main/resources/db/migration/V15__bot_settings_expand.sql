-- V15: amplia as configuracoes do bot por tenant. Ate aqui so havia 5 textos e MUITA coisa
-- continuava hardcoded no codigo — inclusive dados que o bot ENVIA errado num tenant novo:
-- o nome "Barbearia Ficticia" e uma CHAVE PIX FALSA. Tudo abaixo e opcional (NULL) e cai
-- no fallback antigo, entao nenhum tenant existente muda de comportamento.

ALTER TABLE barbershop_settings
    -- Identidade que o bot usa nas mensagens (fim do "Barbearia Ficticia" fixo no codigo)
    ADD COLUMN display_name        VARCHAR(255),

    -- Pagamento (hoje a chave PIX enviada ao cliente e falsa/hardcoded)
    ADD COLUMN pix_key             VARCHAR(255),
    ADD COLUMN pix_copy_paste      TEXT,
    ADD COLUMN payment_info        TEXT,

    -- Funcionamento (hoje 09:00-19:00 fixo no AgendaService)
    ADD COLUMN opening_time        VARCHAR(5),
    ADD COLUMN closing_time        VARCHAR(5),
    ADD COLUMN working_days        VARCHAR(120),

    -- Textos que o bot dispara
    ADD COLUMN welcome_text        TEXT,
    ADD COLUMN reminder_text       TEXT,
    ADD COLUMN review_text         TEXT,
    ADD COLUMN fidelity_reward_text TEXT,
    ADD COLUMN cancellation_policy TEXT,

    -- Personalidade da IA (entra no system prompt)
    ADD COLUMN bot_persona         TEXT;
