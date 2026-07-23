# 📡 Canal de WhatsApp — Evolution vs. API Oficial (Cloud API)

> Pesquisa 2026-07-23. Responde: **até quantos números/instâncias temos de graça, quando
> começa a pagar, e qual a estratégia por fase do Razor.** Fontes ao final.

Relacionado: [PLATFORM](./PLATFORM.md) · [PRICING](./PRICING.md) · [ROADMAP](./ROADMAP.md)

---

## 1. Evolution API (o que usamos hoje)

| Pergunta | Resposta |
|---|---|
| O software é pago? | **Não. 100% open source e gratuito.** Não existe "Evolution pago" para o software em si — o que existe são *terceiros* vendendo hospedagem gerenciada dele. Nós já self-hosteamos (container `razor-evolution`), então nosso único custo é o **nosso servidor**. |
| Limite de instâncias (números)? | **Sem limite de licença.** Uma instância por barbearia, quantas o nosso servidor aguentar (cada instância ≈ 100–300MB RAM; um VPS de 8GB segura dezenas). |
| Como funciona? | Emula o **WhatsApp Web** (lib Baileys). Por isso o pareamento é por QR code — igual conectar o WhatsApp Web. |
| Qual o risco? | **Opera FORA dos termos oficiais do WhatsApp** → risco real de **banimento do número** da barbearia, principalmente com disparos em massa. Não há SLA nem suporte da Meta. |
| Evolution também fala com a API oficial? | Sim — a Evolution suporta integração `WHATSAPP-BUSINESS` (Cloud API) além do Baileys. Ou seja: **podemos migrar tenant a tenant para o canal oficial sem trocar toda a stack**, o que reduz o custo da migração futura. |

**Resumo Evolution: grátis e ilimitado (só infra nossa), com risco de ban.**

---

## 2. WhatsApp Cloud API (oficial da Meta)

### 2.1. O que é grátis vs. pago (regras desde jul/2025: preço POR MENSAGEM)

| Tipo de mensagem | Custo | No uso do Razor |
|---|---|---|
| **Serviço** — qualquer resposta ao cliente na janela de 24h após a última mensagem dele | **GRÁTIS, sem teto mensal** | ✅ **É a maior parte do nosso tráfego**: o cliente chama, a IA responde — tudo grátis |
| Template **Utility** (lembrete/confirmação iniciados por nós) | ~US$ 0,0068/msg no Brasil (com desconto por volume acima de 1.000/mês) | Lembrete de agendamento 1h antes, pedido de avaliação |
| Template **Marketing** (promoções/disparos) | ~US$ 0,0625/msg no Brasil (sem desconto por volume) | Disparos em massa do admin — o item mais caro; cotar por plano |
| Template **Autenticação** | ~US$ 0,0315/msg | Não usamos |
| Click-to-WhatsApp (anúncio → conversa) | **72h inteiramente grátis**, inclusive templates | Útil para campanhas das barbearias |
| Registro do número / uso da API | Grátis (direto com a Meta, sem BSP) | BSPs intermediários cobram +US$ 0,003–0,010/msg — **evitar: integrar direto** |

### 2.2. Limites de ENVIO (mensagens iniciadas pela empresa, por 24h móveis)

Os limites valem para **conversas iniciadas por nós** (templates). Respostas na janela de
24h **não contam** no limite.

| Estágio | Limite (usuários únicos/24h) |
|---|---|
| Portfólio **sem verificação** da empresa na Meta | **250** |
| Verificado — Tier 1 | **1.000** |
| Tier 2 (escala automática) | 10.000 |
| Tier 3 | 100.000 |
| Tier 4 | Ilimitado |

- A escalada é **automática**: usando ~50% do limite por 7 dias com boa qualidade, sobe de tier.
- Desde out/2025 o limite é **por portfólio de negócio** (não por número).

### 2.3. Limite de NÚMEROS (o ponto crítico para o multi-tenant)

| Situação | Números de telefone permitidos |
|---|---|
| Portfólio novo, sem verificação | **2** |
| Após verificação da empresa (ou tier 2K) | **20** (dá para pedir mais, com justificativa) |

⚠️ **Implicação de arquitetura:** se registrássemos os números de TODAS as barbearias no
**nosso** portfólio, bateríamos no teto de 20 números e todos dividiriam o mesmo limite de
envio. O caminho certo em escala é o modelo **Tech Provider + Embedded Signup**: cada
barbearia tem o **próprio** WABA/portfólio (com seus próprios limites: 250 → 1.000+), e o
Razor é o provedor de tecnologia que opera em nome dela. É como Frizzar/CashBarber fariam.

---

## 3. Custo estimado por barbearia no canal OFICIAL (câmbio R$ 5,50)

Cenário: ~300 lembretes utility + ~200 confirmações/mês + 1 campanha marketing de 300 msgs.

| Item | Cálculo | Custo/mês |
|---|---|---|
| Conversas de atendimento (IA respondendo) | grátis | **R$ 0** |
| ~500 utility | 500 × US$ 0,0068 | ~R$ 19 |
| 300 marketing (opcional, cobrável do tenant) | 300 × US$ 0,0625 | ~R$ 103 → **repassar/limitar por plano** |

➡️ Sem marketing pesado, o canal oficial custa **~R$ 15–25/barbearia/mês** — cabe na margem
([PRICING §3](./PRICING.md)). Disparos de marketing devem ter **cota por plano** e excedente
cobrado, senão um tenant "disparador" come a margem.

---

## 4. Estratégia por fase (decisão)

| Fase | Canal | Racional |
|---|---|---|
| **Piloto/beta (agora, ≤ ~20 barbearias)** | **Evolution (grátis)** | Custo zero, QR self-service já pronto; risco de ban aceitável no beta — avisar o tenant no onboarding e evitar marketing agressivo |
| **Tração (20–100)** | Híbrido: novos tenants no **Cloud API** via Tech Provider/Embedded Signup; migração gradual dos antigos | A Evolution já suporta a integração oficial → migração tenant a tenant |
| **Escala (100+)** | **100% Cloud API** | Estabilidade vira requisito de venda; custo ~R$ 15–25/tenant embutido no plano |

**Pré-requisitos para a fase oficial:** verificar a empresa Razor na Meta (tira o limite de
250) · virar **Tech Provider** (Embedded Signup) · interface `WhatsAppGateway` no código
para alternar canal por tenant (já previsto no [ROADMAP Fase 4](./ROADMAP.md)).

---

## Fontes

- [GitHub — Evolution API (open source)](https://github.com/evolution-foundation/evolution-api)
- [Evolution API — deep dive self-hosted](https://kevinkiarie7.medium.com/deep-dive-building-self-hosted-whatsapp-automation-with-evolution-api-1fa434b98ce1)
- [Evolution API — visão geral/limitações](https://gurusup.com/blog/evolution-api-whatsapp)
- [WhatsApp Business API Pricing Brazil 2026 (BRL)](https://whautomate.com/whatsapp-business-api-pricing-brazil)
- [WhatsApp API Pricing 2026 — o que mudou (per-message)](https://blueticks.co/blog/whatsapp-business-api-pricing-2026)
- [WhatsApp API Pricing Brazil — Meta rates](https://ominiflow.com/whatsapp-api-pricing/brazil)
- [Meta for Developers — Messaging Limits](https://developers.facebook.com/docs/whatsapp/messaging-limits/)
- [Manychat — como funcionam os messaging limits](https://help.manychat.com/hc/en-us/articles/14281346817308-WhatsApp-Messaging-Limits-How-they-work-and-how-to-scale-effectively)
- [Vonage — limites de números por WABA](https://api.support.vonage.com/hc/en-us/articles/13159743458460-WhatsApp-Platform-Phone-Number-Limits)
- [Chatarmin — messaging limits 2026](https://chatarmin.com/en/blog/whats-app-messaging-limits)

_Documento vivo — revisar preços/limites a cada trimestre._
