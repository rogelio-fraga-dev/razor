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

### 🔐 Fundação & Segurança *(✅ rodada de 2026-07-23)*

> Auditoria antes de seguir no front. Três buracos **reais** (não teóricos) foram fechados,
> e o bot passou a falar os dados de cada tenant.

- [x] **Injeção pelo webhook (crítico)** — `/api/webhook` era público **e** o `TenantResolver`
      caía no *tenant padrão* para instância desconhecida: dava para forjar um POST com o
      telefone de um admin e executar comandos de gestão numa barbearia real. Agora: token
      compartilhado obrigatório (**fail-closed**, `WEBHOOK_TOKEN`) **e** instância desconhecida
      é descartada — duas barreiras independentes.
- [x] **QR code público (crítico)** — `GET /api/webhook/qrcode` servia o QR de pareamento sem
      autenticação (um estranho podia conectar o WhatsApp da barbearia no próprio aparelho).
      Endpoint removido; o QR só sai pelo painel autenticado.
- [x] **Força bruta no login** — `LoginRateLimiter`: 5 falhas por email+IP → bloqueio de 15 min (429).
- [x] **Cabeçalhos de segurança** (frame-deny, nosniff, referrer-policy, HSTS).
- [x] **Bot multi-tenant** — o system prompt tinha "Barbearia Ficticia" e a tabela de preços
      **cravados no código**: toda barbearia teria um bot dizendo o nome e cobrando os preços
      de outra. Agora nome, catálogo real, horário e persona vêm do tenant; catálogo vazio
      **não inventa preço**. O `AgendaService` usa o expediente configurado (V15).
- [x] **Chave PIX real** — o bot enviava uma chave **falsa hardcoded**; agora é a do tenant, e
      sem PIX configurado ele não inventa nada.
- [x] **Folgas e bloqueios de agenda (V16)** — a IA marcava em cima de folga, almoço e feriado.
      Por barbeiro ou barbearia toda, pontual ou recorrente, faixa ou dia inteiro; barrado na
      oferta de horários **e** na auto-atribuição. Sobreposição respeita duração.
- [x] **Backup do Postgres** — `scripts/backup-db.ps1` (pg_dump `-Fc`, validação, retenção de
      14 dias), verificado com `pg_restore -l`.
- [x] **Estado efêmero fora dos services (partes 1 e 2 de 3)** — porta `StateStore` + adaptador
      em memória; **os 12 mapas `ConcurrentHashMap` saíram**. O TTL substituiu duas rotinas de
      limpeza (`clearZombieCustomerStates`, `clearProcessedCache`) e fechou um vazamento (oferta
      de lista de espera vivia para sempre). Chaves escopadas por tenant.
- [ ] **Parte 3:** adaptador **Redis** (`razor.state.store=redis`) + IT com Testcontainers
      rodando a **mesma bateria de contrato** do `InMemoryStateStoreTest`.

---

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
- [ ] **Textos e dados do bot por tenant (ampliar)** 🎯 *em andamento*: hoje só há 5 campos
      (endereço, mapa, serviços, agendamento, Instagram) e **muita coisa continua hardcoded**
      — nome "Barbearia Ficticia" em 6 pontos, **chave PIX falsa**, horário 09–19h e a
      **tabela de preços dentro do system prompt da IA**. Campos a tornar configuráveis:
      nome de exibição, **chave PIX + PIX copia-e-cola**, horário de funcionamento (dias/horas),
      saudação, texto do lembrete, texto do pedido de avaliação, recompensa de fidelidade
      (+ meta de pontos), política de cancelamento/atraso, formas de pagamento, persona/tom
      de voz da IA. _Prioridade: PIX e nome (hoje o bot envia dados errados)._

**Pronto quando:** um barbeiro consegue fechar o mês (faturamento, comissões,
assinaturas) sem sair do Razor.

---

#### 🎯 Paridade competitiva — análise da lista de features da Frizzar (2026-07)

> **Leitura crítica:** a lista pública da Frizzar tem ~45 itens, mas boa parte é
> **conteúdo/serviço** (escola de formação, treinamentos em vídeo, central de apoio,
> "Q.G do gestor"), não software. Perseguir todos os bullets antes de ter clientes é a
> armadilha clássica. A ordem abaixo é por **o que realmente impede um barbeiro de trocar**.

**🔴 Bloqueadores de troca (fazer antes de vender em escala)**

- [ ] **Caixa: abertura/fechamento + comandas** — o ritual diário da barbearia. Comanda por
      atendimento (serviços + produtos), fechamento com conferência, sangria/suprimento.
- [ ] **Folgas e bloqueios de agenda** — sem isso a IA agenda em cima de folga/almoço/feriado.
      Por profissional, pontual ou recorrente. _(Pré-requisito da agenda confiável.)_
- [ ] **Pacotes** (ex.: 10 cortes pré-pagos) com **baixa automática** no atendimento —
      diferente do clube (recorrência); é crédito pré-pago.
- [ ] **Múltiplas formas de pagamento** por atendimento (dinheiro, débito, crédito, PIX,
      split) — hoje só PIX/presencial/pendente.
- [ ] **Limite de agendamentos por cliente** — impede o cliente de reservar vários horários
      "para garantir" e travar a agenda. _(Boa ideia da Frizzar; barato de implementar.)_

**🟠 Alto valor (diferenciam na venda)**

- [ ] **DRE + taxa de ocupação + frequência de clientes** — as métricas que o dono usa para
      decidir. Ocupação = horas vendidas / horas disponíveis por barbeiro.
- [ ] **Contas a pagar e contas recorrentes** (aluguel, produtos, energia) — fecha o
      financeiro de verdade, não só a receita.
- [ ] **Estoque**: produtos, estoque mínimo com alerta, **baixa automática** na venda.
- [ ] **Vales e adiantamentos** dos profissionais, descontados na comissão.
- [ ] **Comissão por produto e por serviço avulso** + **comissões pagas** (histórico de
      quitação) — complementa o `commission_percent` atual.
- [ ] **Grupos de clientes por tags** + **status do cliente** (ativo/sumido/novo) — munição
      para os disparos que já temos.
- [ ] **Cobrança antecipada** e **controle de débitos/créditos** do cliente.

**🟡 Desejáveis (depois da tração)**

- [ ] Metas de performance por profissional · Cashback · Checkout express ·
      Reagendamento self-service pelo cliente · Auditoria de agendamentos apagados ·
      Avaliação/pesquisa de satisfação estruturada (hoje só o pedido de review) ·
      Relatório de vendas por período/produto.

**🔵 Fora de escopo por ora (decisão consciente)**

- **Emissão de notas (NFS-e)**: integração fiscal por município, alto custo/manutenção —
  avaliar via parceiro (ex.: Focus NFe) só quando houver demanda paga.
- **Escola/treinamentos/central de apoio**: isso é *conteúdo e suporte*, não produto.
  Nosso equivalente inicial: onboarding guiado no painel + suporte por WhatsApp.

**✅ Onde já estamos à frente:** atendimento por **IA conversacional** (eles têm chatbot
roteirizado), agendamento por áudio (Whisper), lista de espera reativa automática,
"app sem download" nativo (o WhatsApp que o cliente já usa) e o site white-label incluso.

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
      Clientes, Planos, Comissões, Configurações (status do bot + WhatsApp por QR +
      textos + admins) e Perfil, com a identidade RAZOR aplicada.
      _(Falta: agenda multiprofissional interativa.)_
- [ ] **Overhaul de UX** conforme [`UX_BLUEPRINT.md`](./UX_BLUEPRINT.md): infra de feedback
      (toasts/skeletons/empty states), **tutorial de primeiro acesso + checklist de ativação**,
      Configurações em abas, Clientes com drawer + cards mobile, chips de período em
      Comissões, regra dos 2 cliques.
- [ ] **Módulo "Site" no painel** (editor white-label): escolha do template, upload de
      fotos/logo, textos, cor de destaque, **preview ao vivo** e publicar. Spec no
      [`UX_BLUEPRINT §3.2`](./UX_BLUEPRINT.md).
- [ ] Site/landing **white-label** por barbearia (marca, serviços, endereço, avaliações),
      servido em **subdomínio próprio**: `<slug>.razor.com.br` (ex.: `barbeariaficticia.razor.com.br`).
      O tenant é resolvido pelo `Host` header → `slug` → `barbershop_id` (mesmo padrão do
      `TenantResolver` de WhatsApp). Exige DNS/TLS **wildcard** `*.razor.com.br` e lista de
      slugs reservados. Detalhes em [`PLATFORM.md §6`](./PLATFORM.md).
- [ ] **Sistema de templates do site** (modelo de negócio — [`PLATFORM.md §templates`](./PLATFORM.md)):
      **3 templates prontos** inclusos (Clássico, Dark Premium, Vitrine) — o tenant escolhe 1
      no painel e sobe apenas **conteúdo** (logo, fotos, textos, cores); **código é nosso e
      centralizado** (tema versionado no repo; atualização propaga p/ todos). **Template
      personalizado = serviço avulso** (one-time, sob consulta) feito pelo nosso time.
      Modelo: tabela `barbershop_site` (template_id + conteúdo).
- [ ] Página de **agendamento online** (link próprio) na mesma agenda do bot.
- [ ] **Tema claro no painel e na landing** — o painel já alterna (dark-first); a landing
      institucional precisa da variante clara com o mesmo brand (tokens do
      [`BRANDING §5.4`](./BRANDING.md)) e toggle persistido.
- [ ] **Landing mais persuasiva**: mais efeitos/motion (parallax sutil, contadores animados,
      hover 3D nos cards, reveal encadeado), **preço anual exibido em valor cheio** (hoje só
      mostra o equivalente mensal) e prova social acima da dobra.

---

### 👤 Contas do CLIENTE FINAL (nível 3) — *nova frente, Fase 3/4*

> Hoje o cliente final só existe como telefone no WhatsApp. Para **assinar planos, salvar
> cartão e se identificar no site**, ele precisa de conta própria — sem nunca virar
> obrigação para quem só quer marcar um corte.

- [ ] **Conta opcional do cliente** na barbearia (`customer_accounts`): quem quiser continua
      agendando **sem cadastro** pelo WhatsApp (fluxo atual intacto); a conta destrava
      assinatura do clube, histórico e cartão salvo.
- [ ] **Login social + tradicional:** Google e Apple (OAuth2/OIDC) e e-mail+senha. Vínculo
      da conta ao telefone do WhatsApp (mesma pessoa nos dois canais).
- [ ] **Assinatura do clube pelo próprio cliente:** escolhe o plano no site da barbearia,
      paga recorrente, e o bot **passa a reconhecê-lo como assinante** (usa o `subscriber`
      que já existe na agenda) — ex.: "seu plano VIP cobre este corte".
- [ ] **Cartão salvo:** **tokenizado no gateway** — o número do cartão **nunca** toca nosso
      banco (fora de escopo PCI). Guardamos só o token e os 4 últimos dígitos.
- [ ] **Visão do barbeiro:** na tela Clientes, distinguir *cadastrado no site* × *só WhatsApp*,
      com status da assinatura e forma de pagamento.
- [ ] **Segurança robusta (requisito explícito):** contexto de auth **separado** do painel
      admin (outro `audience` no JWT — cliente jamais acessa `/api/admin/**`); senhas em
      bcrypt; rate limiting e lockout no login; verificação de e-mail; refresh tokens com
      rotação; **LGPD** (consentimento, exportar/excluir meus dados); auditoria de acesso.
      Escopo de tenant obrigatório: um cliente pertence a **uma** barbearia.

**Pronto quando:** o dono configura e gerencia toda a barbearia por um painel web (config +
relatórios), usando o WhatsApp apenas para a operação do dia a dia.

---

### 🧪 Programa Beta (go-to-market do piloto) — *entre as Fases 3 e 4*

> **Contexto de validação:** o bot (era single-tenant) **já foi validado com ~15 pessoas
> reais**, rodando por vários dias — a cunha funciona. O beta valida agora o *pacote
> completo* (painel + multi-tenant + site) com barbearias de verdade.

- [ ] **Piloto âncora:** oferecer o sistema completo a **1 barbearia amiga**, acompanhar de
      perto (grupo direto de suporte), medir: agendamentos pela IA, no-show, tempo de resposta.
- [ ] **Chamada pública de beta** no **LinkedIn e Instagram**: post com demo em vídeo do bot
      agendando (padrões de social do [`BRANDING §12`](./BRANDING.md)) — vagas limitadas
      (ex.: 10 barbearias), **1º mês grátis**, sem cartão.
- [ ] **Cupom de beta** no onboarding (ex.: `BETA30`) que pula o pagamento no wizard de
      compra — vira o gatilho de provisionamento enquanto o billing não existe.
      **Limite de 10 usos**: esgotou, o código expira sozinho e o cadastro volta a exigir
      cartão/PIX. Novos códigos só pelo console do dono (ver Fase 4).
- [ ] **Canal**: beta roda na **Evolution (grátis, ilimitada, self-host)** com aviso de
      risco ao tenant; migração p/ Cloud API oficial conforme
      [`WHATSAPP_CHANNEL.md`](./WHATSAPP_CHANNEL.md).
- [ ] **Critério de saída do beta:** ≥ 3 barbearias ativas 30 dias, NPS informal positivo,
      custo de IA/tenant medido → liga o billing (Fase 4) e converte os betas em pagantes.

---

### Fase 4 — Plataforma Comercial (venda & entrega via web) 🛰️

> **Objetivo:** vender e entregar o Razor 100% pela web, sem visita presencial, e dar a
> você controle total como dono da plataforma. Detalhes em [`PLATFORM.md`](./PLATFORM.md).

- [ ] **Planos do SaaS (tiers) + feature gating:** `plan_tier` (Solo/Equipe/Pro) na conta da
      barbearia, com **bloqueio de features no servidor** por tier (não só na UI). É o que dá
      sentido aos 3 planos do [`PRICING`](./PRICING.md). ⚠️ **Não** confundir com
      `subscription_plans` (o clube da barbearia p/ os clientes dela) — ver [`PLATFORM.md §7`](./PLATFORM.md).
- [ ] **Console do Dono (super-admin):** papel `PLATFORM_OWNER` + API `/api/platform/**` +
      área própria no painel — **todas as barbearias e instâncias de WhatsApp** (status da
      conexão, plano, uso), MRR, churn, **custo de IA por tenant** (a chave OpenAI é global,
      da plataforma — o custo é nosso e precisa ser visível/alertável), suspender/reativar
      tenant, impersonar com auditoria. _É onde o dono "configura tudo e vê tudo"._
      Telas especificadas em [`UX_BLUEPRINT §6`](./UX_BLUEPRINT.md).
- [ ] **Observabilidade no console:** tela **"Logs ao vivo"** (stream com filtro por
      tenant/nível — v1: tail via SSE; v2: Loki/Grafana) e **"Saúde do sistema"**
      (uptime, fila de webhooks, erros/min, latência OpenAI/Evolution).
- [ ] **Backups gerenciados:** `pg_dump` diário automatizado → storage externo, status no
      console (último ok/falhou), botão "backup agora" e rotina mensal de teste de restauração.
- [ ] **Site institucional** `razor.com.br`: proposta, planos, demonstração, prova social, CTA.
      _(Distinto do site white-label de cada barbearia em `<slug>.razor.com.br` — ver [`PLATFORM.md §6`](./PLATFORM.md).)_
- [ ] **Cadastro self-service** + **billing do SaaS** (gateway BR: Asaas / Mercado Pago / Stripe; cartão/PIX; recorrência), com as regras:
      - **Trial exige meio de pagamento**: cartão **ou PIX recorrente** cadastrado para
        liberar os dias grátis (evita conta-fantasma e vira assinatura sozinha ao fim).
      - **Aviso 1 dia antes do fim do trial** (WhatsApp + e-mail): "continua ou cancela?",
        com link de cancelamento em 1 clique — sem pegadinha.
      - **Cobrança automática** ao término, se não cancelou. Inadimplência: aviso → suspensão
        (dados preservados) → cancelamento.
- [ ] **Códigos beta com controle no super admin** (única exceção que **pula o cartão**):
      entidade `beta_codes` (código, **limite de usos — padrão 10**, usos atuais, validade,
      ativo). Ao atingir o limite **expira automaticamente** e ninguém mais entra sem cartão;
      só volta a existir beta se **você criar outro código no console**. Console mostra
      quantos restam e quem usou cada código.
- [ ] **Provisionamento automático** do tenant ao confirmar pagamento — o motor já existe
      (`BarbershopProvisioningService`); falta trocar o gatilho de "chave de admin" para
      "webhook de pagamento confirmado".
- [x] **Conexão de WhatsApp por QR code remoto** (o que substitui a visita presencial) — feito
      no painel: `/api/admin/whatsapp` (status/connect/disconnect) + card em Configurações.
- [ ] **Infra de subdomínio:** DNS + TLS **wildcard** `*.razor.com.br` e resolução de tenant
      por `Host` header (habilita os sites white-label da Fase 3).
- [ ] Interface `WhatsAppGateway` para trocar Evolution ↔ Cloud API oficial sem reescrever o
      núcleo. **Análise completa do canal (limites grátis/pago, tiers, estratégia por fase)
      em [`WHATSAPP_CHANNEL.md`](./WHATSAPP_CHANNEL.md)** — resumo: Evolution é grátis e
      ilimitada (self-host, risco de ban); oficial tem respostas grátis na janela de 24h,
      templates ~US$0,007 (utility) e limites de 250→1.000+ envios/dia por portfólio
      verificado; em escala, modelo Tech Provider (WABA por barbearia).

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

_Última atualização: 2026-07-23 (segurança, bot multi-tenant, folgas, StateStore) · Documento vivo._
