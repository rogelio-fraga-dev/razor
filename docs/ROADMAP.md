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

### Fase 0 — Fundação de Engenharia 🏗️ *(✅ concluída)*

> **Objetivo:** parar de apagar incêndio. Criar a base técnica que permite evoluir com
> segurança e velocidade.

- [x] **Higiene do repo** — removidos ~900KB de logs commitados, `.gitignore` reforçado.
- [x] **Rebrand para Razor** — pacote `com.barberbot` → `com.razor`, config, `pom.xml`. Compila limpo.
- [x] **CI/CD** — GitHub Actions (build + testes + cobertura JaCoCo) + Dependabot + Testcontainers.
- [x] **Harness de testes** — JUnit 5 + AssertJ + Mockito + JaCoCo.
- [x] **Testes das regras críticas** — parsing de tags da IA, telefone, datas, e os fluxos
      de **cliente**, **admin** e **agenda** (máquina de estados incluída).
- [x] **Refatorar as God classes** — `OrchestratorService` (~994 linhas) quebrado em
      `WebhookRouter` / `AdminCommandService` / `CustomerConversationService` + suporte.
- [x] **Corrigir erros engolidos** — eliminados os 7 `catch (Exception e) {}` vazios.
- [x] **Tipos de domínio fortes** — `PaymentStatus` como `enum`; data/hora como
      `LocalDate`/`LocalTime` (Opção A). Migração `V3` **validada** em Postgres real (ITs
      Testcontainers no CI).

**Pronto ✅:** `./mvnw verify` roda verde no CI (unitários + ITs Testcontainers), e o
monólito `OrchestratorService` não existe mais.

_Observação:_ o `AgendaService` ainda é grande (~800 linhas) — candidato a nova extração
numa fase futura, mas já com serviços satélites extraídos (`ServiceCatalogService`,
`ProfessionalService`, `ProfessionalAssignmentService`).

---

### Fase 1 — Núcleo Multi-Tenant 🏢 *(✅ concluída)*

> **Objetivo:** deixar de ser "um sistema para uma barbearia" e virar "uma plataforma
> onde N barbearias operam isoladas". Este é o pré-requisito de qualquer venda SaaS.

- [x] Entidade `Barbershop` (tenant): nome, slug, instância de WhatsApp, ativo.
      _(CNPJ, endereço, PIX, marca e plano ficam para a Fase 2/3.)_
- [x] `barbershop_id` em **todas** as tabelas + isolamento na camada de dados
      (`TenantContext` + acesso escopado em clientes, agenda, interações, tarefas, waitlist).
- [x] Trocar `UNIQUE(phone_number)` global por `UNIQUE(barbershop_id, phone_number)`.
- [x] Entidade `Professional` (barbeiro) + catálogo `Service` (nome, **preço**, **duração**).
- [x] **Agenda multiprofissional**: disponibilidade **por barbeiro**, auto-atribuição
      (menos ocupado) + escolha explícita, e a IA só bloqueia horário quando **todas as
      cadeiras** estão ocupadas.
- [x] **Escolha de profissional no fluxo do cliente** — com 2+ barbeiros ativos a IA
      pergunta com quem o cliente quer se atender (`tanto faz` = auto); 0/1 barbeiro mantém
      a auto-atribuição imediata (sem regressão).
- [x] **Precificação e duração data-driven** — preço e duração vêm do catálogo `services`
      do tenant (fim dos valores fixos no código); a disponibilidade respeita a **duração
      real** (um combo de 60min ocupa dois slots de 30min).
- [x] **WhatsApp por tenant** — **entrada** (`TenantResolver` mapeia a instância da Evolution
      → tenant) **e saída** (`WhatsAppService`/`EvolutionClient` enviam pela `whatsapp_instance`
      da barbearia; a instância é resolvida **antes** do envio assíncrono, que não propaga o
      `TenantContext`; fallback na instância do config, sem regressão no tenant padrão).
- [x] **Admins por tenant** — tabela `barbershop_admins` + `TenantAdminService` (roteamento
      admin/cliente e notificações por tenant), com fallback nos admins do config global. Os
      **relatórios do scheduler** (matinal/noturno/semanal) passaram a rodar **por barbearia ativa**.
- [x] **Onboarding self-service** — `POST /api/onboarding` provisiona um tenant (barbearia +
      catálogo padrão + barbeiro + admins) numa transação, protegido por chave no header
      (`razor.onboarding.api-key`), **fail-closed**.

**Pronto quando:** duas barbearias distintas operam no mesmo deploy sem enxergar dados
uma da outra, **cada uma com seu WhatsApp**, serviços e barbeiros. ✅ *Implementado.*

> **Status.** Todo o núcleo multi-tenant está implementado e verde no CI (unitários + ITs
> Testcontainers). ⚠️ **Ainda não exercitado com 2 tenants reais** — só build/testes/ITs; hoje
> o deploy roda com o tenant padrão. Validação end-to-end pendente: provisionar uma 2ª
> barbearia via `/api/onboarding` (com `ONBOARDING_API_KEY` setado), cadastrar sua
> `whatsapp_instance` e admins, e trocar mensagens reais pela instância dela.

---

### Fase 2 — Paridade de Gestão 💰 *(🟡 em andamento)*

> **Objetivo:** cobrir o que faz um barbeiro pagar Frizzar/CashBarber hoje.

- [~] **Financeiro**: resumo do dia/semana e fluxo por meio de pagamento (relatórios prontos,
      no WhatsApp e no painel). _(Falta: caixa/comandas transacional e fechamento formal.)_
- [x] **Comissões** por profissional: percentual por barbeiro (`commission_percent`), relatório
      por dia e configuração no painel. _(Config por WhatsApp removida — princípio 6.)_
- [ ] **Vendas de balcão (produtos)** 🎯 *próximo*: catálogo de produtos (nome, preço, ativo) +
      registro de venda no painel (produto, qtde, barbeiro opcional — ex.: bebida/pomada vendida
      na hora do corte). Venda entra no **financeiro do dia** e gera **comissão de produto** para
      o barbeiro no relatório de comissões. _(Estoque/reposição fica para depois.)_
- [~] **Clube de assinatura**: catálogo de **planos configuráveis** pronto (`subscription_plans`,
      painel). _(Falta: identificação assinante x avulso no agendamento e cobrança recorrente — Fase 4.)_
- [~] **Relatórios/DRE**: financeiro estruturado e comissões prontos no painel.
      _(Falta: DRE e dashboard de retenção — "quem some", frequência.)_
- [ ] **Estoque** (opcional): reposição e saídas dos produtos do balcão.

**Pronto quando:** um barbeiro consegue fechar o mês (faturamento, comissões,
assinaturas) sem sair do Razor.

---

### Fase 3 — Painel Web & Site da Barbearia 🌐 *(🎯 próxima)*

> **Objetivo:** dar ao dono da barbearia uma gestão web de verdade (paridade com
> Frizzar/CashBarber) e presença própria além do WhatsApp.

> **Divisão de responsabilidades (definida com o time):**
> - **Site/console do admin (web):** onde o dono **configura tudo** — bot, serviços, preços,
>   barbeiros/comissões, textos, planos, perfil da barbearia — e **gera os relatórios**. É a
>   fonte da verdade da configuração.
> - **WhatsApp (celular):** **operação** do dia a dia — disparar mensagens/promoções para a
>   base, ver a agenda do dia, pausar/retomar cliente. Leve e operacional; **não** é onde se
>   configura.

- [x] **Autenticação e papéis** — login do admin do tenant (Spring Security + JWT), com o
      `TenantContext` vindo da **sessão web** (não da instância de WhatsApp). Papéis: OWNER/
      RECEPTION/BARBER. Onboarding cria o usuário OWNER. `POST /api/auth/login` → JWT.
- [x] **API web (REST)** reutilizando os serviços tenant-scoped: **config** de serviços
      (`/api/admin/services`), profissionais/comissões (`/api/admin/professionals`), admins
      (`/api/admin/admins`) e perfil da barbearia (`/api/admin/barbershop`); **relatórios**
      (`/api/admin/reports/{commissions,financial,agenda}`, JSON estruturado).
      _(Falta: textos/planos do bot — hoje ainda em config; migrar para o tenant/DB.)_
- [~] **SPA React** (painel web em `/web`, Vite+TS) consumindo a API: **MVP amplo** —
      login, Painel (financeiro do dia + agenda), Serviços, Profissionais (+comissão),
      Clientes, Planos, Comissões e Configurações (perfil + **textos do bot** + admins),
      com tema claro/escuro. _(Falta: agenda multiprofissional interativa e assinantes/subscrição.)_
- [ ] Site/landing **white-label** por barbearia (marca, serviços, endereço, avaliações),
      servido em **subdomínio próprio**: `<slug>.razor.com.br` (ex.: `barbeariaficticia.razor.com.br`).
      O tenant é resolvido pelo `Host` header → `slug` → `barbershop_id` (mesmo padrão do
      `TenantResolver` de WhatsApp). Exige DNS/TLS **wildcard** `*.razor.com.br` e lista de
      slugs reservados. Detalhes em [`PLATFORM.md §6`](./PLATFORM.md).
- [ ] Página de **agendamento online** (link próprio) na mesma agenda do bot.

**Pronto quando:** o dono configura e gerencia toda a barbearia por um painel web (config +
relatórios), usando o WhatsApp apenas para a operação do dia a dia.

---

### Fase 4 — Plataforma Comercial (venda & entrega via web) 🛰️

> **Objetivo:** vender e entregar o Razor 100% pela web, sem visita presencial, e dar a
> você controle total como dono da plataforma. Detalhes em [`PLATFORM.md`](./PLATFORM.md).

- [ ] **Planos do SaaS (tiers) + feature gating:** `plan_tier` (Solo/Equipe/Pro) na conta da
      barbearia, com **bloqueio de features no servidor** por tier (não só na UI). É o que dá
      sentido aos 3 planos do [`PRICING`](./PRICING.md). ⚠️ **Não** confundir com
      `subscription_plans` (o clube da barbearia p/ os clientes dela) — ver [`PLATFORM.md §7`](./PLATFORM.md).
- [ ] **Console do Dono (super-admin):** todas as barbearias, MRR, churn, custo por tenant, suporte
      (papel `PLATFORM_OWNER` que enxerga através dos tenants).
- [ ] **Site institucional** `razor.com.br`: proposta, planos, demonstração, prova social, CTA.
      _(Distinto do site white-label de cada barbearia em `<slug>.razor.com.br` — ver [`PLATFORM.md §6`](./PLATFORM.md).)_
- [ ] **Cadastro self-service** + **billing do SaaS** (gateway BR: Asaas / Mercado Pago / Stripe; cartão/PIX; recorrência).
- [ ] **Provisionamento automático** do tenant ao confirmar pagamento — o motor já existe
      (`BarbershopProvisioningService`); falta trocar o gatilho de "chave de admin" para
      "webhook de pagamento confirmado".
- [x] **Conexão de WhatsApp por QR code remoto** (o que substitui a visita presencial) — feito
      no painel: `/api/admin/whatsapp` (status/connect/disconnect) + card em Configurações.
- [ ] **Infra de subdomínio:** DNS + TLS **wildcard** `*.razor.com.br` e resolução de tenant
      por `Host` header (habilita os sites white-label da Fase 3).
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
6. **Config no web, operação no WhatsApp.** A configuração do bot/barbearia e os relatórios
   vivem no painel web; o WhatsApp admin fica para a operação (disparos, agenda do dia,
   pausar/retomar). Regras de negócio ficam nos *services* (tenant-scoped), reusados por ambos.

---

_Última atualização: 2026-07-23 · Documento vivo — atualizado a cada fase concluída._
