# 💵 Custos & Preços — Razor

> Objetivo: garantir que o Razor **dá lucro**, não prejuízo. Este documento modela os
> custos reais de operar o SaaS e define preços com margem saudável.
>
> ⚠️ **Números são estimativas de 2026-07** para dimensionar o negócio. Confirmar sempre
> nos preços vigentes antes de fechar orçamento. Câmbio assumido: **US$ 1 ≈ R$ 5,50**.

---

## 1. De onde vêm os custos

O custo de rodar o Razar tem duas naturezas:

- **Custo fixo (plataforma):** servidor, banco, domínio — independe de quantas barbearias.
- **Custo variável (por barbearia/mês):** IA, WhatsApp, gateway de pagamento — cresce com uso.

### A grande virada: nossos dois maiores medos são baratos

| Medo | Realidade descoberta |
|---|---|
| "Cada conversa no GPT vai custar caro" | Com **`gpt-4o-mini`** + **prompt caching**, uma conversa custa **centavos**. GPT-4o só nos casos complexos. |
| "WhatsApp vai cobrar cada mensagem" | Na **API oficial (Cloud API)**, **resposta do bot ao cliente em até 24h é GRÁTIS**. Só pagamos lembrete/marketing proativo. |

---

## 2. Custo variável por barbearia (estimativa realista)

Cenário: barbearia pequena/média, **~400 conversas de cliente/mês** (agendamentos + dúvidas).

### 2.1. IA (OpenAI)

Preços: GPT-4o `$2,50/1M` input · `$10/1M` output · **gpt-4o-mini `$0,15/1M` input · `$0,60/1M` output**.

Conversa média ≈ 8 turnos; por turno ≈ 1.500 tokens de entrada (prompt + histórico + agenda) + 200 de saída → ~12k in / 1,6k out por conversa.

| Estratégia | Custo/conversa | 400 conversas/mês |
|---|---|---|
| Tudo em GPT-4o | ~US$ 0,046 | ~US$ 18 (**R$ ~100**) |
| **Híbrido (mini 80% + 4o 20%)** ✅ | ~US$ 0,011 | ~US$ 4,5 (**R$ ~25**) |
| Só gpt-4o-mini | ~US$ 0,003 | ~US$ 1,2 (**R$ ~7**) |
| Transcrição de áudio (Whisper) | $0,006/min | ~R$ 1 |

➡️ **Meta de arquitetura: rotear por padrão para `gpt-4o-mini`**, escalar para `gpt-4o`
só quando a intenção é ambígua. Custo alvo de IA: **R$ 10–30/barbearia/mês**.

### 2.2. WhatsApp (Cloud API oficial)

| Tipo de mensagem | Custo | No nosso uso |
|---|---|---|
| **Resposta ao cliente (< 24h)** | **Grátis** | Maior parte do tráfego do bot |
| Lembrete/confirmação (Utility template) | ~US$ 0,008 | ~300/mês → ~R$ 13 |
| Marketing/promoção (Marketing template) | ~US$ 0,0625 | Opcional, controlado pelo dono |
| Autenticação | ~US$ 0,0315 | Não usamos |

➡️ Custo WhatsApp típico: **R$ 10–20/mês** (sem marketing pesado).

> **Trade-off de canal:** a **Evolution API** (não-oficial, usada hoje) é grátis mas tem
> **risco de banimento** do número. A **Cloud API oficial** tem custo baixo mas é estável e
> escalável. Decisão em [`PLATFORM.md`](./PLATFORM.md#canal-de-whatsapp).

### 2.3. Hospedagem (amortizada por barbearia)

Um servidor + Postgres gerenciado atende **muitas** barbearias (arquitetura multi-tenant).

| Escala | Custo fixo infra/mês | Por barbearia |
|---|---|---|
| 1–10 barbearias | ~R$ 400 | ~R$ 40–400 |
| 50 barbearias | ~R$ 700 | ~R$ 14 |
| 200 barbearias | ~R$ 1.500 | ~R$ 7,50 |

➡️ No início o custo fixo pesa; escala dilui rápido.

### 2.4. Gateway de pagamento (cobrança da assinatura do barbeiro)

Asaas / Mercado Pago / Stripe: ~3–4% + R$ 0,40–1,00. Numa assinatura de R$ 150 → **~R$ 6**.

### 📌 Resumo do custo variável por barbearia/mês

| Item | Baixa escala (poucas) | Em escala (50+) |
|---|---|---|
| IA | R$ 10–30 | R$ 10–30 |
| WhatsApp | R$ 10–20 | R$ 10–20 |
| Hospedagem (amortizada) | R$ 40–400 | R$ 7–14 |
| Gateway | R$ 6 | R$ 6 |
| **Total/barbearia** | **R$ 70–450** | **R$ 35–70** |

O que come a margem no início **não é a IA nem o WhatsApp — é o custo fixo de infra
diluído em poucas barbearias.** Por isso o foco em conquistar as primeiras dezenas.

---

## 3. Preços (planos do Razor) — v2, lançamento

Referência de mercado: Frizzar R$ 57–249 · Booksy R$ 99,90 + R$ 20/prof · CashBarber a partir de R$ 300.

> **v2 (2026-07-23).** Preços reduzidos para estratégia de **penetração no lançamento**
> (decisão do dono: "os planos estão caros"). A tabela v1 (97/197/347) fica como alvo
> pós-tração — subir preço com prova social é mais fácil que baixar sem.

| Plano | Público | Preço/mês (lançamento) | Anual (2 meses grátis) | Inclui |
|---|---|---|---|---|
| **Solo** | 1 barbeiro | **R$ 69,90** | R$ 58,25/mês | IA WhatsApp, agenda, **site próprio (3 templates)**, fidelidade, PIX, lembretes, painel |
| **Equipe** | até 5 barbeiros | **R$ 139,90** | R$ 116,58/mês | Tudo do Solo + multiprofissional, comissões, financeiro |
| **Pro** | rede / ilimitado | **R$ 229,90** | R$ 191,58/mês | Tudo + clube de assinatura, relatórios avançados, multi-unidade |

**Margem (escala, custo variável §2):** Solo ~R$ 30 (43%) · Equipe ~R$ 85 (61%) · Pro ~R$ 160 (70%).
Break-even da plataforma sobe para ~**12–18 barbearias** (vs. 8–14 na v1) — aceitável pelo
ganho de conversão na entrada.

### Receitas avulsas (fora da mensalidade)

| Serviço | Modelo | Preço sugerido |
|---|---|---|
| **Site personalizado** (fora dos 3 templates: design exclusivo feito por nós) | one-time, sob consulta | a partir de **R$ 497** |
| Migração assistida de outro sistema (feita por nós, além do CSV self-service) | one-time | R$ 97 |

- **Onboarding self-service gratuito** (sem taxa de setup) — argumento de conquista.
- **Fair-use:** cada plano inclui uma cota de conversas de IA/mês; excedente em faixas,
  para proteger a margem contra um tenant muito pesado (ver §5).
- **O custo de IA é da PLATAFORMA** (chave OpenAI global, do Razor — nunca do tenant),
  embutido na mensalidade e monitorado por tenant no console do dono (§5).

---

## 4. Margem e break-even

| Plano | Preço | Custo variável (escala) | **Margem bruta** |
|---|---|---|---|
| Solo | R$ 97 | ~R$ 40 | ~59% |
| Equipe | R$ 197 | ~R$ 55 | ~72% |
| Pro | R$ 347 | ~R$ 70 | ~80% |

**Break-even da plataforma:** custo fixo inicial ~R$ 500–800/mês (infra + ferramentas + domínio).
Com margem média ~R$ 55–130/barbearia, o ponto de equilíbrio fica em torno de
**8–14 barbearias pagantes**. A partir daí, cada nova barbearia é majoritariamente lucro,
pois o custo fixo já está coberto e o variável escala barato.

---

## 5. Alavancas para nunca ter prejuízo

1. **Rotear para `gpt-4o-mini` por padrão** (escalar para 4o só quando necessário) — maior alavanca de custo de IA.
2. **Prompt caching** do system prompt + menu (−90% no input repetido).
3. **API oficial do WhatsApp** — respostas grátis; pagar só template proativo.
4. **Fair-use por plano** — cota de conversas de IA + cobrança de excedente em faixas.
5. **Alerta de custo por tenant** no console do dono — enxergar quem consome muito.
6. **Amortizar infra** com eficiência multi-tenant (um deploy, muitas barbearias).
7. **Cobrança antecipada** (mensal/anual) via gateway — evita inadimplência corroer caixa.

---

## Fontes

- [OpenAI API Pricing](https://developers.openai.com/api/docs/pricing)
- [WhatsApp Business API Pricing Brazil 2026](https://whautomate.com/whatsapp-business-api-pricing-brazil)
- [WhatsApp Business API Pricing 2026 — categorias e mudanças](https://blueticks.co/blog/whatsapp-business-api-pricing-2026)

---

_Documento vivo — revisar preços de insumos a cada trimestre._
