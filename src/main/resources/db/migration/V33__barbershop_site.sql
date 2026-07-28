-- V33: Site white-label da barbearia.
--
-- Todo plano inclui um site proprio. O modelo de negocio (PLATFORM.md) e: o CODIGO e nosso e
-- centralizado — o tenant escolhe um dos tres templates e sobe apenas CONTEUDO. Assim uma
-- correcao de bug ou uma melhoria de SEO chega a todas as barbearias de uma vez, em vez de
-- exigir mexer em N sites.
--
-- DECISOES:
--
-- 1. O dono escolhe UMA cor de destaque, nunca cor de texto ou de fundo. Cada template ja vem
--    com as duas paletas (clara e escura) testadas em contraste AA, derivadas dessa cor. Deixar
--    escolher todas as cores garante que uma parte dos sites saia ilegivel — e o suporte disso
--    volta para nos.
--
-- 2. `theme_mode` AUTO e o padrao: o site segue o aparelho do visitante. Nao e indecisao — e a
--    escolha certa para quem nao tem opiniao formada, e quem tem escolhe LIGHT ou DARK.
--
-- 3. Trocar de template NAO perde conteudo. Por isso os textos vivem em colunas proprias, e nao
--    num JSON com formato por template: os tres consomem os mesmos campos e mostram cada um do
--    seu jeito.
--
-- 4. `published` separado do registro: o dono monta o site com calma e publica quando quiser.
--    Sem isso, salvar um rascunho ja jogaria no ar um site pela metade, com o endereco impresso
--    no cartao dele.

CREATE TABLE barbershop_site (
    barbershop_id    UUID PRIMARY KEY REFERENCES barbershops(id) ON DELETE CASCADE,

    -- CLASSICO | ESTUDIO | VITRINE. Texto validado na aplicacao (enum Java), nao tipo do
    -- Postgres: acrescentar template nao pode exigir migracao.
    template_id      VARCHAR(20)  NOT NULL DEFAULT 'CLASSICO',
    -- LIGHT | DARK | AUTO
    theme_mode       VARCHAR(10)  NOT NULL DEFAULT 'AUTO',
    -- Hex da cor de destaque. Tudo o mais e derivado dela pelo template.
    accent_color     VARCHAR(9)   NOT NULL DEFAULT '#3E63FF',

    headline         VARCHAR(120),
    subheadline      VARCHAR(255),
    about_text       TEXT,
    -- Imagens como URL: o storage de objetos ainda nao existe (ver ROADMAP, decisao da foto do
    -- barbeiro). Quando entrar, e aqui que a URL passa a apontar.
    logo_url         TEXT,
    hero_image_url   TEXT,
    gallery_urls     TEXT,  -- URLs separadas por quebra de linha
    whatsapp_number  VARCHAR(20),
    instagram_url    TEXT,
    maps_url         TEXT,

    show_team        BOOLEAN NOT NULL DEFAULT TRUE,
    show_plans       BOOLEAN NOT NULL DEFAULT TRUE,
    show_prices      BOOLEAN NOT NULL DEFAULT TRUE,

    -- Rascunho ate o dono publicar. Site nao publicado responde 404 no endereco publico.
    published        BOOLEAN NOT NULL DEFAULT FALSE,
    published_at     TIMESTAMP,

    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL,

    CONSTRAINT chk_site_template CHECK (template_id IN ('CLASSICO', 'ESTUDIO', 'VITRINE')),
    CONSTRAINT chk_site_theme CHECK (theme_mode IN ('LIGHT', 'DARK', 'AUTO')),
    -- Hex de 6 digitos, com ou sem alfa. Cor invalida quebraria o CSS do site inteiro, e o erro
    -- so apareceria para o visitante.
    CONSTRAINT chk_site_accent CHECK (accent_color ~* '^#[0-9a-f]{6}([0-9a-f]{2})?$')
);

COMMENT ON TABLE barbershop_site IS
    'Conteudo do site white-label. Uma linha por barbearia; o codigo do template e nosso.';
