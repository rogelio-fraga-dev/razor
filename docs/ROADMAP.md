# 🪒 Razor — Roadmap de Produto

> **Razor** é um SaaS de gestão e atendimento para barbearias e centros de estética.
> Nascido como um bot de atendimento por IA no WhatsApp, está evoluindo para uma
> plataforma multi-tenant completa que compete de igual para igual com **Frizzar**
> e **CashBarber** — com um diferencial que eles não têm de forma nativa:
> **atendimento conversacional por Inteligência Artificial**.

---

## 🎯 Visão

Fazer um barbeiro olhar para o Razor e pensar:

> _"Nossa, eu realmente posso trocar meu Frizzar por esse aqui."_

Para isso, o Razor precisa de **paridade de gestão** (financeiro, comissões, clube de
assinatura, agenda multiprofissional) **somada** ao que já nos torna únicos: um
assistente de IA que agenda, negocia horários, transcreve áudios, gerencia lista de
espera e retém clientes — tudo dentro do WhatsApp, sem app para o cliente baixar.

### Posicionamento

| | Foco principal | Ponto fraco |
|---|---|---|
| **Frizzar** | Gestão completa (financeiro, estoque, agenda) | Atendimento por IA é um "chatbot" recente e limitado |
| **CashBarber** | Retenção + mentoria + gestão | Sem atendimento conversacional nativo |
| **Razor** | **IA de atendimento nativa** + gestão | Camada de gestão ainda em construção (este roadmap) |

**Estratégia de cunha (wedge):** entramos pela dor que os concorrentes não resolvem
bem — atendimento e agendamento automáticos por IA no WhatsApp — e construímos a
camada de gestão em volta para não dar motivo de o barbeiro ficar no concorrente.

Ver análise detalhada em [`COMPETITIVE_ANALYSIS.md`](./COMPETITIVE_ANALYSIS.md).

### Três níveis de acesso

O Razor é uma plataforma B2B2C com três públicos distintos:

| Nível | Quem | Onde | O que faz |
|---|---|---|---|
| **Plataforma (você)** | Dono do Razor | Console do dono + site institucional | Vê todas as barbearias, faturamento do SaaS, planos, vendas, suporte, provisionamento |
| **Barbearia (tenant)** | Dono/equipe da barbearia | Painel web + WhatsApp admin | Gere sua barbearia: agenda, financeiro, comissões, assinaturas, marca |
| **Cliente final** | Cliente da barbearia | WhatsApp + site da barbearia | Agenda, tira dúvidas, é atendido pela IA |

A venda e a entrega são **100% via web** (self-service): o barbeiro assina online, paga,
e a barbearia é **provisionada automaticamente** — sem visita presencial. Detalhes em
[`PLATFORM.md`](./PLATFORM.md).

---

## 🗺️ Fases

O roadmap é sequencial por dependência: cada fase assenta sobre a anterior. A **Fase 0**
existe porque hoje não há testes nem CI, e refatorar/escalar sem essa rede de segurança
é o que vinha gerando a sequência de "corrige bug crítico" no histórico.

### Fase 0 — Fundação de Engenharia 🏗️ *(em andamento)*

> **Objetivo:** parar de apagar incêndio. Criar a base técnica que permite evoluir com
> segurança e velocidade.

- [x] **Higiene do repo** — removidos ~900KB de logs commitados, `.gitignore` reforçado.
- [x] **Rebrand para Razor** — pacote `com.barberbot` → `com.razor`, config, `pom.xml`. Compila limpo.
- [x] **CI/CD** — GitHub Actions (build + testes + cobertura JaCoCo) + Dependabot.
- [x] **Harness de testes** — JUnit 5 + AssertJ + JaCoCo (16 testes verdes).
- [~] **Testes das regras críticas** — ✅ parsing de tags da IA e normalização de telefone
      (extraídos e testados); ⏳ pendente: agenda (agendar/cancelar/lista de espera) e
      máquina de estados do cliente.
- [~] **Refatorar as God classes** — ✅ extraídos `AiTagParser`/`AiIntent` e `PhoneNumbers`
      do `OrchestratorService`; ⏳ pendente a divisão maior (WebhookRouter, AdminCommandService,
      CustomerConversationService) e a quebra do `AgendaService`.
- [x] **Corrigir erros engolidos** — eliminados os 7 `catch (Exception e) {}` vazios.
- [x] **Tipos de domínio fortes** — ✅ `PaymentStatus` como `enum`; ✅ data/hora do agendamento
      como `LocalDate`/`LocalTime` (Opção A: data completa com ano, inferindo a próxima ocorrência
      futura de DD/MM — corrige o bug de virada de ano e remove o sentinela `"hoje"`).
      Migração Flyway `V3` criada. ⚠️ **Validar a `V3` e o fluxo JPA contra um Postgres real
      (`docker-compose up`) antes de mergear** — não há banco no ambiente de dev atual.

**Pronto quando:** `./mvnw clean verify` roda testes verdes com relatório de cobertura,
CI verde em PR, e nenhuma das God classes originais existe mais como monólito.

---

### Fase 1 — Núcleo Multi-Tenant 🏢

> **Objetivo:** deixar de ser "um sistema para uma barbearia" e virar "uma plataforma
> onde N barbearias operam isoladas". Este é o pré-requisito de qualquer venda SaaS.

- [ ] Entidade `Barbershop` (tenant): nome, CNPJ, endereço, chave PIX, marca, plano.
- [ ] `tenant_id` em **todas** as tabelas + isolamento de dados garantido no acesso.
- [ ] Trocar `UNIQUE(phone_number)` global por `UNIQUE(tenant_id, phone_number)`.
- [ ] Entidade `Professional` (barbeiro): agenda própria, folgas, especialidades.
- [ ] Catálogo `Service` por tenant: nome, **preço**, **duração**, categoria.
- [ ] Uma instância da Evolution API / número de WhatsApp por tenant (roteamento).
- [ ] Onboarding: criar barbearia, primeiro admin e primeiros serviços.

**Pronto quando:** duas barbearias distintas operam no mesmo deploy sem enxergar dados
uma da outra, cada uma com seu WhatsApp, serviços e barbeiros.

---

### Fase 2 — Paridade de Gestão 💰

> **Objetivo:** cobrir o que faz um barbeiro pagar Frizzar/CashBarber hoje.

- [ ] **Financeiro**: caixa/comandas, formas de pagamento, fechamento diário.
- [ ] **Comissões** automáticas por profissional (por serviço e por produto).
- [ ] **Clube de assinatura** (recorrência): planos configuráveis (VIP/Silver/Bronze),
      identificação automática de assinante x avulso no agendamento, cobrança recorrente.
- [ ] **Relatórios/DRE** e **dashboard de retenção** ("quem some", ticket médio, frequência).
- [ ] **Estoque** (opcional): produtos, reposição, saídas.

**Pronto quando:** um barbeiro consegue fechar o mês (faturamento, comissões,
assinaturas) sem sair do Razor.

---

### Fase 3 — Painel Web & Site da Barbearia 🌐

> **Objetivo:** dar ao dono da barbearia uma gestão web de verdade (paridade com
> Frizzar/CashBarber) e presença própria além do WhatsApp.

- [ ] **Painel web de gestão** do tenant: agenda multiprofissional, financeiro, comissões,
      assinaturas, clientes, configuração da barbearia e do bot. _(O que hoje é comando de WhatsApp.)_
- [ ] Usuários e papéis do painel (dono, recepção, barbeiro).
- [ ] Site/landing **white-label** por barbearia (marca, serviços, endereço, avaliações).
- [ ] Página de **agendamento online** (link próprio) na mesma agenda do bot.

**Pronto quando:** o dono gerencia toda a barbearia por um painel web, e tem site + link
de agendamento próprios — sem depender de comandos no WhatsApp.

---

### Fase 4 — Plataforma Comercial (venda & entrega via web) 🛰️

> **Objetivo:** vender e entregar o Razor 100% pela web, sem visita presencial, e dar a
> você controle total como dono da plataforma. Detalhes em [`PLATFORM.md`](./PLATFORM.md).

- [ ] **Console do Dono (super-admin):** todas as barbearias, MRR, churn, custo por tenant, suporte.
- [ ] **Site institucional** `razor.com.br`: proposta, planos, demonstração, prova social, CTA.
- [ ] **Cadastro self-service** + **billing do SaaS** (gateway BR: Asaas / Mercado Pago / Stripe; cartão/PIX; recorrência).
- [ ] **Provisionamento automático** do tenant ao confirmar pagamento.
- [ ] **Conexão de WhatsApp por QR code remoto** (o que substitui a visita presencial).
- [ ] Interface `WhatsAppGateway` para trocar Evolution ↔ Cloud API oficial sem reescrever o núcleo.

**Pronto quando:** um barbeiro de outra cidade assina, paga, conecta o WhatsApp por QR e
começa a operar — tudo sozinho, e você acompanha pelo console do dono.

---

### Fase 5 — Diferenciais de IA 🧠

> **Objetivo:** ampliar a distância para os concorrentes usando nossa cunha.

- [ ] Inteligência de retenção proativa (IA identifica e reengaja quem vai sumir).
- [ ] Upsell/cross-sell por IA (sugere serviços/planos no contexto da conversa).
- [ ] Roteiros/persona do bot configuráveis por tenant (tom de voz da marca).

---

### Fase 6 — Escala & Operação ⚙️

> **Objetivo:** aguentar muitas barbearias com confiança operacional.

- [ ] Tirar o estado da conversa da memória (hoje em `static Map`) → Redis/DB,
      habilitando múltiplas instâncias e zero perda de contexto ao reiniciar.
- [ ] Observabilidade (métricas, tracing, alertas de custo por tenant).
- [ ] Otimização de custo de IA (roteamento `gpt-4o-mini` + prompt caching — ver [`PRICING.md`](./PRICING.md)).

---

## 📌 Princípios de execução

1. **Testes antes de refatorar.** Toda mudança estrutural entra com rede de segurança.
2. **Multi-tenant desde a Fase 1.** Nenhuma feature nova nasce single-tenant a partir daí.
3. **A IA é o diferencial.** Cada fase preserva e amplia a experiência conversacional.
4. **Arquivos pequenos e coesos.** Alvo de 200–400 linhas; 800 é o teto.
5. **Flyway para todo schema.** Migrações versionadas, nunca alteração manual de banco.

---

_Última atualização: 2026-07-20 · Documento vivo — atualizado a cada fase concluída._
