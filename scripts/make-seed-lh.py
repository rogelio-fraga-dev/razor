"""Gera scripts/seed-lh.sql e scripts/seed-lh-extra.sql (demonstração da LH Barbearia, Araguari)
a partir do seed da demo. Dados de clientes e movimento são FICTÍCIOS; nome, cidade e identidade
visual são da barbearia, só para a apresentação local.

Uso: python scripts/make-seed-lh.py   (depois rode os dois .sql no banco LOCAL)
"""
import os
import re

ROOT = os.path.dirname(os.path.abspath(__file__))


def must(s, a, b, count=1):
    assert a in s, a[:80]
    return s.replace(a, b, count)


def common(s):
    s = s.replace("dededede-0000-4000-8000-", "a1a1a1a1-0000-4000-8000-")
    s = s.replace("Barbearia Navalha de Prata (demo)", "LH Barbearia")
    s = s.replace("demo-navalha", "lh-barbearia").replace("DemoNavalhaWpp", "LhBarbeariaDemo")
    s = s.replace("navalhademo.local", "lhbarbearia.local")
    s = s.replace("Navalha de Prata", "LH Barbearia")
    # Dois barbeiros: o dono (LH) e o Vinícius. O terceiro some e a divisão fica entre os dois.
    s = s.replace("'Rafael Tesoura'", "'Luiz'").replace("'Bruno Navalha'", "'Vinícius'")
    s = re.sub(r"v_prof\[1 \+ \(\(v_i \+ v_k\) % 3\)\]", "v_prof[1 + ((v_i + v_k) % 2)]", s)
    s = re.sub(r"v_prof\[1 \+ \(v_k % 3\)\]", "v_prof[1 + (v_k % 2)]", s)
    s = s.replace("v_prof[3]", "v_prof[2]")
    s = s.replace("v_prof[1 + (g % 3)]", "v_prof[1 + (g % 2)]")
    s = s.replace("'Vale — Caio Máquina'", "'Vale — Vinícius'").replace("O Bruno entendeu", "O Vinícius entendeu")
    return s


src = open(os.path.join(ROOT, "seed-demo.sql"), encoding="utf-8").read()
s = common(src)
s = must(s, "E'📍 *Barbearia LH Barbearia*\\nRua das Tesouras, 456 - Centro\\nCidade Exemplo - SP, 01000-000'",
         "E'📍 *LH Barbearia*\\nAraguari - MG'")
# Remove o terceiro barbeiro e fecha a lista no Vinícius.
s = re.sub(r"\n      \(v_prof\[\d\], v_shop, 'Caio Máquina'[^\n]*", "", s)
s = must(s, "      (v_prof[2], v_shop, 'Vinícius',  '5511999990003', true, 45.00, NULL,  now() - interval '12 months', now()),",
         "      (v_prof[2], v_shop, 'Vinícius',  '5511999990003', true, 45.00, NULL,  now() - interval '12 months', now());")
s = s.replace("'Juliana Alves',  'RECEPTION'", "'Recepção',  'RECEPTION'").replace("'Juliana Alves'", "'LH'")
s = s.replace("'Ricardo Prata'", "'Luiz'")
s = must(s, "    v_svc_preco := ARRAY[45.00, 35.00, 70.00, 90.00, 60.00, 20.00, 20.00];",
         "    v_svc_preco := ARRAY[40.00, 30.00, 65.00, 70.00, 45.00, 15.00, 15.00];")
# 5 meses de história em vez de 2
s = must(s, "FOR v_i IN REVERSE 60 .. 1 LOOP", "FOR v_i IN REVERSE 150 .. 1 LOOP")
s = must(s, "    FOR v_i IN 1 .. 3 LOOP\n        INSERT INTO commission_payments", "    FOR v_i IN 1 .. 2 LOOP\n        INSERT INTO commission_payments")
# Clube "LH Barberclub"
s = must(s, "(v_plan_bronze, v_shop, 'Bronze', 69.90,  'Corte ilimitado de segunda a quarta.',        true, 7,",
         "(v_plan_bronze, v_shop, 'Barberclub Corte', 89.90,  'Corte ilimitado de segunda a quinta.',   true, 7,")
s = must(s, "(v_plan_silver, v_shop, 'Silver', 94.90,  'Corte e barba de segunda a sexta.',           true, 14,",
         "(v_plan_silver, v_shop, 'Barberclub Corte + Barba', 129.90, 'Corte e barba ilimitados de segunda a sexta.', true, 14,")
s = must(s, "(v_plan_vip,    v_shop, 'VIP',    119.90, 'Tudo ilimitado, inclusive sábado, com 20% nos produtos.', true, 30,",
         "(v_plan_vip,    v_shop, 'Barberclub Black', 159.90, 'Tudo ilimitado, inclusive sábado, visagismo e 20% nos produtos.', true, 30,")
s = s.replace("'1,2,3',       now(), now()),\n      -- Silver", "'1,2,3,4',     now(), now()),\n      -- Silver")
# ~1/3 dos clientes no clube (antes só 3 assinantes)
s = must(s, "CASE v_i WHEN 1 THEN v_plan_vip WHEN 2 THEN v_plan_silver WHEN 3 THEN v_plan_bronze ELSE NULL END,",
         "CASE v_i % 9 WHEN 1 THEN v_plan_vip WHEN 2 THEN v_plan_silver WHEN 3 THEN v_plan_bronze ELSE NULL END,")
# Luiz é dono e barbeiro: o login do dono fica ligado ao profissional Luiz; o de barbeiro é o Vinícius.
s += """
DO $lh$
BEGIN
    UPDATE barbershops SET signup_plan = 'PRO', status = 'ACTIVE', trial_ends_at = NULL
     WHERE id = 'a1a1a1a1-0000-4000-8000-000000000001';
    UPDATE tenant_users SET name = 'Vinícius', email = 'vinicius@lhbarbearia.local',
           professional_id = 'a1a1a1a1-0000-4000-8000-000000000032'
     WHERE barbershop_id = 'a1a1a1a1-0000-4000-8000-000000000001' AND role = 'BARBER';
    UPDATE tenant_users SET professional_id = 'a1a1a1a1-0000-4000-8000-000000000031'
     WHERE barbershop_id = 'a1a1a1a1-0000-4000-8000-000000000001' AND role = 'OWNER';
END
$lh$;
"""
open(os.path.join(ROOT, "seed-lh.sql"), "w", encoding="utf-8", newline="\n").write(
    "-- GERADO por scripts/make-seed-lh.py a partir de seed-demo.sql (demonstração da LH Barbearia).\n" + s)

ex = common(open(os.path.join(ROOT, "seed-demo-extra.sql"), encoding="utf-8").read())
open(os.path.join(ROOT, "seed-lh-extra.sql"), "w", encoding="utf-8", newline="\n").write(
    "-- GERADO por scripts/make-seed-lh.py a partir de seed-demo-extra.sql.\n" + ex)
left = [m for m in ["Rafael", "Bruno", "Caio", "Navalha", "navalhademo", "dededede", "% 3)]"] if m in s or m in ex]
print("ok · resquícios:", left or "nenhum")
