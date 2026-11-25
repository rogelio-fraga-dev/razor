-- =============================================================================================
-- RAZOR · complemento do seed de demonstracao (Parte 6 — dados para os videos)
-- =============================================================================================
--
-- Roda DEPOIS do scripts/seed-demo.sql e cobre o que ele nao cobre: pacotes pre-pagos,
-- avaliacoes, etiquetas, o cartao "O que a IA fez por voce" (origem dos agendamentos,
-- mensagens do bot, confirmacoes) e o uso da IA visto no console da plataforma.
--
--   docker compose exec -T postgres psql -U barberbot_user -d barberbot_db < scripts/seed-demo.sql
--   docker compose exec -T postgres psql -U barberbot_user -d barberbot_db < scripts/seed-demo-extra.sql
--
-- Tudo e do tenant demo, com nomes e telefones ficticios. Re-executavel: apaga o que cria.
-- =============================================================================================

DO $extra$
DECLARE
    v_shop  uuid := 'dededede-0000-4000-8000-000000000001';
    v_prof  uuid[] := ARRAY['dededede-0000-4000-8000-000000000031'::uuid,
                            'dededede-0000-4000-8000-000000000032'::uuid,
                            'dededede-0000-4000-8000-000000000033'::uuid];
    v_pk_corte4  uuid := 'dededede-0000-4000-8000-000000000301';
    v_pk_corte10 uuid := 'dededede-0000-4000-8000-000000000302';
    v_pk_barba5  uuid := 'dededede-0000-4000-8000-000000000303';
    v_tag  uuid[] := ARRAY['dededede-0000-4000-8000-000000000401'::uuid,
                           'dededede-0000-4000-8000-000000000402'::uuid,
                           'dededede-0000-4000-8000-000000000403'::uuid,
                           'dededede-0000-4000-8000-000000000404'::uuid];
    r      record;
    v_i    int := 0;
    v_cp   uuid;
    v_ts   timestamp;
    v_comments text[] := ARRAY[
        'Melhor degradê da região, saí outro.',
        'Atendimento rápido e o café tava ótimo.',
        'Marquei pelo WhatsApp em 1 minuto, fácil demais.',
        'Barba na toalha quente é outro nível.',
        'Pontual, sem fila. Voltarei.',
        'O Bruno entendeu exatamente o corte que eu queria.',
        'Ambiente top, preço justo.',
        NULL, NULL, NULL];
BEGIN
    -- ---- limpeza do que este script cria -----------------------------------------------------
    DELETE FROM package_usages    WHERE barbershop_id = v_shop;
    DELETE FROM customer_packages WHERE barbershop_id = v_shop;
    DELETE FROM service_packages  WHERE barbershop_id = v_shop;
    DELETE FROM customer_reviews  WHERE barbershop_id = v_shop;
    DELETE FROM customer_tag_links WHERE barbershop_id = v_shop;
    DELETE FROM customer_tags     WHERE barbershop_id = v_shop;
    DELETE FROM ai_impact_events  WHERE barbershop_id = v_shop;
    DELETE FROM ai_usage_daily    WHERE barbershop_id = v_shop;
    DELETE FROM conversation_events WHERE barbershop_id = v_shop;
    DELETE FROM interactions      WHERE barbershop_id = v_shop AND type = 'BOT';

    -- ---- origem dos agendamentos: nem tudo e do bot -----------------------------------------
    -- ~60% WhatsApp (bot), ~22% site, ~18% painel (recepcao). Deterministico pelo id.
    UPDATE appointments SET source = CASE
             WHEN id % 9 IN (1, 5) THEN 'SITE'
             WHEN id % 11 IN (2, 7) THEN 'PAINEL'
             ELSE 'BOT' END
     WHERE barbershop_id = v_shop;

    -- ---- pacotes pre-pagos --------------------------------------------------------------------
    INSERT INTO service_packages (id, barbershop_id, name, service_id, quantity, price, validity_days, active, created_at, updated_at)
    VALUES (v_pk_corte4,  v_shop, '4 cortes no mês',  'dededede-0000-4000-8000-000000000041', 4,  160.00, 45,  true, now() - interval '90 days', now()),
           (v_pk_corte10, v_shop, '10 cortes',        'dededede-0000-4000-8000-000000000041', 10, 380.00, 180, true, now() - interval '90 days', now()),
           (v_pk_barba5,  v_shop, '5 barbas',         'dededede-0000-4000-8000-000000000042', 5,  150.00, 90,  true, now() - interval '90 days', now());

    FOR r IN SELECT id, name, phone_number FROM customers WHERE barbershop_id = v_shop ORDER BY name LIMIT 9 LOOP
        v_i := v_i + 1;
        v_cp := gen_random_uuid();
        INSERT INTO customer_packages (id, barbershop_id, customer_id, package_id, package_name, service_id, service_name,
                                       total_uses, remaining_uses, price, unit_value, payment_method, purchased_at, expires_at, created_at, updated_at)
        SELECT v_cp, v_shop, r.id, p.id, p.name, p.service_id, s.name, p.quantity,
               GREATEST(0, p.quantity - (v_i % p.quantity) - 1), p.price, round(p.price / p.quantity, 2),
               CASE WHEN v_i % 2 = 0 THEN 'PIX' ELSE 'CREDITO' END,
               now() - (v_i * interval '4 days'), (current_date - v_i * 4) + p.validity_days, now(), now()
          FROM service_packages p JOIN services s ON s.id = p.service_id
         WHERE p.id = (ARRAY[v_pk_corte4, v_pk_corte10, v_pk_barba5])[1 + v_i % 3];
        INSERT INTO package_usages (id, barbershop_id, customer_package_id, professional_id, service_name, uses, unit_value, original_price, usage_date, used_at)
        SELECT gen_random_uuid(), v_shop, cp.id, v_prof[1 + (g % 3)], cp.service_name, 1, cp.unit_value,
               CASE WHEN cp.service_name = 'Barba' THEN 35.00 ELSE 45.00 END,
               (cp.purchased_at + g * interval '6 days')::date, cp.purchased_at + g * interval '6 days' + interval '10 hours'
          FROM customer_packages cp, generate_series(1, (SELECT total_uses - remaining_uses FROM customer_packages WHERE id = v_cp)) g
         WHERE cp.id = v_cp;
    END LOOP;

    -- ---- etiquetas -----------------------------------------------------------------------------
    INSERT INTO customer_tags (id, barbershop_id, name, color, created_at, updated_at)
    VALUES (v_tag[1], v_shop, 'VIP',               '#C9A227', now(), now()),
           (v_tag[2], v_shop, 'Indica amigos',     '#25D366', now(), now()),
           (v_tag[3], v_shop, 'Prefere manhã',     '#3E63FF', now(), now()),
           (v_tag[4], v_shop, 'Barba toalha quente','#A78BFA', now(), now());
    v_i := 0;
    FOR r IN SELECT id FROM customers WHERE barbershop_id = v_shop ORDER BY phone_number LIMIT 30 LOOP
        v_i := v_i + 1;
        INSERT INTO customer_tag_links (barbershop_id, customer_id, tag_id, created_at)
        VALUES (v_shop, r.id, v_tag[1 + v_i % 4], now());
        IF v_i % 3 = 0 THEN
            INSERT INTO customer_tag_links (barbershop_id, customer_id, tag_id, created_at)
            VALUES (v_shop, r.id, v_tag[1 + (v_i + 1) % 4], now());
        END IF;
    END LOOP;

    -- ---- avaliacoes depois do atendimento -------------------------------------------------------
    v_i := 0;
    FOR r IN SELECT a.id, a.customer_phone, a.customer_name, a.professional_id, a.appointment_date, a.appointment_time
               FROM appointments a
              WHERE a.barbershop_id = v_shop AND a.appointment_date < current_date
                AND a.appointment_date >= current_date - 40
              ORDER BY a.appointment_date DESC, a.id LIMIT 46 LOOP
        v_i := v_i + 1;
        v_ts := r.appointment_date + r.appointment_time + interval '2 hours';
        INSERT INTO customer_reviews (id, barbershop_id, customer_phone, customer_name, appointment_id, professional_id,
                                      status, rating, comment, scheduled_for, sent_at, rated_at, created_at, updated_at)
        VALUES (gen_random_uuid(), v_shop, r.customer_phone, r.customer_name, r.id, r.professional_id,
                CASE WHEN v_i % 7 = 0 THEN 'SENT' ELSE 'RATED' END,
                CASE WHEN v_i % 7 = 0 THEN NULL WHEN v_i % 9 = 0 THEN 4 WHEN v_i % 17 = 0 THEN 3 ELSE 5 END,
                CASE WHEN v_i % 7 = 0 THEN NULL ELSE v_comments[1 + v_i % 10] END,
                v_ts, v_ts, CASE WHEN v_i % 7 = 0 THEN NULL ELSE v_ts + interval '40 minutes' END, v_ts, now());
    END LOOP;

    -- ---- "o que a IA fez": mensagens respondidas e confirmacoes -------------------------------
    -- Mensagens do bot nos ultimos 35 dias; ~30% fora do horario (antes das 9h, depois das 20h).
    INSERT INTO interactions (id, customer_id, type, content, timestamp, barbershop_id)
    SELECT gen_random_uuid(), c.id, 'BOT', 'Resposta automática (demo)',
           date_trunc('day', now()) - (d * interval '1 day')
             + CASE WHEN (d + n) % 10 < 3 THEN interval '21 hours' + (n % 50) * interval '1 minute'
                    ELSE interval '9 hours' + ((d * 7 + n * 13) % 600) * interval '1 minute' END,
           v_shop
      FROM generate_series(0, 34) d
      CROSS JOIN generate_series(1, 28) n
      JOIN LATERAL (SELECT id FROM customers WHERE barbershop_id = v_shop
                     ORDER BY md5(id::text || d || n) LIMIT 1) c ON true;

    INSERT INTO ai_impact_events (id, barbershop_id, event_type, appointment_id, customer_phone, created_at)
    SELECT gen_random_uuid(), v_shop,
           CASE WHEN a.id % 13 = 0 THEN 'DECLINED' WHEN a.id % 17 = 0 THEN 'NO_RESPONSE' ELSE 'CONFIRMED' END,
           a.id, a.customer_phone, (a.appointment_date - 1) + time '18:00'
      FROM appointments a
     WHERE a.barbershop_id = v_shop AND a.appointment_date BETWEEN current_date - 34 AND current_date;
    INSERT INTO ai_impact_events (id, barbershop_id, event_type, customer_phone, created_at)
    SELECT gen_random_uuid(), v_shop, (ARRAY['AUTO_RELEASED','WAITLIST_REFILL'])[1 + g % 2], '5511900000' || lpad(g::text, 3, '0'),
           now() - g * interval '2 days 3 hours'
      FROM generate_series(1, 14) g;

    -- ---- consumo da IA (console da plataforma) -------------------------------------------------
    INSERT INTO ai_usage_daily (barbershop_id, day, model, kind, calls, failures, input_tokens, output_tokens)
    SELECT v_shop, current_date - d, 'gpt-4o-mini', 'CHAT', 70 + (d * 37) % 55, CASE WHEN d % 9 = 0 THEN 1 ELSE 0 END,
           (70 + (d * 37) % 55) * 1850, (70 + (d * 37) % 55) * 140
      FROM generate_series(0, 29) d;
    INSERT INTO ai_usage_daily (barbershop_id, day, model, kind, calls, failures, input_tokens, output_tokens)
    SELECT v_shop, current_date - d, 'whisper-1', 'TRANSCRIBE', 8 + (d * 5) % 9, 0, 0, 0
      FROM generate_series(0, 29) d;

    RAISE NOTICE 'Complemento da demo concluido.';
END
$extra$;
