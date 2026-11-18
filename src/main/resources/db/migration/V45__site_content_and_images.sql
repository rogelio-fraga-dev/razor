-- V45: Site 100% editavel pelo dono — textos por secao e fotos hospedadas por nos.
--
-- 1. content_json (TEXT): o "olho", o titulo, o texto e a visibilidade de cada secao do site,
--    mais o texto do botao de agendar e a intensidade das animacoes. Fica num JSON, e nao em
--    dezenas de colunas, porque e um conjunto que cresce junto com os templates (secao nova nao
--    pode exigir migracao). Continua valendo a regra da V33: o formato e o MESMO para os tres
--    templates — trocar de modelo nao perde texto. Quem garante o formato e o SiteContentPolicy
--    (chaves conhecidas, tamanhos, sem caractere de controle), antes de gravar. TEXT e nao JSONB
--    porque o banco nunca consulta dentro dele: so guarda e devolve.
--
-- 2. site_image: o dono sobe a foto pelo painel em vez de colar link de outro site. A imagem
--    fica no proprio Postgres (BYTEA) porque o storage de objetos ainda nao existe e o volume e
--    pequeno: no maximo ~40 fotos de ate 2MB por barbearia, ja redimensionadas no navegador.
--    O endereco publico e /api/site/img/<id>; o id e um UUID aleatorio, entao nao da para
--    "andar" pelas fotos das outras barbearias trocando um numero na URL.

ALTER TABLE barbershop_site ADD COLUMN content_json TEXT;

CREATE TABLE site_image (
    id             UUID PRIMARY KEY,
    -- A foto e da barbearia: some junto com ela.
    barbershop_id  UUID         NOT NULL REFERENCES barbershops(id) ON DELETE CASCADE,
    -- Tipo DETECTADO pelos bytes do arquivo no upload, nunca o declarado pelo navegador. SVG
    -- fica de fora de proposito: carrega script e seria servido na mesma origem do painel.
    content_type   VARCHAR(20)  NOT NULL,
    bytes          BYTEA        NOT NULL,
    size_bytes     INTEGER      NOT NULL,
    created_at     TIMESTAMP    NOT NULL,

    CONSTRAINT chk_site_image_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT chk_site_image_size CHECK (size_bytes > 0 AND size_bytes <= 2097152)
);

-- Listagem e contagem por barbearia (teto de fotos) sem varrer a tabela.
CREATE INDEX idx_site_image_shop ON site_image (barbershop_id, created_at);

COMMENT ON TABLE site_image IS
    'Fotos do site white-label enviadas pelo dono. Publicas por UUID em /api/site/img/{id}.';
