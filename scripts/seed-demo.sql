-- =============================================================================================
-- RAZOR · seed de demonstracao
-- =============================================================================================
--
-- Popula o banco com uma barbearia ficticia e ~60 dias de movimento coerente, para que o painel
-- tenha o que mostrar em relatorio, caixa, comanda, estoque, comissao e fidelidade.
--
-- Como rodar:
--   docker compose exec -T postgres psql -U barberbot_user -d barberbot_db < scripts/seed-demo.sql
--   (ou:  .\scripts\seed.ps1)
--
-- ESCOPO: tudo aqui pertence a UM tenant de id fixo (v_shop). O bloco comeca apagando esse
-- tenant e so ele — rodar de novo regenera a demo sem encostar em nenhuma outra barbearia.
-- Por isso e seguro rodar num banco que ja tenha dados reais.
--
-- COERENCIA DO DINHEIRO: o seed nao inventa numero solto. Ele respeita as mesmas invariantes
-- que o codigo cobra, porque um seed que as viola faz o painel mostrar valores que o sistema
-- jamais produziria — e ai o teste nao vale nada:
--   · uma venda gera exatamente um movimento de caixa;
--   · comanda FECHADA tem total, forma de pagamento e data de fechamento (CHECK do banco);
--   · total da comanda = soma dos itens − desconto do cupom;
--   · esperado na gaveta = fundo + suprimentos − sangrias + vendas em dinheiro
--     (mesma conta de CashRegisterService.expectedDrawer);
--   · todo movimento de estoque grava o saldo resultante de verdade, lido do proprio UPDATE.
--
-- Datas sao relativas a CURRENT_DATE, entao o "mes atual" do relatorio sempre tem movimento,
-- nao importa quando o seed rodar.
-- =============================================================================================

DO $seed$
DECLARE
    -- Tenant da demo. UUID fixo e proposital: e o que torna o seed re-executavel e inofensivo.
    v_shop      uuid := 'dededede-0000-4000-8000-000000000001';

    -- bcrypt de "demo12345", gerado pelo proprio BCryptPasswordEncoder da aplicacao.
    v_pwd       text := '$2a$10$PNg40lXM.aZi1OnL0fV2qe/B7O8blXmHQwQ0oz0zGrmcM5WFT.8y6';

    v_prof      uuid[];          -- barbeiros
    v_svc_nome  text[];          -- catalogo de servicos (nome/preco/duracao)
    v_svc_preco numeric[];
    v_prod      uuid[];          -- produtos
    v_prod_nome text[];
    v_prod_prec numeric[];
    v_cust      uuid[];          -- clientes
    v_cust_nome text[];
    v_cust_fone text[];

    v_plan_bronze uuid := 'dededede-0000-4000-8000-000000000101';
    v_plan_silver uuid := 'dededede-0000-4000-8000-000000000102';
    v_plan_vip    uuid := 'dededede-0000-4000-8000-000000000103';

    v_cup_bemvindo uuid := 'dededede-0000-4000-8000-000000000201';
    v_cup_fidelidade uuid := 'dededede-0000-4000-8000-000000000202';
    v_cup_indicacao uuid := 'dededede-0000-4000-8000-000000000203';

    -- estado do laco diario
    v_dia        date;
    v_i          int;
    v_n          int;
    v_sessao     uuid;
    v_abertura   numeric(12,2);
    v_dinheiro   numeric(12,2);   -- vendas em dinheiro do dia
    v_sangrias   numeric(12,2);
    v_supri      numeric(12,2);
    v_esperado   numeric(12,2);
    v_contado    numeric(12,2);

    v_comanda    uuid;
    v_agend      bigint;
    v_mov        uuid;
    v_venda      uuid;
    v_total      numeric(12,2);
    v_desconto   numeric(12,2);
    v_metodo     text;
    v_hora       time;
    v_k          int;             -- contador da comanda dentro do dia
    v_ci         int;             -- indice do cliente
    v_si         int;             -- indice do servico
    v_qi         int;             -- indice do produto
    v_estoque    int;
    v_cupom      uuid;
    v_redencoes  int := 0;
    v_metodos    text[] := ARRAY['DINHEIRO','PIX','DEBITO','CREDITO','PIX','DINHEIRO'];
BEGIN
    -- -----------------------------------------------------------------------------------------
    -- 0. Limpeza do tenant demo (ordem que respeita as FKs)
    -- -----------------------------------------------------------------------------------------
    DELETE FROM expenses              WHERE barbershop_id = v_shop;
    DELETE FROM stock_movements       WHERE barbershop_id = v_shop;
    DELETE FROM coupon_redemptions    WHERE barbershop_id = v_shop;
    DELETE FROM product_sales         WHERE barbershop_id = v_shop;
    DELETE FROM comanda_items         WHERE barbershop_id = v_shop;
    DELETE FROM comandas              WHERE barbershop_id = v_shop;
    DELETE FROM commission_payments   WHERE barbershop_id = v_shop;
    DELETE FROM professional_advances WHERE barbershop_id = v_shop;
    DELETE FROM cash_movements        WHERE barbershop_id = v_shop;
    DELETE FROM cash_register_sessions WHERE barbershop_id = v_shop;
    DELETE FROM appointments          WHERE barbershop_id = v_shop;
    DELETE FROM interactions          WHERE barbershop_id = v_shop;
    DELETE FROM scheduled_tasks       WHERE barbershop_id = v_shop;
    DELETE FROM waitlist_entries      WHERE barbershop_id = v_shop;
    DELETE FROM schedule_blocks       WHERE barbershop_id = v_shop;
    DELETE FROM customers             WHERE barbershop_id = v_shop;
    DELETE FROM coupons               WHERE barbershop_id = v_shop;
    DELETE FROM plan_benefits         WHERE barbershop_id = v_shop;
    DELETE FROM subscription_plans    WHERE barbershop_id = v_shop;
    DELETE FROM products              WHERE barbershop_id = v_shop;
    DELETE FROM services              WHERE barbershop_id = v_shop;
    DELETE FROM professionals         WHERE barbershop_id = v_shop;
    DELETE FROM barbershop_admins     WHERE barbershop_id = v_shop;
    DELETE FROM tenant_users          WHERE barbershop_id = v_shop;
    DELETE FROM barbershop_settings   WHERE barbershop_id = v_shop;
    DELETE FROM barbershops           WHERE id = v_shop;

    -- -----------------------------------------------------------------------------------------
    -- 1. Barbearia, configuracoes e usuarios do painel
    -- -----------------------------------------------------------------------------------------
    INSERT INTO barbershops (id, name, slug, whatsapp_instance, active, created_at, updated_at)
    VALUES (v_shop, 'Barbearia Navalha de Prata (demo)', 'demo-navalha', 'DemoNavalhaWpp',
            true, now() - interval '18 months', now());

    INSERT INTO barbershop_settings (
        barbershop_id, display_name, address_text, address_maps_url, schedule_url, instagram_url,
        bot_enabled, pix_key, pix_copy_paste, payment_info, services_text,
        -- working_weekdays (V32) e nao mais o working_days de texto livre: e a coluna que a
        -- agenda le de verdade. Sem ela o seed dependeria do fallback e a demo nao exercitaria
        -- o campo novo.
        opening_time, closing_time, working_weekdays,
        welcome_text, reminder_text, review_text, fidelity_reward_text, cancellation_policy,
        bot_persona, monthly_revenue_goal, created_at, updated_at)
    VALUES (
        v_shop, 'Navalha de Prata',
        E'📍 *Barbearia Navalha de Prata*\nRua das Tesouras, 456 - Centro\nCidade Exemplo - SP, 01000-000',
        'https://maps.google.com/?q=barbearia-exemplo',
        'https://exemplo.com.br/agendar/navalha',
        'https://instagram.com/navalhadeprata_demo',
        true,
        'demo@navalhadeprata.com.br',
        '00020126580014BR.GOV.BCB.PIX0136demo-chave-ficticia-nao-funcional5204000053039865802BR',
        'Aceitamos PIX, dinheiro, débito e crédito em até 3x.',
        -- Tabela de precos: sem ela o bot responde que nao sabe quanto custa (nunca inventa
        -- preco), e a demo mostraria um assistente incapaz de responder a pergunta mais comum.
        E'✂️ *Nossos serviços*\n\nCorte — R$ 45\nBarba — R$ 35\nCorte + Barba — R$ 70\nCorte infantil — R$ 40\nPezinho — R$ 20\nPlatinado — R$ 150',
        '09:00', '20:00', '1,2,3,4,5,6',
        'Fala! Aqui é a Navalha de Prata. Quer agendar um horário?',
        'Passando pra lembrar do seu horário amanhã. Confirma pra mim?',
        'E aí, curtiu o corte? Se puder deixar uma avaliação ajuda demais!',
        'Você fechou 10 visitas — o próximo corte é por nossa conta! 🎉',
        'Cancelamento sem custo até 2h antes do horário marcado.',
        'Direto, simpático e sem enrolação. Trata o cliente por você.',
        28000.00,
        now() - interval '18 months', now());

    INSERT INTO tenant_users (id, barbershop_id, email, password_hash, name, role, active, created_at, updated_at)
    VALUES
      ('dededede-0000-4000-8000-000000000011', v_shop, 'dono@navalhademo.local',      v_pwd, 'Ricardo Prata',  'OWNER',     true, now() - interval '18 months', now()),
      ('dededede-0000-4000-8000-000000000012', v_shop, 'recepcao@navalhademo.local',  v_pwd, 'Juliana Alves',  'RECEPTION', true, now() - interval '10 months', now()),
      ('dededede-0000-4000-8000-000000000013', v_shop, 'barbeiro@navalhademo.local',  v_pwd, 'Rafael Tesoura', 'BARBER',    true, now() - interval '8 months',  now());

    INSERT INTO barbershop_admins (id, barbershop_id, phone, name, created_at, updated_at)
    VALUES ('dededede-0000-4000-8000-000000000021', v_shop, '5511999990001', 'Ricardo Prata', now(), now());

    -- -----------------------------------------------------------------------------------------
    -- 2. Equipe
    --    product_commission_percent NULL = herda o percentual de servico (decisao da V23:
    --    nulo significa "herda", nunca "zero").
    -- -----------------------------------------------------------------------------------------
    v_prof := ARRAY['dededede-0000-4000-8000-000000000031'::uuid,
                    'dededede-0000-4000-8000-000000000032'::uuid,
                    'dededede-0000-4000-8000-000000000033'::uuid];

    INSERT INTO professionals (id, barbershop_id, name, phone, active, commission_percent, product_commission_percent, created_at, updated_at)
    VALUES
      (v_prof[1], v_shop, 'Rafael Tesoura', '5511999990002', true, 40.00, 10.00, now() - interval '18 months', now()),
      (v_prof[2], v_shop, 'Bruno Navalha',  '5511999990003', true, 45.00, NULL,  now() - interval '12 months', now()),
      (v_prof[3], v_shop, 'Caio Máquina',   '5511999990004', true, 35.00, 15.00, now() - interval '5 months',  now());

    -- Login do barbeiro ligado ao cadastro dele (V44): o "Meu dia" mostra os horarios e a
    -- comissao do Rafael.
    UPDATE tenant_users SET professional_id = v_prof[1]
     WHERE id = 'dededede-0000-4000-8000-000000000013';

    -- -----------------------------------------------------------------------------------------
    -- 3. Catalogo de servicos
    --    Os nomes contem as palavras que o PricingService usa para categorizar ("combo",
    --    "barba"); fora desse padrao o servico cairia em SIMPLES e seria faturado errado.
    -- -----------------------------------------------------------------------------------------
    v_svc_nome  := ARRAY['Corte de Cabelo', 'Barba', 'Corte + Barba (Combo)', 'Corte Visagista',
                         'Barba Visagista', 'Sobrancelha', 'Pézinho'];
    v_svc_preco := ARRAY[45.00, 35.00, 70.00, 90.00, 60.00, 20.00, 20.00];

    INSERT INTO services (id, barbershop_id, name, price, duration_minutes, active, commission_percent, created_at, updated_at)
    VALUES
      ('dededede-0000-4000-8000-000000000041', v_shop, v_svc_nome[1], v_svc_preco[1], 30, true, NULL,  now() - interval '18 months', now()),
      ('dededede-0000-4000-8000-000000000042', v_shop, v_svc_nome[2], v_svc_preco[2], 30, true, NULL,  now() - interval '18 months', now()),
      ('dededede-0000-4000-8000-000000000043', v_shop, v_svc_nome[3], v_svc_preco[3], 60, true, NULL,  now() - interval '18 months', now()),
      -- Servico premium com percentual proprio (V27): o barbeiro leva mais nesse aqui.
      ('dededede-0000-4000-8000-000000000044', v_shop, v_svc_nome[4], v_svc_preco[4], 60, true, 50.00, now() - interval '6 months',  now()),
      ('dededede-0000-4000-8000-000000000045', v_shop, v_svc_nome[5], v_svc_preco[5], 45, true, 50.00, now() - interval '6 months',  now()),
      ('dededede-0000-4000-8000-000000000046', v_shop, v_svc_nome[6], v_svc_preco[6], 15, true, NULL,  now() - interval '18 months', now()),
      ('dededede-0000-4000-8000-000000000047', v_shop, v_svc_nome[7], v_svc_preco[7], 15, true, NULL,  now() - interval '18 months', now());

    -- -----------------------------------------------------------------------------------------
    -- 4. Produtos
    --    track_stock opt-in (V30): so quem conta estoque tem venda bloqueada por falta.
    -- -----------------------------------------------------------------------------------------
    v_prod      := ARRAY['dededede-0000-4000-8000-000000000051'::uuid,
                         'dededede-0000-4000-8000-000000000052'::uuid,
                         'dededede-0000-4000-8000-000000000053'::uuid,
                         'dededede-0000-4000-8000-000000000054'::uuid,
                         'dededede-0000-4000-8000-000000000055'::uuid];
    v_prod_nome := ARRAY['Pomada Modeladora', 'Óleo para Barba', 'Shampoo Anticaspa',
                         'Cera Fixadora', 'Kit Barba Completo'];
    v_prod_prec := ARRAY[45.00, 55.00, 38.00, 42.00, 130.00];

    INSERT INTO products (id, barbershop_id, name, price, active, track_stock, stock_quantity, min_stock, created_at, updated_at)
    VALUES
      (v_prod[1], v_shop, v_prod_nome[1], v_prod_prec[1], true, true,  120, 10, now() - interval '12 months', now()),
      (v_prod[2], v_shop, v_prod_nome[2], v_prod_prec[2], true, true,   80,  8, now() - interval '12 months', now()),
      (v_prod[3], v_shop, v_prod_nome[3], v_prod_prec[3], true, true,   60,  6, now() - interval '9 months',  now()),
      -- Sem controle de estoque: vende sempre, nunca bloqueia.
      (v_prod[4], v_shop, v_prod_nome[4], v_prod_prec[4], true, false,   0,  0, now() - interval '9 months',  now()),
      -- Proposital: estoque abaixo do minimo, para o alerta de reposicao aparecer no painel.
      (v_prod[5], v_shop, v_prod_nome[5], v_prod_prec[5], true, true,    3,  5, now() - interval '4 months',  now());

    -- Entrada inicial de estoque, para o historico nao comecar do nada.
    INSERT INTO stock_movements (id, barbershop_id, product_id, type, quantity, resulting_stock, note, created_at)
    SELECT gen_random_uuid(), v_shop, p.id, 'ENTRADA', p.stock_quantity, p.stock_quantity,
           'Carga inicial (demo)', now() - interval '90 days'
    FROM products p WHERE p.barbershop_id = v_shop AND p.track_stock;

    -- -----------------------------------------------------------------------------------------
    -- 5. Planos de assinatura e beneficios
    --    Precos definidos pelo dono: 69,90 / 94,90 / 119,90.
    -- -----------------------------------------------------------------------------------------
    INSERT INTO subscription_plans (id, barbershop_id, name, price, description, active, booking_window_days, created_at, updated_at)
    VALUES
      (v_plan_bronze, v_shop, 'Bronze', 69.90,  'Corte ilimitado de segunda a quarta.',        true, 7,    now() - interval '6 months', now()),
      (v_plan_silver, v_shop, 'Silver', 94.90,  'Corte e barba de segunda a sexta.',           true, 14,   now() - interval '6 months', now()),
      (v_plan_vip,    v_shop, 'VIP',    119.90, 'Tudo ilimitado, inclusive sábado, com 20% nos produtos.', true, 30, now() - interval '6 months', now());

    INSERT INTO plan_benefits (id, barbershop_id, plan_id, target_type, target_id, discount_percent, monthly_quota, weekdays, created_at, updated_at)
    VALUES
      -- Bronze: corte de graca, so seg-qua, ate 4 por mes.
      (gen_random_uuid(), v_shop, v_plan_bronze, 'SERVICO', 'dededede-0000-4000-8000-000000000041', 100.00, 4,    '1,2,3',       now(), now()),
      -- Silver: corte e barba de graca, seg-sex, ate 6 por mes.
      (gen_random_uuid(), v_shop, v_plan_silver, 'SERVICO', 'dededede-0000-4000-8000-000000000041', 100.00, 6,    '1,2,3,4,5',   now(), now()),
      (gen_random_uuid(), v_shop, v_plan_silver, 'SERVICO', 'dededede-0000-4000-8000-000000000042', 100.00, 6,    '1,2,3,4,5',   now(), now()),
      -- VIP: qualquer servico de graca, sem cota, e 20% em qualquer produto.
      (gen_random_uuid(), v_shop, v_plan_vip,    'SERVICO', NULL,                                   100.00, NULL, '1,2,3,4,5,6', now(), now()),
      (gen_random_uuid(), v_shop, v_plan_vip,    'PRODUTO', NULL,                                    20.00, NULL, NULL,          now(), now());

    -- -----------------------------------------------------------------------------------------
    -- 6. Clientes
    -- -----------------------------------------------------------------------------------------
    -- Base de 72 clientes, e nao 18.
    --
    -- Com 18 clientes os ~370 atendimentos do historico davam uma visita a cada 2,7 dias por
    -- pessoa — frequencia que nao existe em barbearia nenhuma. Duas telas ficavam inuteis por
    -- causa disso: a Retencao mostrava "18 ativos" e mais nada (todo mundo abaixo do piso de
    -- ritmo plausivel, entao nem mediana havia), e a lista de Clientes cabia numa pagina so, sem
    -- exercitar a paginacao.
    --
    -- Com 72, o mesmo volume financeiro se espalha em ~5 visitas por cliente no periodo, dando
    -- intervalos de 12 a 20 dias. Volume de caixa igual, comportamento realista.
    v_cust_nome := ARRAY['André Souza','Bruno Lima','Carlos Dias','Diego Rocha','Eduardo Pinto',
                         'Fábio Moreira','Gustavo Alves','Henrique Costa','Igor Ramos','João Bastos',
                         'Kleber Nunes','Lucas Freitas','Marcelo Tavares','Nelson Bueno','Otávio Prado',
                         'Paulo Vieira','Rodrigo Sales','Thiago Mendes',
                         'Alexandre Rocha','Bento Farias','Caio Antunes','Danilo Peixoto','Elias Marques',
                         'Felipe Duarte','Gabriel Lira','Heitor Campos','Ivan Portela','Joel Siqueira',
                         'Kaique Barros','Leandro Pires','Murilo Fontes','Norberto Alencar','Osvaldo Reis',
                         'Pedro Cavalcanti','Quirino Matos','Renato Bastos','Sérgio Vasques','Tarcísio Melo',
                         'Ubirajara Pinto','Valter Assunção','Wagner Diniz','Xavier Brandão','Yuri Salgado',
                         'Zeca Monteiro','Adriano Cordeiro','Breno Tavares','Cássio Nogueira','Décio Ramalho',
                         'Emerson Paiva','Fernando Quirino','Guilherme Sá','Hugo Teixeira','Ícaro Amaral',
                         'Jonas Bittencourt','Kevin Andrade','Luiz Otávio','Mateus Camargo','Nicolas Prado',
                         'Otto Bergamo','Pablo Estrela','Raul Continentino','Samuel Vidigal','Téo Rangel',
                         'Ulisses Franco','Vinícius Meireles','Wesley Aragão','Yago Bertoldo','Zico Neves',
                         'Anderson Vilela','Bruno Sampaio','César Delgado','Douglas Mainardi'];

    v_cust := ARRAY[]::uuid[];
    v_cust_fone := ARRAY[]::text[];
    FOR v_i IN 1 .. array_length(v_cust_nome, 1) LOOP
        v_cust := array_append(v_cust, gen_random_uuid());
        v_cust_fone := array_append(v_cust_fone, '5511988' || lpad(v_i::text, 6, '0'));
    END LOOP;

    FOR v_i IN 1 .. array_length(v_cust_nome, 1) LOOP
        INSERT INTO customers (id, barbershop_id, phone_number, name, fidelity_points,
                               subscription_plan_id, created_at, updated_at)
        VALUES (v_cust[v_i], v_shop, v_cust_fone[v_i], v_cust_nome[v_i], 0,
                -- Os tres primeiros sao assinantes, um de cada plano; o resto e avulso.
                CASE v_i WHEN 1 THEN v_plan_vip WHEN 2 THEN v_plan_silver WHEN 3 THEN v_plan_bronze ELSE NULL END,
                now() - (interval '1 day' * (400 - v_i * 17)), now());
    END LOOP;

    -- -----------------------------------------------------------------------------------------
    -- 7. Cupons
    -- -----------------------------------------------------------------------------------------
    INSERT INTO coupons (id, barbershop_id, code, description, discount_type, discount_value, origin,
                         customer_phone, valid_from, valid_until, max_uses, used_count, min_amount, active, created_at, updated_at)
    VALUES
      (v_cup_bemvindo,   v_shop, 'BEMVINDO10', 'Primeira visita: 10% de desconto', 'PERCENT', 10.00, 'MANUAL', NULL,
       CURRENT_DATE - 90, CURRENT_DATE + 90, 200, 0, 40.00, true, now() - interval '90 days', now()),
      (v_cup_indicacao,  v_shop, 'INDICA15',   'Indicou um amigo: R$ 15 off',      'FIXED',   15.00, 'MANUAL', NULL,
       CURRENT_DATE - 60, CURRENT_DATE + 30, 100, 0, 50.00, true, now() - interval '60 days', now()),
      -- Cupom nominal, emitido pelo cartao fidelidade (V29) para um cliente especifico.
      (v_cup_fidelidade, v_shop, 'FIDELIDADE-DEMO', 'Corte grátis do cartão fidelidade', 'FIXED', 45.00, 'FIDELIDADE',
       v_cust_fone[4], CURRENT_DATE - 10, CURRENT_DATE + 80, 1, 0, NULL, true, now() - interval '10 days', now());

    -- -----------------------------------------------------------------------------------------
    -- 8. Movimento dos ultimos 60 dias
    --    Um dia = uma sessao de caixa fechada, N comandas fechadas, cada uma com o seu unico
    --    movimento de caixa. Domingo a barbearia nao abre.
    -- -----------------------------------------------------------------------------------------
    FOR v_i IN REVERSE 60 .. 1 LOOP
        v_dia := CURRENT_DATE - v_i;
        CONTINUE WHEN extract(dow from v_dia) = 0;   -- domingo fechado

        v_abertura := 150.00;
        v_dinheiro := 0; v_sangrias := 0; v_supri := 0;

        v_sessao := gen_random_uuid();
        INSERT INTO cash_register_sessions (id, barbershop_id, opened_by_name, opened_at, opening_amount,
                                            status, created_at, updated_at)
        VALUES (v_sessao, v_shop, 'Juliana Alves', v_dia + time '08:45', v_abertura, 'OPEN', now(), now());

        -- Sabado enche, meio de semana e mais calmo.
        v_n := CASE WHEN extract(dow from v_dia) = 6 THEN 9 ELSE 5 + (v_i % 3) END;

        FOR v_k IN 1 .. v_n LOOP
            v_ci := 1 + ((v_i * 7 + v_k * 3) % array_length(v_cust, 1));
            v_si := 1 + ((v_i * 5 + v_k * 2) % array_length(v_svc_nome, 1));
            v_hora := time '09:00' + (interval '45 minutes' * (v_k - 1));
            v_metodo := v_metodos[1 + ((v_i + v_k) % 6)];

            -- Agendamento que originou a comanda (a agenda e a base das comissoes).
            INSERT INTO appointments (customer_phone, customer_name, service, payment_status,
                                      notified, thanked, appointment_date, appointment_time,
                                      barbershop_id, professional_id, created_at, updated_at)
            VALUES (v_cust_fone[v_ci], v_cust_nome[v_ci], v_svc_nome[v_si],
                    CASE WHEN v_metodo = 'PIX' THEN 'PIX' ELSE 'PRESENCIAL' END,
                    true, true, v_dia, v_hora, v_shop,
                    v_prof[1 + ((v_i + v_k) % 3)], v_dia + v_hora - interval '2 days', now())
            RETURNING id INTO v_agend;

            v_comanda := gen_random_uuid();
            v_total := v_svc_preco[v_si];
            v_desconto := NULL;
            v_cupom := NULL;

            INSERT INTO comandas (id, barbershop_id, customer_name, customer_phone, professional_id,
                                  status, opened_at, comanda_date, appointment_id, created_at, updated_at)
            VALUES (v_comanda, v_shop, v_cust_nome[v_ci], v_cust_fone[v_ci],
                    v_prof[1 + ((v_i + v_k) % 3)], 'ABERTA', v_dia + v_hora, v_dia, v_agend, now(), now());

            -- Item de servico. Preco copiado do catalogo, nao referenciado: reajuste futuro nao
            -- pode reescrever o faturamento de um mes ja fechado.
            INSERT INTO comanda_items (id, comanda_id, barbershop_id, kind, reference_id, description,
                                       unit_price, quantity, total, created_at)
            VALUES (gen_random_uuid(), v_comanda, v_shop, 'SERVICO', NULL, v_svc_nome[v_si],
                    v_svc_preco[v_si], 1, v_svc_preco[v_si], v_dia + v_hora);

            -- A cada 4 atendimentos, o cliente leva um produto junto.
            IF (v_i + v_k) % 4 = 0 THEN
                v_qi := 1 + ((v_i + v_k) % 5);

                -- O saldo resultante vem do proprio UPDATE: e o unico jeito de o historico de
                -- estoque nunca discordar do saldo atual do produto.
                IF (SELECT track_stock FROM products WHERE id = v_prod[v_qi]) THEN
                    UPDATE products SET stock_quantity = stock_quantity - 1
                    WHERE id = v_prod[v_qi] AND stock_quantity > 0
                    RETURNING stock_quantity INTO v_estoque;
                ELSE
                    v_estoque := NULL;
                END IF;

                IF v_estoque IS NOT NULL OR NOT (SELECT track_stock FROM products WHERE id = v_prod[v_qi]) THEN
                    v_total := v_total + v_prod_prec[v_qi];

                    INSERT INTO comanda_items (id, comanda_id, barbershop_id, kind, reference_id, description,
                                               unit_price, quantity, total, created_at)
                    VALUES (gen_random_uuid(), v_comanda, v_shop, 'PRODUTO', v_prod[v_qi], v_prod_nome[v_qi],
                            v_prod_prec[v_qi], 1, v_prod_prec[v_qi], v_dia + v_hora);

                    v_venda := gen_random_uuid();
                    INSERT INTO product_sales (id, barbershop_id, product_id, product_name, unit_price,
                                               quantity, total, professional_id, payment_method,
                                               sale_date, sold_at, comanda_id, created_at)
                    VALUES (v_venda, v_shop, v_prod[v_qi], v_prod_nome[v_qi], v_prod_prec[v_qi],
                            1, v_prod_prec[v_qi], v_prof[1 + ((v_i + v_k) % 3)], v_metodo,
                            v_dia, v_dia + v_hora, v_comanda, now());

                    IF v_estoque IS NOT NULL THEN
                        INSERT INTO stock_movements (id, barbershop_id, product_id, type, quantity,
                                                     resulting_stock, product_sale_id, note, created_at)
                        VALUES (gen_random_uuid(), v_shop, v_prod[v_qi], 'SAIDA', 1, v_estoque,
                                v_venda, 'Venda em comanda', v_dia + v_hora);
                    END IF;
                END IF;
            END IF;

            -- Um cupom aplicado de vez em quando, com a redencao registrada.
            IF (v_i % 11) = 0 AND v_k = 2 AND v_total >= 50.00 THEN
                v_cupom := v_cup_indicacao;
                v_desconto := 15.00;
                v_total := v_total - v_desconto;
                v_redencoes := v_redencoes + 1;

                INSERT INTO coupon_redemptions (id, barbershop_id, coupon_id, comanda_id, customer_phone,
                                                amount, redeemed_at, redemption_date)
                VALUES (gen_random_uuid(), v_shop, v_cupom, v_comanda, v_cust_fone[v_ci],
                        v_desconto, v_dia + v_hora, v_dia);
            END IF;

            -- Uma venda, um movimento de caixa. Dobrar aqui e o erro mais caro possivel,
            -- porque a gaveta e conferida contra dinheiro fisico.
            v_mov := gen_random_uuid();
            INSERT INTO cash_movements (id, session_id, barbershop_id, type, payment_method, amount, description, created_at)
            VALUES (v_mov, v_sessao, v_shop, 'VENDA', v_metodo, v_total,
                    'Comanda de ' || v_cust_nome[v_ci], v_dia + v_hora);

            IF v_metodo = 'DINHEIRO' THEN
                v_dinheiro := v_dinheiro + v_total;
            END IF;

            UPDATE comandas
               SET status = 'FECHADA', total = v_total, payment_method = v_metodo,
                   closed_at = v_dia + v_hora + interval '40 minutes',
                   cash_movement_id = v_mov, coupon_id = v_cupom, discount_amount = v_desconto
             WHERE id = v_comanda;
        END LOOP;

        -- Vale para barbeiro toda sexta: sai dinheiro da gaveta, entao e uma sangria.
        IF extract(dow from v_dia) = 5 THEN
            v_mov := gen_random_uuid();
            INSERT INTO cash_movements (id, session_id, barbershop_id, type, payment_method, amount, description, created_at)
            VALUES (v_mov, v_sessao, v_shop, 'SANGRIA', NULL, 100.00, 'Vale — Caio Máquina', v_dia + time '18:00');
            v_sangrias := v_sangrias + 100.00;

            INSERT INTO professional_advances (id, barbershop_id, professional_id, amount, advance_date,
                                               notes, cash_movement_id, granted_by_name, created_at)
            VALUES (gen_random_uuid(), v_shop, v_prof[3], 100.00, v_dia, 'Vale semanal',
                    v_mov, 'Ricardo Prata', v_dia + time '18:00');
        END IF;

        -- Sangria de seguranca quando o caixa acumula muito dinheiro vivo.
        IF v_dinheiro > 400.00 THEN
            INSERT INTO cash_movements (id, session_id, barbershop_id, type, payment_method, amount, description, created_at)
            VALUES (gen_random_uuid(), v_sessao, v_shop, 'SANGRIA', NULL, 200.00, 'Sangria para o cofre', v_dia + time '19:00');
            v_sangrias := v_sangrias + 200.00;
        END IF;

        -- Fechamento, pela mesma conta do CashRegisterService.
        v_esperado := v_abertura + v_supri - v_sangrias + v_dinheiro;
        -- Um dia do mes fecha com quebra de caixa, que e o que acontece na vida real.
        v_contado := CASE WHEN v_i % 23 = 0 THEN v_esperado - 2.00 ELSE v_esperado END;

        UPDATE cash_register_sessions
           SET status = 'CLOSED', closed_by_name = 'Ricardo Prata',
               closed_at = v_dia + time '20:15',
               closing_expected = v_esperado, closing_counted = v_contado,
               notes = CASE WHEN v_contado <> v_esperado THEN 'Diferença conferida com a equipe.' ELSE NULL END,
               updated_at = now()
         WHERE id = v_sessao;
    END LOOP;

    UPDATE coupons SET used_count = v_redencoes WHERE id = v_cup_indicacao;

    -- -----------------------------------------------------------------------------------------
    -- 9. Hoje: alguns cortes ja fechados pela manha, caixa aberto, duas comandas em andamento
    --    agora e mais uns horarios reservados para o resto do dia.
    -- -----------------------------------------------------------------------------------------
    v_sessao := gen_random_uuid();
    INSERT INTO cash_register_sessions (id, barbershop_id, opened_by_name, opened_at, opening_amount,
                                        status, created_at, updated_at)
    VALUES (v_sessao, v_shop, 'Juliana Alves', CURRENT_DATE + time '08:45', 150.00, 'OPEN', now(), now());

    -- 5 cortes ja concluidos e cobrados hoje de manha. Mesma coerencia do historico de 60 dias:
    -- agendamento de origem -> comanda FECHADA -> um unico cash_movement por venda.
    FOR v_k IN 1 .. 5 LOOP
        v_ci := 1 + ((v_k * 11 + 3) % array_length(v_cust, 1));
        v_si := 1 + ((v_k * 5 + 1) % array_length(v_svc_nome, 1));
        v_hora := time '09:00' + (interval '50 minutes' * (v_k - 1));
        v_metodo := v_metodos[1 + (v_k % 6)];

        INSERT INTO appointments (customer_phone, customer_name, service, payment_status,
                                  notified, thanked, appointment_date, appointment_time,
                                  barbershop_id, professional_id, created_at, updated_at)
        VALUES (v_cust_fone[v_ci], v_cust_nome[v_ci], v_svc_nome[v_si],
                CASE WHEN v_metodo = 'PIX' THEN 'PIX' ELSE 'PRESENCIAL' END,
                true, true, CURRENT_DATE, v_hora, v_shop,
                v_prof[1 + (v_k % 3)], CURRENT_DATE + v_hora - interval '1 day', now())
        RETURNING id INTO v_agend;

        v_comanda := gen_random_uuid();
        v_total := v_svc_preco[v_si];

        INSERT INTO comandas (id, barbershop_id, customer_name, customer_phone, professional_id,
                              status, opened_at, comanda_date, appointment_id, created_at, updated_at)
        VALUES (v_comanda, v_shop, v_cust_nome[v_ci], v_cust_fone[v_ci],
                v_prof[1 + (v_k % 3)], 'ABERTA', CURRENT_DATE + v_hora, CURRENT_DATE, v_agend, now(), now());

        INSERT INTO comanda_items (id, comanda_id, barbershop_id, kind, reference_id, description,
                                   unit_price, quantity, total, created_at)
        VALUES (gen_random_uuid(), v_comanda, v_shop, 'SERVICO', NULL, v_svc_nome[v_si],
                v_svc_preco[v_si], 1, v_svc_preco[v_si], CURRENT_DATE + v_hora);

        -- Um em cada tres leva um produto junto.
        IF v_k % 3 = 0 THEN
            v_qi := 1 + (v_k % 5);
            IF (SELECT track_stock FROM products WHERE id = v_prod[v_qi]) THEN
                UPDATE products SET stock_quantity = stock_quantity - 1
                WHERE id = v_prod[v_qi] AND stock_quantity > 0
                RETURNING stock_quantity INTO v_estoque;
            ELSE
                v_estoque := NULL;
            END IF;

            v_total := v_total + v_prod_prec[v_qi];
            INSERT INTO comanda_items (id, comanda_id, barbershop_id, kind, reference_id, description,
                                       unit_price, quantity, total, created_at)
            VALUES (gen_random_uuid(), v_comanda, v_shop, 'PRODUTO', v_prod[v_qi], v_prod_nome[v_qi],
                    v_prod_prec[v_qi], 1, v_prod_prec[v_qi], CURRENT_DATE + v_hora);

            v_venda := gen_random_uuid();
            INSERT INTO product_sales (id, barbershop_id, product_id, product_name, unit_price,
                                       quantity, total, professional_id, payment_method,
                                       sale_date, sold_at, comanda_id, created_at)
            VALUES (v_venda, v_shop, v_prod[v_qi], v_prod_nome[v_qi], v_prod_prec[v_qi],
                    1, v_prod_prec[v_qi], v_prof[1 + (v_k % 3)], v_metodo,
                    CURRENT_DATE, CURRENT_DATE + v_hora, v_comanda, now());

            IF v_estoque IS NOT NULL THEN
                INSERT INTO stock_movements (id, barbershop_id, product_id, type, quantity,
                                             resulting_stock, product_sale_id, note, created_at)
                VALUES (gen_random_uuid(), v_shop, v_prod[v_qi], 'SAIDA', 1, v_estoque,
                        v_venda, 'Venda em comanda', CURRENT_DATE + v_hora);
            END IF;
        END IF;

        v_mov := gen_random_uuid();
        INSERT INTO cash_movements (id, session_id, barbershop_id, type, payment_method, amount, description, created_at)
        VALUES (v_mov, v_sessao, v_shop, 'VENDA', v_metodo, v_total,
                'Comanda de ' || v_cust_nome[v_ci], CURRENT_DATE + v_hora);

        UPDATE comandas
           SET status = 'FECHADA', total = v_total, payment_method = v_metodo,
               closed_at = CURRENT_DATE + v_hora + interval '35 minutes',
               cash_movement_id = v_mov
         WHERE id = v_comanda;
    END LOOP;

    INSERT INTO cash_movements (id, session_id, barbershop_id, type, payment_method, amount, description, created_at)
    VALUES (gen_random_uuid(), v_sessao, v_shop, 'SUPRIMENTO', NULL, 50.00, 'Troco', CURRENT_DATE + time '11:00');

    -- Comandas abertas: clientes na cadeira agora. Sem total nem forma de pagamento, como o
    -- CHECK do banco exige.
    FOR v_k IN 1 .. 2 LOOP
        v_comanda := gen_random_uuid();
        INSERT INTO comandas (id, barbershop_id, customer_name, customer_phone, professional_id,
                              status, opened_at, comanda_date, created_at, updated_at)
        VALUES (v_comanda, v_shop, v_cust_nome[10 + v_k], v_cust_fone[10 + v_k], v_prof[v_k],
                'ABERTA', CURRENT_DATE + time '14:00' + (interval '30 minutes' * v_k), CURRENT_DATE, now(), now());

        INSERT INTO comanda_items (id, comanda_id, barbershop_id, kind, reference_id, description,
                                   unit_price, quantity, total, created_at)
        VALUES (gen_random_uuid(), v_comanda, v_shop, 'SERVICO', NULL, v_svc_nome[v_k],
                v_svc_preco[v_k], 1, v_svc_preco[v_k], now());
    END LOOP;

    -- Mais alguns horarios reservados para o fim da tarde de hoje.
    FOR v_k IN 1 .. 3 LOOP
        v_ci := 1 + ((v_k * 13 + 7) % array_length(v_cust, 1));
        v_si := 1 + ((v_k * 4 + 2) % array_length(v_svc_nome, 1));
        v_hora := time '16:00' + (interval '45 minutes' * (v_k - 1));

        INSERT INTO appointments (customer_phone, customer_name, service, payment_status,
                                  notified, thanked, appointment_date, appointment_time,
                                  barbershop_id, professional_id, created_at, updated_at)
        VALUES (v_cust_fone[v_ci], v_cust_nome[v_ci], v_svc_nome[v_si], 'PENDENTE',
                false, false, CURRENT_DATE, v_hora, v_shop, v_prof[1 + (v_k % 3)], now(), now());
    END LOOP;

    -- -----------------------------------------------------------------------------------------
    -- 10. Agenda futura (proximos 10 dias, sem domingo)
    -- -----------------------------------------------------------------------------------------
    FOR v_i IN 1 .. 10 LOOP
        v_dia := CURRENT_DATE + v_i;
        CONTINUE WHEN extract(dow from v_dia) = 0;

        FOR v_k IN 1 .. (3 + (v_i % 3)) LOOP
            v_ci := 1 + ((v_i * 5 + v_k * 4) % array_length(v_cust, 1));
            v_si := 1 + ((v_i * 3 + v_k) % array_length(v_svc_nome, 1));

            INSERT INTO appointments (customer_phone, customer_name, service, payment_status,
                                      notified, thanked, appointment_date, appointment_time,
                                      barbershop_id, professional_id, created_at, updated_at)
            VALUES (v_cust_fone[v_ci], v_cust_nome[v_ci], v_svc_nome[v_si], 'PENDENTE',
                    false, false, v_dia, time '09:00' + (interval '45 minutes' * (v_k - 1)),
                    v_shop, v_prof[1 + ((v_i + v_k) % 3)], now(), now());
        END LOOP;
    END LOOP;

    -- -----------------------------------------------------------------------------------------
    -- 10b. Amanha e depois de amanha tambem ganham horarios a tarde — o laco acima so cobre a
    --      manha, e sem isso os dois dias mais proximos pareceriam vazios depois do meio-dia.
    -- -----------------------------------------------------------------------------------------
    FOR v_i IN 1 .. 2 LOOP
        v_dia := CURRENT_DATE + v_i;
        CONTINUE WHEN extract(dow from v_dia) = 0;

        FOR v_k IN 1 .. 3 LOOP
            v_ci := 1 + ((v_i * 19 + v_k * 6) % array_length(v_cust, 1));
            v_si := 1 + ((v_i * 2 + v_k) % array_length(v_svc_nome, 1));
            v_hora := time '14:00' + (interval '45 minutes' * (v_k - 1));

            INSERT INTO appointments (customer_phone, customer_name, service, payment_status,
                                      notified, thanked, appointment_date, appointment_time,
                                      barbershop_id, professional_id, created_at, updated_at)
            VALUES (v_cust_fone[v_ci], v_cust_nome[v_ci], v_svc_nome[v_si], 'PENDENTE',
                    false, false, v_dia, v_hora, v_shop, v_prof[1 + ((v_i + v_k) % 3)], now(), now());
        END LOOP;
    END LOOP;

    -- -----------------------------------------------------------------------------------------
    -- 11. Comissoes ja pagas (dois periodos fechados)
    -- -----------------------------------------------------------------------------------------
    FOR v_i IN 1 .. 3 LOOP
        INSERT INTO commission_payments (id, barbershop_id, professional_id, period_start, period_end,
                                         amount, notes, paid_by_name, paid_at, payment_date, created_at)
        VALUES
          (gen_random_uuid(), v_shop, v_prof[v_i], CURRENT_DATE - 60, CURRENT_DATE - 31,
           (900 + v_i * 137)::numeric, 'Acerto mensal', 'Ricardo Prata',
           (CURRENT_DATE - 30) + time '19:00', CURRENT_DATE - 30, now()),
          (gen_random_uuid(), v_shop, v_prof[v_i], CURRENT_DATE - 30, CURRENT_DATE - 1,
           (950 + v_i * 121)::numeric, 'Acerto mensal', 'Ricardo Prata',
           CURRENT_DATE + time '09:00', CURRENT_DATE, now());
    END LOOP;

    -- -----------------------------------------------------------------------------------------
    -- 12. Fila de espera, bloqueios de agenda e conversas
    -- -----------------------------------------------------------------------------------------
    INSERT INTO waitlist_entries (id, barbershop_id, customer_phone, customer_name, preferred_day, created_at)
    VALUES (gen_random_uuid(), v_shop, v_cust_fone[15], v_cust_nome[15], 'sábado de manhã', now() - interval '2 days'),
           (gen_random_uuid(), v_shop, v_cust_fone[16], v_cust_nome[16], 'sexta à tarde',   now() - interval '1 day');

    INSERT INTO schedule_blocks (id, barbershop_id, professional_id, block_date, weekday, start_time, end_time, reason, created_at, updated_at)
    VALUES
      -- Almoco todo dia util para o Rafael.
      (gen_random_uuid(), v_shop, v_prof[1], NULL, 3, time '12:00', time '13:00', 'Almoço', now(), now()),
      -- Folga pontual do Bruno.
      (gen_random_uuid(), v_shop, v_prof[2], CURRENT_DATE + 5, NULL, NULL, NULL, 'Folga', now(), now());

    INSERT INTO interactions (id, customer_id, barbershop_id, type, content, "timestamp", message_id)
    VALUES
      (gen_random_uuid(), v_cust[1], v_shop, 'USER', 'oi, tem horário hoje?',                    now() - interval '3 hours', 'demo-msg-1'),
      (gen_random_uuid(), v_cust[1], v_shop, 'BOT',  'Tenho sim! 14h ou 16h30, qual prefere?',   now() - interval '3 hours', 'demo-msg-2'),
      (gen_random_uuid(), v_cust[1], v_shop, 'USER', 'pode ser 16h30',                           now() - interval '3 hours', 'demo-msg-3'),
      (gen_random_uuid(), v_cust[2], v_shop, 'USER', 'qual o valor do combo?',                    now() - interval '1 day',   'demo-msg-4'),
      (gen_random_uuid(), v_cust[2], v_shop, 'BOT',  'O Corte + Barba sai por R$ 70,00.',         now() - interval '1 day',   'demo-msg-5');

    INSERT INTO scheduled_tasks (id, barbershop_id, customer_phone, execution_time, task_type, message_content, status, created_at, updated_at)
    VALUES
      (gen_random_uuid(), v_shop, v_cust_fone[5], now() + interval '20 hours', 'REMINDER',
       'Passando pra lembrar do seu horário amanhã.', 'PENDING', now(), now()),
      (gen_random_uuid(), v_shop, v_cust_fone[6], now() - interval '2 days', 'REVIEW_REQUEST',
       'E aí, curtiu o corte?', 'COMPLETED', now() - interval '2 days', now());

    -- -----------------------------------------------------------------------------------------
    -- 13. Fidelidade: pontos derivados das visitas que realmente existem
    --     Nada de numero inventado — o ponto vem da comanda fechada, que e o que o sistema conta.
    -- -----------------------------------------------------------------------------------------
    UPDATE customers c
       SET fidelity_points = sub.visitas % 10,
           last_service_date = sub.ultima
      FROM (SELECT customer_phone, count(*) AS visitas, max(closed_at) AS ultima
              FROM comandas
             WHERE barbershop_id = v_shop AND status = 'FECHADA'
             GROUP BY customer_phone) sub
     WHERE c.barbershop_id = v_shop AND c.phone_number = sub.customer_phone;

    -- -----------------------------------------------------------------------------------------
    -- 14. Contas a pagar (V31): o outro lado do caixa
    --     Sem despesa, o DRE da demo mostraria "sobrou = faturamento" — exatamente a leitura
    --     enganosa que a V31 existe para corrigir. Uma barbearia com ~R$ 8-9 mil de receita
    --     mensal paga cerca de R$ 5 mil de custo fixo; e essa proporcao que faz o relatorio
    --     parecer o de um negocio de verdade.
    --
    --     TUDO PAGO "POR FORA DO CAIXA" (cash_movement_id NULL), de proposito: gerar sangria
    --     aqui quebraria a invariante que o seed ja garante e verifica —
    --     closing_expected = fundo + suprimentos - sangrias + vendas em dinheiro. Alem disso e o
    --     realista: aluguel, energia e imposto saem por transferencia, nao da gaveta.
    -- -----------------------------------------------------------------------------------------
    FOR v_i IN 0..3 LOOP
        DECLARE
            v_mes  DATE := (date_trunc('month', CURRENT_DATE) - (v_i || ' months')::interval)::date;
            -- Meses passados quitados; o corrente fica em aberto. Como todos os vencimentos
            -- fixos caem entre os dias 4 e 19, do meio do mes em diante eles ja estao vencidos —
            -- por isso a conta "a vencer" e criada a parte, ancorada em CURRENT_DATE.
            v_fech BOOLEAN := v_i > 0;
        BEGIN
            INSERT INTO expenses (id, barbershop_id, description, category, amount, due_date, paid_at, recurring, notes, created_at)
            VALUES
              (gen_random_uuid(), v_shop, 'Aluguel da loja',      'ALUGUEL',   2500.00, v_mes + 4,
               CASE WHEN v_fech THEN v_mes + 4 ELSE NULL END, true, 'Contrato ate dez/2027', now()),

              (gen_random_uuid(), v_shop, 'Energia eletrica',     'ENERGIA',   380.00 + (v_i * 17), v_mes + 9,
               CASE WHEN v_fech THEN v_mes + 9 ELSE NULL END, true, NULL, now()),

              (gen_random_uuid(), v_shop, 'Agua',                 'AGUA',      118.00, v_mes + 9,
               CASE WHEN v_fech THEN v_mes + 9 ELSE NULL END, true, NULL, now()),

              (gen_random_uuid(), v_shop, 'Internet e telefone',  'INTERNET',  199.90, v_mes + 14,
               CASE WHEN v_fech THEN v_mes + 14 ELSE NULL END, true, NULL, now()),

              (gen_random_uuid(), v_shop, 'Sistema Razor',        'SOFTWARE',  139.90, v_mes + 19,
               CASE WHEN v_fech THEN v_mes + 19 ELSE NULL END, true, 'Plano Equipe', now()),

              (gen_random_uuid(), v_shop, 'Reposicao de produtos','PRODUTOS',  760.00 + (v_i * 40), v_mes + 11,
               CASE WHEN v_fech THEN v_mes + 11 ELSE NULL END, true, NULL, now()),

              (gen_random_uuid(), v_shop, 'Simples Nacional',     'IMPOSTOS',  610.00 + (v_i * 25), v_mes + 19,
               CASE WHEN v_fech THEN v_mes + 19 ELSE NULL END, true, NULL, now());

            -- Despesas eventuais: nem todo mes tem, e nenhuma e recorrente. Sao elas que fazem o
            -- resultado oscilar de um mes para o outro — se tudo fosse fixo, o grafico seria reta.
            IF v_i % 2 = 0 THEN
                INSERT INTO expenses (id, barbershop_id, description, category, amount, due_date, paid_at, recurring, notes, created_at)
                -- Sempre paga: e servico pre-pago, nao roda no boleto.
                VALUES (gen_random_uuid(), v_shop, 'Impulsionamento no Instagram', 'MARKETING', 300.00,
                        v_mes + 7, v_mes + 7, false, NULL, now());
            END IF;
            IF v_i = 1 THEN
                INSERT INTO expenses (id, barbershop_id, description, category, amount, due_date, paid_at, recurring, notes, created_at)
                VALUES (gen_random_uuid(), v_shop, 'Conserto da cadeira 2', 'MANUTENCAO', 450.00, v_mes + 21,
                        v_mes + 21, false, 'Pistao hidraulico', now());
            END IF;

            -- Uma conta que ainda VAI vencer, so no mes corrente. Ancorada em CURRENT_DATE (e
            -- nao num dia fixo) porque o seed roda em qualquer dia do mes: com dia fixo, rodar
            -- no fim do mes deixaria a tela sem o estado "em aberto, a vencer".
            IF v_i = 0 THEN
                INSERT INTO expenses (id, barbershop_id, description, category, amount, due_date, paid_at, recurring, notes, created_at)
                VALUES (gen_random_uuid(), v_shop, 'Toalhas e descartaveis', 'PRODUTOS', 240.00,
                        LEAST((date_trunc('month', CURRENT_DATE) + interval '1 month - 1 day')::date,
                              CURRENT_DATE + 5),
                        NULL, false, NULL, now());
            END IF;
        END;
    END LOOP;

    RAISE NOTICE 'Seed da demo concluido para a barbearia % (slug demo-navalha).', v_shop;
END
$seed$;
