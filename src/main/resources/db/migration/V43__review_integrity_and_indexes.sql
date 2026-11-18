-- V43: rodada de revisao 2026-11-18 — integridade da agenda, indices de consulta e tenant
-- consistente nas tabelas filhas.
--
-- 1) REDE DE SEGURANCA CONTRA HORARIO DUPLO
--    A agenda agora serializa as gravacoes por barbearia (pg_advisory_xact_lock, ver BookingLock),
--    mas o banco e a ultima barreira: o mesmo barbeiro nao pode ter dois agendamentos COMECANDO no
--    mesmo dia/hora. Falta (NO_SHOW) libera a cadeira — fica fora do indice. Agendamento sem
--    barbeiro ou sem hora (legado) tambem fica fora. A aplicacao traduz a violacao para 409
--    "horario ocupado".
--
--    Se ja existirem duplicados, a migracao PARA com a lista: escolher qual dos dois horarios
--    vale e decisao da barbearia, nao de um script.
DO $$
DECLARE
    dup RECORD;
    msg TEXT := '';
BEGIN
    FOR dup IN
        SELECT barbershop_id, professional_id, appointment_date, appointment_time,
               string_agg(id::text, ', ' ORDER BY id) AS ids
          FROM appointments
         WHERE professional_id IS NOT NULL
           AND appointment_time IS NOT NULL
           AND attendance_status IS DISTINCT FROM 'NO_SHOW'
         GROUP BY barbershop_id, professional_id, appointment_date, appointment_time
        HAVING count(*) > 1
         LIMIT 50
    LOOP
        msg := msg || format(E'\n  barbearia %s, barbeiro %s, %s %s: agendamentos %s',
                             dup.barbershop_id, dup.professional_id, dup.appointment_date,
                             dup.appointment_time, dup.ids);
    END LOOP;
    IF msg <> '' THEN
        RAISE EXCEPTION 'V43: existem agendamentos duplicados no mesmo barbeiro/dia/hora. Cancele ou remarque um de cada par (ou marque como NO_SHOW) e rode de novo:%', msg;
    END IF;
END $$;

CREATE UNIQUE INDEX uq_appointments_slot
    ON appointments (barbershop_id, professional_id, appointment_date, appointment_time)
    WHERE professional_id IS NOT NULL
      AND appointment_time IS NOT NULL
      AND attendance_status IS DISTINCT FROM 'NO_SHOW';

-- 2) COMPARECIMENTO so com os valores do enum (AttendanceStatus).
ALTER TABLE appointments
    ADD CONSTRAINT chk_appointment_attendance_status
        CHECK (attendance_status IS NULL OR attendance_status IN ('ATTENDED', 'NO_SHOW'));

-- 3) INDICES DA AGENDA, no formato das consultas de verdade.
--    Grade/ocupacao do dia (bot, site, painel): barbearia + dia (+ hora para ordenar).
CREATE INDEX idx_appointments_shop_date_time
    ON appointments (barbershop_id, appointment_date, appointment_time);
--    Agenda do barbeiro no dia (validacao do painel, atribuicao, comissao por periodo).
--    Substitui o (barbershop_id, professional_id), que obrigava a varrer todos os dias dele.
CREATE INDEX idx_appointments_shop_pro_date
    ON appointments (barbershop_id, professional_id, appointment_date);
DROP INDEX IF EXISTS idx_appointments_professional;
--    Horarios do cliente (limite por cliente, "seus horarios", teto do site). Substitui o indice
--    so por telefone, que ignorava a barbearia (multi-tenant) e a data.
CREATE INDEX idx_appointments_shop_phone_date_time
    ON appointments (barbershop_id, customer_phone, appointment_date, appointment_time);
DROP INDEX IF EXISTS idx_appointments_phone;

-- 4) TAREFAS PENDENTES por telefone (cancelar lembretes do cliente). Parcial: a tabela acumula
--    milhares de COMPLETED/CANCELLED, e so as pendentes interessam.
CREATE INDEX idx_scheduled_tasks_pending_phone
    ON scheduled_tasks (barbershop_id, customer_phone)
    WHERE status = 'PENDING';

-- 5) INDICES DE FK das tabelas de pacote (V34). Sem eles, apagar/atualizar a linha-mae (cliente,
--    servico, comanda, barbeiro, movimento de caixa) varre a tabela filha inteira para conferir.
CREATE INDEX idx_customer_packages_customer_id ON customer_packages (customer_id);
CREATE INDEX idx_customer_packages_package ON customer_packages (package_id) WHERE package_id IS NOT NULL;
CREATE INDEX idx_customer_packages_cash_movement ON customer_packages (cash_movement_id) WHERE cash_movement_id IS NOT NULL;
CREATE INDEX idx_service_packages_service ON service_packages (service_id) WHERE service_id IS NOT NULL;
CREATE INDEX idx_package_usages_comanda ON package_usages (comanda_id) WHERE comanda_id IS NOT NULL;
CREATE INDEX idx_package_usages_professional ON package_usages (professional_id) WHERE professional_id IS NOT NULL;

-- 6) VALOR UNITARIO DOS PACOTES. O ultimo uso vale o resto do preco (preco - unitario*(usos-1)),
--    entao nenhum uso passa do preco nem fica negativo.
ALTER TABLE customer_packages
    ADD CONSTRAINT chk_customer_package_unit_value CHECK (unit_value >= 0 AND unit_value <= price);
ALTER TABLE package_usages
    ADD CONSTRAINT chk_package_usage_unit_value CHECK (unit_value >= 0),
    ADD CONSTRAINT chk_package_usage_original_price CHECK (original_price >= 0);

-- 7) TENANT CONSISTENTE NAS TABELAS FILHAS.
--    As FKs simples (customer_id -> customers.id) deixavam um pacote da barbearia A apontar para o
--    cliente da barbearia B. Com FK composta (filho.cliente, filho.barbearia) ->
--    (cliente.id, cliente.barbearia), o banco garante que os dois lados sao da mesma barbearia.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM customer_packages cp JOIN customers c ON c.id = cp.customer_id
                WHERE c.barbershop_id <> cp.barbershop_id) THEN
        RAISE EXCEPTION 'V43: ha pacotes vendidos (customer_packages) com cliente de outra barbearia.';
    END IF;
    IF EXISTS (SELECT 1 FROM package_usages pu JOIN customer_packages cp ON cp.id = pu.customer_package_id
                WHERE cp.barbershop_id <> pu.barbershop_id) THEN
        RAISE EXCEPTION 'V43: ha baixas de pacote (package_usages) de outra barbearia.';
    END IF;
    IF EXISTS (SELECT 1 FROM customer_tag_links l
                 JOIN customers c ON c.id = l.customer_id
                 JOIN customer_tags t ON t.id = l.tag_id
                WHERE c.barbershop_id <> t.barbershop_id) THEN
        RAISE EXCEPTION 'V43: ha vinculos cliente x tag (customer_tag_links) entre barbearias diferentes.';
    END IF;
END $$;

ALTER TABLE customers ADD CONSTRAINT uq_customers_id_shop UNIQUE (id, barbershop_id);
ALTER TABLE customer_packages ADD CONSTRAINT uq_customer_packages_id_shop UNIQUE (id, barbershop_id);
ALTER TABLE customer_tags ADD CONSTRAINT uq_customer_tags_id_shop UNIQUE (id, barbershop_id);

-- Pacote vendido -> cliente (continua RESTRICT: pacote e dinheiro pago e historico de baixas).
ALTER TABLE customer_packages DROP CONSTRAINT IF EXISTS customer_packages_customer_id_fkey;
ALTER TABLE customer_packages
    ADD CONSTRAINT fk_customer_packages_customer_shop
        FOREIGN KEY (customer_id, barbershop_id) REFERENCES customers (id, barbershop_id);

-- Baixa -> pacote vendido.
ALTER TABLE package_usages DROP CONSTRAINT IF EXISTS package_usages_customer_package_id_fkey;
ALTER TABLE package_usages
    ADD CONSTRAINT fk_package_usages_package_shop
        FOREIGN KEY (customer_package_id, barbershop_id) REFERENCES customer_packages (id, barbershop_id);

-- Vinculo cliente x tag ganha a barbearia, preenchida pelo cliente.
ALTER TABLE customer_tag_links ADD COLUMN barbershop_id UUID;
UPDATE customer_tag_links l SET barbershop_id = c.barbershop_id FROM customers c WHERE c.id = l.customer_id;
ALTER TABLE customer_tag_links ALTER COLUMN barbershop_id SET NOT NULL;

ALTER TABLE customer_tag_links DROP CONSTRAINT IF EXISTS customer_tag_links_customer_id_fkey;
ALTER TABLE customer_tag_links DROP CONSTRAINT IF EXISTS customer_tag_links_tag_id_fkey;
ALTER TABLE customer_tag_links
    ADD CONSTRAINT fk_customer_tag_links_customer_shop
        FOREIGN KEY (customer_id, barbershop_id) REFERENCES customers (id, barbershop_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_customer_tag_links_tag_shop
        FOREIGN KEY (tag_id, barbershop_id) REFERENCES customer_tags (id, barbershop_id) ON DELETE CASCADE;
