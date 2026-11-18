-- V40: instancia de WhatsApp unica entre barbearias.
--
-- barbershops.whatsapp_instance decide para qual tenant o webhook da Evolution entrega as
-- mensagens (TenantResolver). Sem UNIQUE, duas barbearias com o mesmo nome de instancia
-- dividiam o trafego — e o painel deixava o dono escolher qualquer nome, inclusive o de outra
-- barbearia (sequestro das conversas dela). O painel nao edita mais esse campo; este indice e
-- a garantia no banco.
--
-- Dados existentes: texto em branco vira NULL (NULL nao conflita no UNIQUE). Se houver nomes
-- repetidos, a barbearia MAIS ANTIGA (created_at, depois id) fica com o nome e as demais
-- voltam para NULL — ao conectar pelo painel elas recebem um nome derivado do proprio slug.
-- Cada ajuste sai no log do Flyway (NOTICE) para conferencia.

UPDATE barbershops SET whatsapp_instance = NULL
 WHERE whatsapp_instance IS NOT NULL AND btrim(whatsapp_instance) = '';

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT id, whatsapp_instance
          FROM (SELECT id, whatsapp_instance,
                       row_number() OVER (PARTITION BY whatsapp_instance
                                          ORDER BY created_at NULLS LAST, id) AS rn
                  FROM barbershops
                 WHERE whatsapp_instance IS NOT NULL) d
         WHERE d.rn > 1
    LOOP
        RAISE NOTICE 'V40: instancia de WhatsApp "%" duplicada; removida da barbearia % (a mais antiga mantem o nome)',
            r.whatsapp_instance, r.id;
        UPDATE barbershops SET whatsapp_instance = NULL WHERE id = r.id;
    END LOOP;
END $$;

CREATE UNIQUE INDEX ux_barbershops_whatsapp_instance
    ON barbershops (whatsapp_instance)
 WHERE whatsapp_instance IS NOT NULL;
