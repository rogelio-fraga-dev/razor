# 🪒 Razor — Roadmap de Produto

> **Razor** é um SaaS de gestão e atendimento para barbearias e centros de estética.
> Nascido como um bot de atendimento por IA no WhatsApp, está evoluindo para uma
> plataforma multi-tenant completa que compete de igual para igual com **Frizzar**
> e **CashBarber** — com um diferencial que eles não têm de forma nativa:
> **atendimento conversacional por Inteligência Artificial**.

---

## 📖 De onde o Razor veio

Não começou num plano de negócios. Começou numa cadeira de barbearia.

Era uma conversa comum com o meu barbeiro — dessas que se tem enquanto o corte acontece — até
ele descrever uma rotina que eu reconheci na hora como um problema de software: o celular
apitando sem parar no meio do atendimento, cliente esperando resposta, horário perdido porque
ninguém conseguiu responder a tempo. Saí de lá e construí um bot para **aquela** barbearia.
Funcionava, mas era isso mesmo: feito sob medida para o contexto dela, com todas as limitações
que isso traz.

O passo seguinte parecia óbvio — tirar as amarras e transformar aquilo num bot que servisse a
qualquer barbearia pequena. Foi o que fiz. E foi aí que veio o feedback que mudou tudo.

Uma barbearia testou e disse, com todas as letras: *"é interessante, mas não vale a pena pagar
por um bot que resolve só uma parte."* Doeu — e estava certíssimo. O atendimento é a porta de
entrada, não a barbearia inteira. Quem toca um negócio de verdade precisa saber quanto entrou
no caixa, quanto vai para cada barbeiro, quem sumiu e quem volta. Um robô simpático no WhatsApp
não paga o aluguel.

Foi conversando com o meu professor de programação que a ficha caiu de vez. A dica dele foi
direta: pare de tratar isso como um bot e trate como um **produto**. Evolua.

**O Razor nasceu daí.** Não de uma ideia bonita, mas de um "não" bem dado e de um conselho na
hora certa. Ele foi desenhado para o dia a dia real de quem vive de barbearia — a de uma
cadeira só e a que tem cinco barbeiros e fila de espera. Atendimento por IA que realmente
atende, agenda que não fura, financeiro que fecha, comissão que não gera discussão.

Chegamos para competir com os grandes do mercado. E, mais do que isso, para entregar solução e
praticidade — tirar da frente do barbeiro tudo o que não é o ofício dele, e devolver o tempo
para o que realmente importa: **cortar bem e ganhar dinheiro**.

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
- [x] **Parte 3:** adaptador **Redis** (`razor.state.store=redis`, padrão segue `memory`) —
      estado compartilhado entre instâncias e persistente no restart; TTL nativo do Redis e
      `keysWithPrefix` por **SCAN** (nunca `KEYS`). `RedisStateStoreIT` valida o contrato
      (round-trip, lista, expiração, remoção, prefixo) contra Redis real via Testcontainers no
      CI. Health do Redis desligado por padrão para não ficar DOWN sem Redis.

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

- [x] **Financeiro**: resumo do dia/semana e fluxo por meio de pagamento (relatórios prontos, no
      WhatsApp e no painel), **caixa transacional com fechamento formal (V18)** e **comanda por
      atendimento (V22)**.
- [x] **Comissões** por profissional: percentual por barbeiro (`commission_percent`), relatório
      por dia e configuração no painel. _(Config por WhatsApp removida — princípio 6.)_
- [x] **Vendas de balcão (produtos)** — **catálogo (V20)** com tela própria (*Produtos*): nome
      único por barbearia, preço maior que zero e desativação em vez de exclusão. **Venda (V21)**:
      produto, quantidade e barbeiro opcional, lançada no **caixa aberto** como movimento de venda
      — sem caixa aberto a venda é recusada, senão o dinheiro não apareceria na conferência da
      gaveta. Nome e preço são **congelados na venda**: reajuste de preço não reescreve o
      histórico. Com barbeiro escolhido, entra na **comissão** dele (serviços e produtos aparecem
      em colunas separadas no relatório).
      **Percentual próprio de produto (V23):** cada barbeiro tem
      `product_commission_percent` além do de serviços, e cada base usa o seu. A coluna nasce
      **nula** de propósito — nula significa "usa o mesmo percentual dos serviços", o
      comportamento anterior, então subir a versão não alterou a comissão de ninguém. `0` é
      diferente de nulo: quer dizer "não paga comissão de produto".
- [x] **Comanda por atendimento (V22)** — serviços + produtos na mesma conta, fechada de uma vez.
      Abre com cliente e barbeiro (ambos opcionais), recebe itens do catálogo com **preço
      congelado na entrada**, e no fechamento lança **um único** movimento no caixa com o total.
      Os produtos da comanda viram linha em `product_sales` apontando para **esse mesmo**
      movimento — registram a venda e a comissão sem lançar o dinheiro duas vezes na gaveta.
      Comanda vazia, sem forma de pagamento ou já fechada não fecha; cancelar não mexe em dinheiro.
- [x] **Produtos no financeiro estruturado** — `/reports/financial` agora devolve
      `servicesRevenue`, `productsRevenue` e `productsSold` **separados**, com `totalRevenue` =
      serviços + produtos. O **tíquete médio continua serviços ÷ atendimentos**: somar produto ali
      inflaria o ticket com venda de balcão que nem teve atendimento junto. O gráfico de evolução
      também passou a incluir produtos, para bater com o KPI. O `payments` (PIX/presencial/
      pendente) segue descrevendo só os agendamentos — a forma de pagamento das vendas vive no
      caixa, com outro vocabulário.
      > ⚠️ **Lacuna conhecida:** serviço lançado numa **comanda** não entra no `/reports/financial`,
      > que conta agendamentos. Ligar comanda ↔ agendamento resolveria, mas hoje não há esse
      > vínculo e somar direto arriscaria contar duas vezes quando o dono registra os dois.
- [~] **Clube de assinatura**: catálogo de **planos configuráveis** pronto (`subscription_plans`,
      painel). _(Falta: identificação assinante x avulso no agendamento e cobrança recorrente — Fase 4.)_
- [~] **Relatórios/DRE**: financeiro estruturado e comissões prontos no painel.
      _(Falta: DRE e dashboard de retenção — "quem some", frequência.)_
- [x] **Estoque (V24)** — reposição, perda, ajuste por contagem física, **baixa automática** na
      venda (balcão e comanda passam pelo mesmo caminho) e alerta de nível de reposição.
      **Opt-in por produto** (`track_stock`, padrão desligado): barbearia que não conta estoque
      vende exatamente como antes, e nada bloqueia por saldo. Ligado, a venda só passa se houver
      saldo — a conferência acontece **antes de cobrar**, para não deixar saldo negativo.
      Toda mudança grava linha em `stock_movements` com o saldo resultante: sem histórico,
      "meu estoque está errado" não tem como ser investigado.
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

- [x] **Caixa: abertura/fechamento + comandas** — o ritual diário da barbearia, completo.
      **V18:** sessão de caixa com fundo de troco (uma aberta por barbearia), movimentos (venda
      com forma de pagamento, sangria, suprimento), **gaveta esperada** = fundo + suprimentos −
      sangrias + vendas em dinheiro, e fechamento com conferência (sobra/falta).
      **V22:** comanda por atendimento juntando serviços + **produtos** numa conta só, com baixa
      no caixa em **um único** movimento no fechamento. Telas *Caixa* e *Comandas* no painel.
- [x] **Folgas e bloqueios de agenda** — sem isso a IA agenda em cima de folga/almoço/feriado.
      Por profissional (ou barbearia toda), pontual ou recorrente, dia inteiro ou faixa de
      horário. Back V16 + **tela no painel** (modal aberto pela toolbar da Agenda).
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
- [x] **Estoque**: produtos, estoque mínimo com alerta e **baixa automática** na venda — feito na
      V24 (ver Fase 2).
- [x] **Vales e adiantamentos (V26)** dos profissionais, descontados na comissão. O vale pertence
      a uma **data** e é descontado no acerto do período que a contém — sem estado "quitado" para
      dessincronizar. Sair do caixa é opcional (igual ao acerto V25): vale em espécie vira sangria,
      vale por fora não mexe na gaveta. O saldo **pode ficar negativo** quando o vale passa da
      comissão: o barbeiro deve à barbearia, e zerar o número esconderia isso.
- [~] **Comissão por produto** ✅ (V23: `product_commission_percent` por barbeiro) e **comissões
      pagas** ✅ (V25) + **por serviço avulso** — este último ainda pendente.
      > **Acerto (V25):** tela *Acerto* mostra, por período, o apurado × o **já pago** × o saldo —
      > é o que impede pagar a mesma quinzena duas vezes. O valor é congelado no pagamento
      > (correção posterior num atendimento não reescreve o recibo) e **sair do caixa é opcional**:
      > pagamento em espécie vira sangria; PIX/transferência não mexe na conferência da gaveta.
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
- [ ] 🎯 **Perfil completo do barbeiro (hoje é só nome, telefone, comissão e ativo)** — o
      profissional é a cara da barbearia: aparece na agenda, no relatório de comissão e,
      principalmente, na **página da equipe do site white-label**. Campos a acrescentar,
      todos editáveis pelo dono na tela *Profissionais*:
      | Campo | Para quê |
      |---|---|
      | **Foto** | avatar na agenda/painel e retrato na página da equipe do site |
      | **Apelido / como o bot chama** | a IA fala "com o Diego", não "com Diego Aparecido Santos" |
      | **Bio curta** | texto de apresentação no site ("10 anos de navalha, especialista em degradê") |
      | **Especialidades** (tags) | mostra no site e, depois, ajuda a IA a sugerir o barbeiro certo |
      | **Instagram** | link no perfil do site |
      | **Serviços que atende** | restringe a auto-atribuição (ex.: quem não faz barba não recebe barba) |
      > **Decisão de armazenamento da foto (a tomar):** não há storage de objetos hoje.
      > Opções: **(a)** redimensionar no navegador (~256px, JPEG) e guardar **data URI** em
      > coluna `TEXT` — sem infra nova, ~30KB por barbeiro, suficiente para uma equipe;
      > **(b)** `photo_url` apontando para storage externo (S3/R2) — melhor em escala, exige
      > infra e credenciais. **Recomendação: (a) agora, (b) quando entrar o upload de fotos do
      > site** (que vai precisar de storage de qualquer forma) — aí os dois migram juntos.
      > Onde aparece: avatar nas colunas da **Agenda**, na lista de **Profissionais**, em
      > **Comissões**, e no bloco "nossa equipe" do **site**.
- [ ] **Overhaul de UX** conforme [`UX_BLUEPRINT.md`](./UX_BLUEPRINT.md): infra de feedback
      (toasts/skeletons/empty states), **tutorial de primeiro acesso + checklist de ativação**,
      Configurações em abas, Clientes com drawer + cards mobile, chips de período em
      Comissões, regra dos 2 cliques.
- [ ] **Módulo "Site" no painel** (editor white-label) — spec em
      [`UX_BLUEPRINT §3.2`](./UX_BLUEPRINT.md):
      - **Escolha do template** entre **3 opções** com preview clicável: **Clássico**,
        **Estúdio**, **Vitrine**. _(O antigo "Dark Premium" foi renomeado para "Estúdio":
        com claro/escuro em todos, nome de template descreve **layout**, não cor.)_
      - **Escolha do tema**, válida para qualquer template: ☀️ **Claro** · 🌙 **Escuro** ·
        ◐ **Ambos** (padrão — segue o aparelho do visitante e mostra um botão de alternância
        no site).
      - Cada template entregue com **as duas paletas prontas e testadas** (contraste AA nas
        duas), derivadas só da **cor de destaque** que o dono escolhe — ele nunca define cor
        de texto ou de fundo.
      - Upload de logo/fotos com crop guiado, textos, **preview ao vivo** e publicar.
        Trocar template ou tema **não perde** conteúdo.
      - Dados: `barbershop_site.template_id`, `theme_mode` (`LIGHT|DARK|AUTO`), `accent_color`.
- [x] 🎯 **Assistente de configuração do bot (wizard, substitui a lista seca)** — botão
      *"✨ Abrir assistente"* nas Configurações abre um **passo a passo** com barra de
      progresso, um cartão por etapa e *Voltar/Próximo*:
      1. **Identidade** — nome (obrigatório), endereço, mapa, Instagram
      2. **Funcionamento** — abre/fecha, dias, política de cancelamento
      3. **Serviços e preços** — tabela do menu + link de agendamento
      4. **Modo de fala da IA** — **5 presets prontos** + *Personalizado*
      5. **Pagamento** — chave PIX, copia-e-cola e formas aceitas
      → ao concluir, dispara o **resumo de boas-vindas** ao dono.
      > O wizard começa a partir das configs já salvas e sobrepõe as edições, então nunca
      > apaga campos fora dele (as **mensagens automáticas** já vêm com padrão do sistema e
      > seguem editáveis campo a campo abaixo). _A retomada "salvar e continuar" e a prévia
      > "ver como o bot fala" ficam como refinamento futuro._
- [ ] 🎯 **Config do bot vira modal único e completo (evolução do wizard)** — mudar o fluxo de
      configuração do WhatsApp: **ao abrir a tela de Configurações, já abrir automaticamente o
      modal** de configuração — e nele dá pra configurar **TUDO, num lugar só**:
      - **Conexão do WhatsApp (QR code)** — parear o número da **barbearia** (o bot) direto no
        modal, com o status "conectado/desconectado" ali mesmo.
      - **Número do admin** — cadastrar/editar o(s) telefone(s) **pessoal(is)** do dono que dão
        comando e recebem avisos (regra dos dois números).
      - **Identidade, funcionamento, serviços, modo de fala, pagamento** e **todas as mensagens
        automáticas** — nada fica só na "lista seca" de fora.
      **Ao concluir, fechar o modal e atualizar a tela** refletindo tudo que foi salvo (a tela por
      trás mostra o estado atualizado — QR conectado, número do admin, textos — sem F5).
      > Diferença para o wizard atual: hoje o assistente é opt-in (botão "Abrir assistente"),
      > cobre só os essenciais dos **textos** e deixa QR, admin e mensagens automáticas nos cards
      > separados. Aqui o modal **abre sozinho**, junta **QR + número do admin + todos os campos**
      > num fluxo único e a tela de trás **reflete o resultado** ao fechar. Reaproveita
      > `ConfigWizard`, `WhatsAppCard` (QR), `AdminsCard` (números) e o `reload()` que já existem.
- [x] **Boas-vindas no WhatsApp ao concluir a 1ª configuração** — ao salvar a config do bot
      pela 1ª vez, o sistema manda **no WhatsApp do dono** um guia curto de *como o sistema
      funciona e o que ele pode fazer*. Fecha o ciclo *configurei no computador → o bot me
      respondeu no celular* — a primeira prova de que está no ar. Enviado **uma vez**
      (`welcome_brief_sent_at`, V17), sai pelo número da barbearia para o número do admin.
      _(Ainda dispara no save da config, não no wizard — o wizard reaproveita este envio.)_
      Conteúdo em 4 blocos, curto e escaneável:
      1. **O que o bot faz sozinho** — atende no WhatsApp 24h, agenda entendendo texto e
         áudio, não marca em cima de folga, manda lembrete 1h antes, pede avaliação depois
         e chama a lista de espera quando alguém cancela.
      2. **O que você faz por aqui** (fala natural, sem decorar): _"agenda de hoje"_,
         _"manda um aviso"_, _"disparar promoção"_, _"pausar cliente"_ — e **"menu"**
         mostra tudo.
      3. **O que fica no painel** — financeiro, comissões, clube de assinatura, clientes e
         as configurações do bot (com o link).
      4. **Convite para testar** — _"Manda um **oi** de outro número pra ver seu bot
         atendendo."_
      > O tom segue o **modo de fala** escolhido no wizard — a mensagem já é uma amostra de
      > como o bot vai soar.
- [x] **Modos de fala da IA (presets de persona)** — 5 opções prontas que preenchem o
      `bot_persona` (V15) sem o dono escrever nada. Seletor na config do bot (Settings →
      Personalidade da IA); editar o texto cai em *Personalizado*:
      | Modo | Como soa |
      |---|---|
      | **Clássico** | educado e formal, trata por "senhor/senhora" |
      | **Parceiro** (padrão) | descontraído e próximo, "fala, chefe!", poucos emojis |
      | **Direto** | objetivo, frases curtas, zero enrolação |
      | **Premium** | sofisticado e discreto, sem gírias nem emoji |
      | **Brincalhão** | bem-humorado, emojis à vontade |
      | *Personalizado* | o dono escreve o próprio tom |
- [ ] Site/landing **white-label** por barbearia (marca, serviços, endereço, avaliações),
      servido em **subdomínio próprio**: `<slug>.razor.com.br` (ex.: `barbeariaficticia.razor.com.br`).
      O tenant é resolvido pelo `Host` header → `slug` → `barbershop_id` (mesmo padrão do
      `TenantResolver` de WhatsApp). Exige DNS/TLS **wildcard** `*.razor.com.br` e lista de
      slugs reservados. Detalhes em [`PLATFORM.md §6`](./PLATFORM.md).
- [ ] **Sistema de templates do site** (modelo de negócio — [`PLATFORM.md §templates`](./PLATFORM.md)):
      **3 templates prontos** inclusos (Clássico, Estúdio, Vitrine) + tema claro/escuro/ambos — o tenant escolhe
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

### 🛡️ Proteção jurídica, marca e segurança da operação *(fundador solo)*

> **Por que entra no roadmap:** boa parte disto **bloqueia a cobrança** (não se emite nota nem
> se assina contrato como pessoa física) ou é **irreversível se feito tarde** (alguém registrar
> "Razor" antes). Não é burocracia paralela — é pré-requisito comercial.
>
> ⚠️ *Este documento organiza as ações; não substitui orientação de advogado/contador.*

#### 1. Autoria e propriedade intelectual

- [ ] 🔴 **Registro de marca no INPI** — *prioridade máxima, e a mais sensível ao tempo.* O
      domínio garante só o endereço; quem registrar o nome na classe certa pode exigir que a
      gente **pare de usar "Razor"** e perca o domínio. Classe **NCL 42** (software/SaaS).
      Passos: busca prévia no site do INPI → GRU → protocolo.
      _Atenção: "razor" é palavra comum em inglês e existe marca conhecida em periféricos de
      games — vale checar a busca com atenção e ter um **plano B de nome** antes de investir
      mais em identidade visual._
- [ ] **Registro de programa de computador no INPI** — gera certidão pública de autoria e data.
      Protocola-se o **hash** do código-fonte; custo baixo e processo online.
      _Fazer depois de um marco estável (ex.: fim do beta), para registrar uma versão que valha._
- [x] **Histórico Git contínuo e limpo** — o repositório já tem trilha completa e mensagens
      descritivas, com autoria e timestamp do servidor.
- [ ] **Assinar commits com GPG** — *auditado hoje: os commits **não** estão assinados*
      (`git log --format='%G?'` → `N`). Gerar chave, cadastrar no GitHub e habilitar
      `commit.gpgsign = true`; ativar "Vigilant mode" para marcar como *unverified* qualquer
      commit não assinado em nome do dono.

#### 2. Blindagem jurídica

- [ ] 🔴 **Abrir a empresa (CNPJ)** — **antes da primeira cobrança**. SLU costuma ser o modelo
      do fundador solo; a responsabilidade fica limitada ao capital, separando o patrimônio
      pessoal de dívidas ou processos do SaaS. Também é o que permite gateway PJ e nota fiscal.
- [ ] **Termos de Serviço próprios** (nada de modelo genérico da internet), prevendo:
      **licença de uso** (o cliente usa o serviço; não recebe o código nem a arquitetura),
      **limitação de responsabilidade** (danos indiretos, lucros cessantes, indisponibilidade
      de terceiros — inclusive **banimento de número pelo WhatsApp**, risco real do canal
      não-oficial: ver [`WHATSAPP_CHANNEL`](./WHATSAPP_CHANNEL.md)), e **proibição de engenharia
      reversa/scraping/cópia de funcionalidades**.
- [ ] **Política de Privacidade + LGPD** — o Razor trata dado pessoal de **terceiros** (os
      clientes das barbearias), então somos **operador** e a barbearia é **controladora**:
      isso precisa estar escrito, com **contrato de tratamento de dados** no ToS.
      Implementar: consentimento no cadastro, exportar/excluir meus dados, e retenção definida.

#### 3. Segurança da infraestrutura *(status auditado em 2026-07-23)*

- [ ] **E-mail institucional no domínio + 2FA por app em tudo** (nunca só SMS): GitHub,
      provedor de nuvem, DNS, gateway de pagamento, OpenAI. Contas hoje provavelmente em
      e-mail pessoal — migrar antes de crescer.
- [x] **Sem segredo no front** — auditado: nenhuma chave/API key no `web/src` nem no bundle.
      Tudo por variável de ambiente no servidor.
- [x] **Sem source map em produção** — o build do Vite não emite `.map` (confirmado no `dist/`).
- [x] **CORS restrito por origem** — lista explícita em `razor.security.cors-allowed-origins`,
      nunca `*`. _(Melhoria: restringir também os headers, hoje `*`.)_
- [x] **Segredos por variável de ambiente**, com **fail-closed** onde importa
      (`WEBHOOK_TOKEN`, `ONBOARDING_API_KEY`).
- [x] **Rate limiting no login** (5 falhas/15 min por email+IP).
- [ ] **Rate limiting geral da API** — hoje existe **só no login**. Falta limite por
      IP/tenant nas rotas `/api/admin/**` e no webhook, para barrar scraping e abuso.
- [ ] **Backup fora da máquina** — o script existe e é testado, mas grava **local**; falta
      enviar para storage externo e testar restauração periodicamente.
- [ ] **Rotação de segredos** documentada (JWT, webhook, OpenAI) e checklist de resposta a
      incidente.

---

### Fase 5 — Diferenciais de IA 🧠

> **Objetivo:** ampliar a distância para os concorrentes usando nossa cunha.

- [x] **Admin fala natural** — o dono não precisa decorar o menu: "manda um aviso",
      "quem vem hoje?", "disparar promoção" viram ação (`AdminIntentResolver`).
      Saudação responde com o resumo do dia; texto não entendido **ensina por exemplo**.
- [x] **Cliente conversacional na abertura** — cliente novo/ocioso que só cumprimenta é
      recebido como por um **atendente** (nome da barbearia + convite para dizer dia e
      serviço), não com o menu numerado. "Oi, quero marcar" já vai direto para a IA.
      _(De quebra: `MenuOptions` tinha o nome "Barbearia Ficticia" cravado — corrigido para
      usar o nome do tenant.)_ Falta o passo mais fino: a **primeira** mensagem da IA também
      soar como atendente (hoje a abertura é determinística; a IA entra da 2ª em diante).

> 📱 **Regra dos dois números (documentar no onboarding e validar no wizard):**
> - **Número do BOT** = o **da barbearia** — é o que aparece para o cliente e o que é pareado
>   pelo QR code. Idealmente um chip/WhatsApp **só da barbearia**.
> - **Número do ADMIN** = o **pessoal do dono** (e da equipe), cadastrado em *Equipe & acesso*.
>   É por ele que o dono comanda o bot e recebe os alertas.
>
> Não é só recomendação: se o admin usar **o mesmo número do bot**, as mensagens dele chegam
> ao webhook marcadas como `fromMe` e são **descartadas na triagem** — ele nunca conseguiria
> dar um comando. **A fazer:** bloquear no wizard/onboarding cadastrar como admin o mesmo
> número conectado como bot, com mensagem explicando o porquê.

> 🔒 **Invariante do menu (vale para CLIENTE e ADMIN):** quem digitar **"menu"** — ou
> "opções", "comandos" — **sempre** recebe o menu completo. O menu não é eliminado, é
> **rebaixado de porta de entrada para atalho sob demanda**. Ele continua sendo também o
> *fallback* quando a IA estiver indisponível.
> *Status: já funciona nos dois fluxos* — cliente (`MenuOptions.isAskingForMenu`) e admin
> (`AdminIntentResolver` → `MENU`). A mudança da Fase 5 é só na **abertura** da conversa.
- [ ] Inteligência de retenção proativa (IA identifica e reengaja quem vai sumir).
- [ ] Upsell/cross-sell por IA (sugere serviços/planos no contexto da conversa).
- [x] Persona do bot configurável por tenant (`bot_persona`, V15), com os **5 modos de
      fala prontos** já expostos na config do bot (ver Fase 3).

---

### Fase 6 — Escala & Operação ⚙️

> **Objetivo:** aguentar muitas barbearias com confiança operacional.

- [x] Tirar o estado da conversa da memória → **Redis** (`razor.state.store=redis`),
      habilitando múltiplas instâncias e zero perda de contexto ao reiniciar. _(Ver Fundação
      → StateStore parte 3.)_
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

### 🐛 Pendências & débitos conhecidos

- [x] **Topo do Painel renderizando vazio (`ActivationChecklist`)** — o card aparecia como uma
      barra escura sem conteúdo. Causa: a versão de 25/07 trocou as classes do design system por
      estilos inline e engolia falha de API com dados-padrão (`DEFAULT_STATUS`/`DEFAULT_BOT_TEXTS`),
      então uma barbearia já configurada via um checklist zerado. Agora o componente usa as classes
      `.checklist`/`.cl-*`, mostra skeleton enquanto carrega e **não aparece** se os dados não vierem.
- [x] **Painel com números fabricados** — o Dashboard exibia faturamento, meta mensal, ranking de
      barbeiros e métricas de IA **hardcoded** (R$ 42.850, "Lucas Navalha", 94.2%), com fallback que
      substituía o dado real por exemplo sempre que a API retornava zero atendimentos. Removido: todo
      número da tela agora vem de `/api/admin/reports/{financial,agenda,commissions}` e período sem
      movimento aparece como período sem movimento.
- [x] **Auto-login de demonstração** — `AuthContext` criava uma sessão `OWNER` falsa (`demo-token`)
      quando não havia sessão salva, entrando no painel sem passar pelo login. Removido.
- [x] **Cards espremidos no painel (barras vazias)** — o `razor-design-system.css` pôs
      `overflow: hidden` no `.card`. Em flexbox isso **zera o min-height automático** do item, e
      como `.content` é `display: flex; flex-direction: column`, o `flex-shrink` esmagava os cards
      que são filhos diretos da página (checklist e meta) até virarem uma listra, cortando o
      conteúdo. Os KPIs escapavam por estarem dentro do `.bento-grid`. Corrigido com
      `.content:not(.flush) > * { flex-shrink: 0 }` — quem rola continua sendo o `.main`.
- [x] **Enxurrada de requisições no painel** — o gráfico montava a série pedindo um resumo
      financeiro por ponto (7 requisições) e, com as demais, o painel abria **16 chamadas
      simultâneas**; passando do limite de ~6 conexões do navegador, o checklist ficava preso na
      fila mostrando skeleton. Pior: cada chamada varria todos os agendamentos do tenant. Agora
      `GET /reports/revenue-series?period=` devolve a série pronta em **uma** chamada e **uma**
      varredura.
- [ ] **Dados de exemplo para desenvolvimento local** — sem histórico o painel fica (corretamente)
      vazio, o que dificulta ver gráfico e ranking ao desenvolver. Se for feito, tem que ser um
      **script de seed de ambiente local** (perfil `local`), **nunca** uma migração Flyway: migração
      roda em produção e injetaria movimento financeiro falso no banco de uma barbearia real.
- [x] **Meta de faturamento mensal (V19)** — em vez do alvo fixo de R$ 50.000 que estava no
      código, o dono define a própria meta em *Configurações → Meta do mês*
      (`monthly_revenue_goal` em `barbershop_settings`, endpoint próprio
      `/api/admin/settings/goals` para o PUT das configs do bot não zerar a meta). O painel
      compara com o faturamento real; sem meta definida, mostra só o convite para definir.
- [ ] **Métricas ainda sem fonte de dados** — taxa de ocupação de cadeiras, no-show e "clientes
      resgatados pela IA" não existem no backend. Enquanto não existirem, não entram na tela.

---

_Última atualização: 2026-07-27 (painel religado a dados reais; removidos auto-login demo e números
fabricados; checklist de ativação corrigido) · Documento vivo._

