-- V50: Monitoramento (incidentes de saude por barbearia) e armazenamento de fotos trocavel.
--
-- 1. system_incident: cada problema que o monitor detecta (WhatsApp desconectado, IA falhando,
--    envios falhando, fila de mensagens parada, motor de tarefas sem batimento) vira UM incidente
--    aberto por (barbearia, tipo). Repetir o problema nao cria linha nova: soma em occurrences e
--    atualiza last_seen_at — e isso que permite avisar uma vez so, com intervalo minimo entre
--    avisos (owner_notified_at / operator_notified_at), em vez de uma mensagem por falha.
--    barbershop_id nulo = incidente da plataforma (ex.: motor de tarefas parado vale para todas).
--    detail guarda so um texto classificado por nos ("cota da OpenAI esgotada"), nunca a
--    mensagem crua do provedor — ela pode trazer pedaco de chave ou telefone de cliente.
--
-- 2. site_image.storage_backend: a foto pode morar no proprio Postgres (DB, padrao, como na
--    V45) ou num bucket S3 compativel (S3, opcional por config). Cada linha guarda ONDE os bytes
--    estao: trocar a configuracao nao quebra as fotos antigas, que continuam sendo lidas do banco.
--    Por isso bytes deixa de ser obrigatorio — no S3 a linha e so o registro (dono, tipo, tamanho).

CREATE TABLE system_incident (
    id                    UUID PRIMARY KEY,
    -- Some junto com a barbearia; nulo = incidente da plataforma inteira.
    barbershop_id         UUID         REFERENCES barbershops(id) ON DELETE CASCADE,
    type                  VARCHAR(40)  NOT NULL,
    status                VARCHAR(10)  NOT NULL,
    detail                VARCHAR(300),
    first_seen_at         TIMESTAMP    NOT NULL,
    last_seen_at          TIMESTAMP    NOT NULL,
    resolved_at           TIMESTAMP,
    occurrences           INTEGER      NOT NULL DEFAULT 1,
    owner_notified_at     TIMESTAMP,
    operator_notified_at  TIMESTAMP,

    CONSTRAINT chk_system_incident_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT chk_system_incident_occurrences CHECK (occurrences >= 1),
    CONSTRAINT chk_system_incident_resolved CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);

-- No maximo UM incidente aberto por (barbearia, tipo) — a deduplicacao vale no banco, nao so no
-- codigo. O COALESCE poe os incidentes da plataforma (barbershop_id nulo) na mesma regra.
CREATE UNIQUE INDEX uq_system_incident_open
    ON system_incident (COALESCE(barbershop_id, '00000000-0000-0000-0000-000000000000'::uuid), type)
    WHERE status = 'OPEN';

-- Historico do painel: os incidentes da barbearia, do mais recente para o mais antigo.
CREATE INDEX idx_system_incident_shop ON system_incident (barbershop_id, last_seen_at DESC);

COMMENT ON TABLE system_incident IS
    'Incidentes de saude (WhatsApp, IA, envios, fila, motor de tarefas) por barbearia; um aberto por tipo.';

ALTER TABLE site_image ADD COLUMN storage_backend VARCHAR(10) NOT NULL DEFAULT 'DB';
ALTER TABLE site_image ADD CONSTRAINT chk_site_image_backend CHECK (storage_backend IN ('DB', 'S3'));
ALTER TABLE site_image ALTER COLUMN bytes DROP NOT NULL;
-- Foto no banco precisa dos bytes; no S3 a linha nao os carrega.
ALTER TABLE site_image ADD CONSTRAINT chk_site_image_bytes
    CHECK (storage_backend <> 'DB' OR bytes IS NOT NULL);
