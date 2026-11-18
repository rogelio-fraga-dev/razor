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
      > ✅ **Lacuna fechada na V28:** serviço lançado em comanda **sem** vínculo com agendamento
      > agora entra no `/reports/financial`; comanda **com** vínculo é ignorada porque o serviço
      > já foi contado pelo agendamento. Ver item 4 da frente do cliente final.
- [~] **Clube de assinatura**: catálogo de **planos configuráveis** pronto (`subscription_plans`,
      painel). _(Falta: identificação assinante x avulso no agendamento e cobrança recorrente — Fase 4.)_
- [~] **Relatórios/DRE**: financeiro estruturado, comissões e **DRE (V31)** prontos no painel.
      _(Falta: dashboard de retenção — "quem some", frequência — e taxa de ocupação.)_
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
- [x] **Pacotes (V34)** (ex.: 10 cortes pré-pagos) com **baixa automática** no atendimento —
      diferente do clube (recorrência); é crédito pré-pago. **Catálogo** por barbearia (nome
      único, serviço coberto ou "qualquer serviço", usos, preço, validade em dias, desativação
      em vez de exclusão) e tela própria (*Pacotes*). **Venda** ao cliente lançada no **caixa
      aberto** como movimento de venda — sem caixa aberto é recusada, igual ao balcão. Nome,
      serviço, preço, validade e **valor por uso** (preço ÷ usos) são **congelados** no pacote
      do cliente. **Baixa** no fechamento da comanda: com o telefone do cliente na comanda, cada
      serviço coberto por pacote com saldo e dentro da validade sai a **R$ 0,00** (o item é
      dividido se o saldo for menor que a quantidade) e consome um uso; o que vence primeiro é
      gasto primeiro, e o pacote específico do serviço antes do "qualquer serviço". Cada baixa
      grava linha em `package_usages`, **única por item de comanda** — reprocessar não desconta
      duas vezes —, com trava de linha no saldo e `CHECK remaining_uses >= 0` no banco. Comanda
      toda coberta fecha com total zero e **sem** movimento de caixa (não entrou dinheiro).
      **Comissão:** o barbeiro continua comissionado, mas sobre o **valor por uso do pacote**, não
      sobre o preço de tabela — foi isso que entrou por aquele corte; aplicado aos agendamentos
      cuja comanda usou pacote. O **bot** recebe no prompt os pacotes usáveis do cliente (saldo e
      validade), para não cobrar de novo um corte já pago. Clientes com saldo aparecem marcados
      na tela *Clientes*.
      > ⚠️ **Limites conhecidos:** (1) a baixa só existe na comanda — agendamento não tem estado
      > "concluído", então não há gancho natural para dar baixa sem comanda; (2) comanda sem
      > agendamento já não gera comissão de serviço hoje, e o pacote segue a mesma regra;
      > (3) o `/reports/financial` não soma a venda do pacote (ela está no caixa) e continua
      > contando o agendamento coberto pelo preço de tabela — reconhecer receita do pacote no
      > financeiro/DRE fica para depois; (4) vender pacote é só do dono (a recepção vê o saldo).
- [ ] **Múltiplas formas de pagamento** por atendimento (dinheiro, débito, crédito, PIX,
      split) — hoje só PIX/presencial/pendente.
- [ ] **Limite de agendamentos por cliente** — impede o cliente de reservar vários horários
      "para garantir" e travar a agenda. _(Boa ideia da Frizzar; barato de implementar.)_

**🟠 Alto valor (diferenciam na venda)**

- [x] **Contas a pagar e contas recorrentes (V31)** — aluguel, energia, produtos, impostos.
      Ver *Contas a pagar e DRE*, abaixo.
- [x] **DRE** (V31) e **taxa de ocupação** (V32) feitos. **Frequência/retenção de clientes**
      continua aberta. Ver *Expediente estruturado e taxa de ocupação*, abaixo.
- [x] **Estoque**: produtos, estoque mínimo com alerta e **baixa automática** na venda — feito na
      V24 (ver Fase 2).
- [x] **Vales e adiantamentos (V26)** dos profissionais, descontados na comissão. O vale pertence
      a uma **data** e é descontado no acerto do período que a contém — sem estado "quitado" para
      dessincronizar. Sair do caixa é opcional (igual ao acerto V25): vale em espécie vira sangria,
      vale por fora não mexe na gaveta. O saldo **pode ficar negativo** quando o vale passa da
      comissão: o barbeiro deve à barbearia, e zerar o número esconderia isso.
- [x] **Comissão por produto** (V23: `product_commission_percent` por barbeiro), **comissões
      pagas** (V25) e **por serviço avulso** (V27: `services.commission_percent`, nulo = usa o
      percentual geral do barbeiro).
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
- [x] **SPA React** (painel web em `/web`, Vite+TS) consumindo a API: **MVP amplo** —
      login, Painel (financeiro do dia + agenda), Serviços, Profissionais (+comissão),
      Clientes, Planos, Comissões, Configurações (status do bot + WhatsApp por QR +
      textos + admins) e Perfil, com a identidade RAZOR aplicada.
      _(Falta: agenda multiprofissional interativa.)_
- [x] **Perfil completo do barbeiro** _(V37)_ — o
- [x] **Agenda multiprofissional interativa** (UX_BLUEPRINT §4.6) — `/api/admin/agenda` +
      `AgendaPanelService`, tela *Agenda*:
      - **Dia** com uma coluna por barbeiro (grade do expediente do tenant, slots de 30min,
        altura do bloco = duração do serviço) e **Semana** por barbeiro; folgas/almoço e dias
        fechados hachurados; coluna "Outros / sem barbeiro" para nada sumir da grade
        (agendamento sem barbeiro ou de barbeiro inativo); avatar com foto quando o perfil
        tiver `photoUrl`, iniciais senão.
      - **Clique no horário vazio** → novo agendamento (busca/cadastro do cliente pelo
        telefone, serviço do catálogo, barbeiro e hora preenchidos), gravado pelo
        `AgendaBookingService` (mesmo caminho da IA: lembrete, fidelidade, limites).
      - **Clique no bloco** → gaveta: remarcar, trocar barbeiro, cancelar, **compareceu/faltou**
        (V39 `attendance_status`; falta cancela o pedido de avaliação e libera a cadeira) e
        abrir a comanda vinculada (`appointment_id`).
      - **Arrastar** (pointer events, sem dependência nova) para outro horário/barbeiro/dia,
        com troca otimista e rollback + toast quando o servidor recusa. Validação no
        servidor: dia aberto, expediente, folga/bloqueio, sobreposição pela duração,
        barbeiro ativo, horário não passado.
      - Remarcar/cancelar **avisa o cliente no WhatsApp** (desligável) e o **lembrete
        acompanha o novo horário**.
      - Celular: um barbeiro por vez (chips + deslizar); gaveta vira folha de baixo; no toque
        a remarcação é pela gaveta (arrastar brigaria com a rolagem).
      - RBAC: leitura para todos os papéis; escrita para dono e recepção.
      _(Falta: barbeiro editar só os próprios horários — exige ligar o usuário do painel ao
      cadastro de profissional; restrição "barbeiro atende este serviço" quando o perfil do
      profissional tiver a lista; tirar faltas (NO_SHOW) da receita prevista nos relatórios;
      oferecer o horário liberado à lista de espera quando o cancelamento vem do painel.)_
- [x] **Cancelar um horário não derruba mais os lembretes dos outros** — as tarefas
      (lembrete/avaliação) nascem presas ao agendamento (V39 `scheduled_tasks.appointment_id`);
      antes o cancelamento era por telefone e apagava os lembretes de todos os horários do
      cliente. Tarefas pré-V39 são reconhecidas pelo horário exato.
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
      > **Feito (V37):** decisão **(a)** aplicada — o painel recorta o quadrado central e reduz
      > para 256px JPEG no navegador (canvas); o backend (`ProfessionalProfileRules`) só aceita
      > `data:image/jpeg|png|webp;base64,`, até **60KB** decodificados e com a assinatura do
      > arquivo batendo com o tipo declarado (SVG, `http:`, `javascript:` recusados). Instagram
      > só http(s) (ou `@perfil`, normalizado). **Serviços que atende** em
      > `professional_services`; **vazio = todos** (sem regressão). A restrição vale na
      > auto-atribuição **e** na escolha explícita (`ProfessionalAssignmentService` +
      > `ServiceCapability`, que casa a string livre da IA com o catálogo: nome exato →
      > composição "corte e barba" exige todos → abreviação "corte" aceita qualquer um; texto
      > fora do catálogo não restringe), e o bot só oferece ao cliente quem faz o serviço,
      > chamando cada um pelo **apelido**. Painel: Profissionais em cartões com avatar e modal
      > com foto, bio, especialidades (chips), Instagram e checklist de serviços; avatar em
      > Comissões; `photoUrl` no DTO para a Agenda. Site: bloco de equipe com foto (ou inicial),
      > bio, especialidades e Instagram, em variante própria por template; o `PublicSiteResponse`
      > só leva barbeiro ativo e só campos de vitrine.
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
- [x] 🎯 **Config do bot vira modal único e completo (evolução do wizard)** — mudar o fluxo de
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
      **Entregue (2026-11-17):** o modal (`ConfigWizard`) tem 8 passos com barra de progresso —
      QR com selo conectado/desconectado ao vivo, admins (`AdminsEditor`, com a regra dos dois
      números explicada), identidade, funcionamento, serviços, modo de fala, PIX e mensagens.
      Abre sozinho quando falta item obrigatório (`evaluateConfig`), já no 1º pendente, uma vez
      por sessão (`sessionStorage` em try/catch); senão, "Revisar configuração" abre. **Salva a
      cada passo** (`PUT ...?welcome=false`) e ao fechar com alteração pendente; o resumo de
      boas-vindas só sai no "Concluir". Parte de `{...config salva}`, então nunca apaga campo
      que não mostra. Ao fechar, a tela recarrega tudo. O formulário "O que o bot fala" virou
      um resumo com "Editar" por assunto (abre o modal no passo certo) e Configurações ganhou
      abas (Status · WhatsApp · Bot & IA · Equipe & acesso · Barbearia · Site, `?tab=`).
- [x] **Boas-vindas no WhatsApp ao concluir a 1ª configuração** — ao salvar a config do bot
      pela 1ª vez, o sistema manda **no WhatsApp do dono** um guia curto de *como o sistema
      funciona e o que ele pode fazer*. Fecha o ciclo *configurei no computador → o bot me
      respondeu no celular* — a primeira prova de que está no ar. Enviado **uma vez**
      (`welcome_brief_sent_at`, V17), sai pelo número da barbearia para o número do admin.
      _(Dispara no "Concluir" do modal de configuração; os saves por passo usam `?welcome=false`.)_
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

### 🎯 Frente do CLIENTE FINAL — sequência de execução *(definida em 2026-07-27)*

> **Por que esta frente existe:** hoje todo o valor do Razor está no painel do dono. O cliente
> final só existe como telefone no WhatsApp. O concorrente (CashBarber) tem app do cliente com
> plano visível, agendamento self-service e pagamento — é a maior lacuna competitiva.
>
> **Referência analisada:** 13 telas do app do cliente do CashBarber (celular). O modelo de
> plano deles é mais rico do que o nosso `subscription_plans` atual:
> - Benefício **por serviço**: `100% OFF` + cota (`999 gratuitos por mês`) + restrição de dia
>   (`Segunda, terça ou quarta`)
> - Benefício **por categoria**: `Extras 10% OFF`, `Cosméticos 10% OFF` (produtos)
> - **Regra de agenda**: `Agenda liberada com 7 dias de antecedência`
> - Preço sempre exibido como `De ~~R$ 35,00~~ / Por R$ 0,00`; total do agendamento já com desconto
> - Upsell no agendamento: *"Sentimos sua falta! Você realizou X faz tempo, deseja adicionar?"*
>
> **A ordem abaixo é de dependência, não de preferência** — cada item destrava o seguinte.
> Construir fora de ordem gera retrabalho.

- [x] **3. Cupons de desconto e fidelidade de verdade (V29)** — o cartão fidelidade já mandava um
      código ao cliente que **nunca era gravado**: o caixa não validava, nada impedia usar duas
      vezes e não havia registro do desconto dado. Agora cupom é entidade com validade, limite de
      usos, valor mínimo, cupom pessoal × campanha aberta e histórico de uso. Aplicado no
      fechamento da comanda, validado **antes** de lançar no caixa. _(Também corrigidos dois
      "Barbearia Ficticia" cravados: na recompensa de fidelidade e no lembrete de agendamento.)_
- [x] **1. Motor de benefícios de plano + comissão por serviço (V27)**
      `plan_benefits` (alvo = item específico **ou** todos de um tipo, desconto %, cota mensal,
      dias da semana) e `services.commission_percent` (o dono define a comissão de cada serviço;
      nulo = usa o percentual geral do barbeiro, V23). `PlanPricingService` resolve o preço do
      item para o cliente: benefício específico ganha do geral, respeita dia e cota.
      _Cota só é verificada em serviço (contando atendimentos do mês); venda de produto não
      guarda o cliente, e no modelo real produto ganha percentual, não cota._
- [x] **2. Três planos realistas** (Bronze R$ 69,90 / Prata R$ 94,90 / Ouro R$ 119,90) com corte
      ilimitado, criados por `POST /plans/suggested` (idempotente, nunca automático). Tela de
      benefícios em Planos, espelhando o app do cliente. Landing ligada ao painel no mesmo commit.
- [x] **4. Comanda ↔ agendamento (V28)** — `comandas.appointment_id`, opcional e com índice único
      parcial (uma comanda por agendamento; duas seria cobrança dupla). O financeiro passou a somar
      o serviço de comandas **sem** vínculo — receita real que o laço de agendamentos não via — e a
      **ignorar** as com vínculo, porque aquele serviço já foi contado pelo agendamento. Fecha a
      lacuna registrada na V21 sem risco de contar duas vezes.
- [ ] **8. Contas do cliente final** *(movido para o fim — decisão de 2026-07-27: fechar a parte do dono e do bot primeiro)* (`customer_accounts`) — login próprio, vínculo ao telefone
      do WhatsApp, histórico. **Opcional**: quem só quer marcar um corte continua sem cadastro.
- [ ] **6. Pagamento pelo cliente** *(cartão apenas VISUAL por ora; gateway a pesquisar)* — escolher entre **pagar no app** ou **pagar ao barbeiro**.
      Cartão **tokenizado no gateway**; o número **nunca** toca nosso banco nem nossos logs.
      Idempotência na cobrança, verificação de assinatura no webhook do gateway, e o pagamento
      no app entra no caixa como venda igual aos demais.
- [ ] **7. Bot reconhece e recomenda** — cliente novo agenda normalmente e, ao final, o bot
      convida a criar conta para ver planos e descontos. Assinante é reconhecido pelo telefone
      e já recebe o preço com benefício na conversa.
- [ ] **5. Três sites white-label** — Clássico, Estúdio e Vitrine (direção visual livre) — templates prontos (ver *Sistema de templates* acima),
      com vitrine de planos e agendamento online ligado à mesma agenda do bot.
- [ ] **9. Melhorias sobre a base** — upsell "sentimos sua falta" no agendamento, seleção de
      múltiplos serviços numa marcação, "sem preferência" de barbeiro, e agenda liberada por
      antecedência do plano (`booking_window_days`, já criado na V27).

---

### 👤 Contas do CLIENTE FINAL (nível 3) — *detalhamento do item 4 acima*

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
- [x] **Dados de exemplo para desenvolvimento local (2026-07-28)** — sem histórico o painel fica
      (corretamente) vazio, o que dificultava ver gráfico e ranking ao desenvolver. Resolvido com
      `scripts/seed-demo.sql`: um script avulso, **fora do Flyway**, exatamente como este item
      exigia — migração roda em produção e injetaria movimento financeiro falso no banco de uma
      barbearia real. Detalhes na seção *Ambiente local em um comando*, abaixo.
- [x] **Meta de faturamento mensal (V19)** — em vez do alvo fixo de R$ 50.000 que estava no
      código, o dono define a própria meta em *Configurações → Meta do mês*
      (`monthly_revenue_goal` em `barbershop_settings`, endpoint próprio
      `/api/admin/settings/goals` para o PUT das configs do bot não zerar a meta). O painel
      compara com o faturamento real; sem meta definida, mostra só o convite para definir.
- [~] **Métricas ainda sem fonte de dados** — **taxa de ocupação existe desde a V32**. No-show e
      "clientes resgatados pela IA" continuam sem fonte; enquanto não existirem, não entram na tela.

---

### 🐳 Ambiente local em um comando *(2026-07-28)*

> Antes desta rodada, ligar o Razor exigia Docker **mais** JDK 17 **mais** Node, três terminais e
> seis passos manuais de `INICIALIZACAO.md`. Agora `docker compose up -d --build` sobe o sistema
> inteiro, e `.\scripts\seed.ps1` enche o banco de movimento para o painel ter o que mostrar.

**O stack**

- [x] **`Dockerfile` do backend** — build em dois estágios (Maven → JRE), usuário sem privilégio,
      cache do `~/.m2` entre builds. Testes **não** rodam na imagem: os de integração usam
      Testcontainers e precisariam de Docker dentro do Docker.
- [x] **`web/Dockerfile` + `web/nginx.conf`** — o Vite gera estático e o nginx serve, encaminhando
      `/api` para o backend. Isso põe painel e API na **mesma origem**, então não existe CORS no
      caminho — é o mesmo papel que o proxy do Vite faz em `npm run dev`. O upstream fica numa
      variável com `resolver 127.0.0.11` de propósito: sem isso o nginx resolveria `backend` uma
      única vez no boot e morreria de vez se o container da API fosse recriado.
- [x] **`docker-compose.yml` reescrito** — banco + API + painel no `up` padrão. A Evolution API
      saiu para trás do profile `bot` (`docker compose --profile bot up -d`) porque só faz sentido
      quando se vai parear um WhatsApp de verdade; sem ela o resto funciona inteiro.
- [x] **Sobe sem configuração nenhuma** — todas as variáveis têm default no compose
      (`.env.docker.example` documenta). A `OPENAI_API_KEY` nasce como `sk-dummy-local`, não
      vazia: o cliente da OpenAI recusa chave em branco no boot, e o painel já trata chave com
      "dummy" como IA não configurada. Assim o stack sobe com a IA honestamente desligada em vez
      de não subir.
- [x] **`EVOLUTION_BASE_URL` em `application.yml`** — o endereço da Evolution era literal
      (`http://localhost:8080`), que dentro de um container aponta para o próprio container.
      Virou placeholder com o mesmo valor como default, então nada muda fora do Docker.
- [x] **`dev.ps1` passou a subir o Postgres pelo compose** — ele ligava um container solto
      `razor-postgres`, que depois desta mudança brigaria pela porta 5433 com o do compose. Agora
      há uma definição só de banco. Os containers soltos antigos (`razor-postgres`,
      `razor-evolution`) foram removidos, com dump preservado em `backups/`.

**O seed**

- [x] **`scripts/seed-demo.sql`** — uma barbearia fictícia ("Navalha de Prata", slug
      `demo-navalha`) com ~60 dias de movimento: 3 barbeiros, 7 serviços, 5 produtos, 3 planos com
      benefícios, 18 clientes, 335 comandas, 52 caixas fechados, cupons, vales e comissões pagas.
      Login `dono@navalhademo.local` / `demo12345` (e um `RECEPTION` e um `BARBER`).
      **Desde a V31, também 4 meses de contas a pagar** (~R$ 5 mil/mês de custo fixo contra
      ~R$ 8,6 mil de receita → margem de ~39%). Sem elas o DRE da demo mostraria
      "sobrou = faturamento", que é exatamente a leitura enganosa que a V31 corrige.
      As despesas nascem **pagas por fora do caixa**: gerar sangria quebraria a invariante
      `closing_expected` que o seed garante — e é o realista, já que aluguel e imposto saem por
      transferência, não da gaveta. A conta "a vencer" é ancorada em `CURRENT_DATE`, não num dia
      fixo: com dia fixo, rodar o seed no fim do mês deixaria a tela sem esse estado.
- [x] **Escopo travado num tenant de UUID fixo** — o script começa apagando **esse** tenant e só
      ele, então é re-executável e não encosta em nenhuma outra barbearia. É o que o torna seguro
      de rodar num banco que já tenha dados.
- [x] **O seed respeita as invariantes de dinheiro** — e isso não é preciosismo: um seed que as
      viola faz o painel mostrar números que o sistema jamais produziria, e aí o teste não vale
      nada. Verificado por consulta depois de rodar, tudo em zero divergência: total da comanda =
      soma dos itens − desconto; uma comanda fechada = um movimento de caixa de mesmo valor;
      `closing_expected` = fundo + suprimentos − sangrias + vendas em dinheiro (a mesma conta de
      `CashRegisterService.expectedDrawer`); saldo do produto = entradas − saídas no histórico.
      O saldo de estoque vem do `RETURNING` do próprio `UPDATE`, que é o único jeito de o
      histórico nunca discordar do saldo atual.
- [x] **Pontos de fidelidade derivados, não inventados** — vêm da contagem de comandas fechadas
      que existem de fato, mesmo critério do sistema. Coerente com a regra de nunca fabricar
      número no painel: aqui o dado é fictício por ser um ambiente de demonstração, mas continua
      sendo *consequência* do movimento, não um valor solto.
- [x] **Datas relativas a `CURRENT_DATE`** — o "mês atual" do relatório sempre tem movimento, não
      importa quando o seed rodar. Domingo fica zerado porque a barbearia não abre.

**Achado ao validar: a lacuna de PIX e horário já estava fechada**

- [x] **Chave PIX e horário de funcionamento por tenant** — estavam anotados como a pendência mais
      urgente ("o bot envia dado errado hoje") e como o próximo passo da sessão. Ao popular
      `barbershop_settings` para o seed ficou claro que **já foram resolvidos** na leva V19→V30:
      as colunas `pix_key`, `opening_time`, `closing_time` e `working_days` existem,
      `BarbershopSettingsService` as lê (com 09:00/19:00 apenas como *default* quando em branco),
      `AgendaService.montarHorariosLivres` gera os horários livres dentro do expediente configurado
      e o painel expõe os campos em *Configurações* e no `ConfigWizard`. Não é preciso uma V31 para
      isso — **a próxima tarefa da frente do cliente final é o item 5 (sites white-label)**.

---

### 💸 Contas a pagar e DRE *(V31 — 2026-07-28)*

> Até aqui o sistema só sabia o que **entra**. Fechar o mês com metade da conta é pior do que não
> fechar: o dono olha R$ 18 mil de faturamento e acha que teve R$ 18 mil de resultado. **447
> testes verdes** (eram 427).

- [x] **`expenses` (V31)** — descrição, categoria, valor, vencimento, pagamento, recorrência e
      observação, escopado por tenant. Migração **validada no Postgres real** (Flyway aplicou,
      Hibernate validou o mapeamento, `\d expenses` conferido) — o passo que faltou na V28.
- [x] **Tela *Despesas e resultado*** — DRE no topo, contas abaixo. Ficam juntos de propósito: o
      número que o dono quer ("quanto sobrou?") só existe por causa das contas logo abaixo.
- [x] **Endpoints** `/api/admin/expenses` (CRUD, quitação, repetição) e
      `/api/admin/reports/dre`. Nasceram **OWNER-only sem eu tocar no `SecurityConfig`** — é o
      fecho fail-closed do RBAC funcionando como projetado.

**Decisões**

- **"A pagar" × "pago" é derivado de `paid_at IS NULL`, não um enum de status** — mesmo critério
  do vale (V26): estado paralelo ao fato é mais uma coisa para dessincronizar.
- **Vencimento e pagamento são datas separadas.** O aluguel vence dia 5 e às vezes é pago dia 8:
  o DRE precisa da competência, a conferência do caixa precisa do desembolso. Uma coluna só
  obrigaria a escolher qual dos dois mentir.
- **O DRE conta por competência (vencimento).** Sem isso, um mês em que o dono atrasou dois
  boletos apareceria lucrativo e o seguinte no prejuízo, sem nada ter mudado na operação. Por
  isso `expensesPaid` e `expensesUnpaid` vêm separados: somam o mesmo total, mas dizem quanto já
  saiu de fato.
- **Sair do caixa é opcional** — despesa paga em espécie vira sangria; boleto ou PIX não mexe na
  gaveta. Mesmo padrão do acerto (V25) e do vale (V26).
- **Conta paga pelo caixa não pode ser editada nem apagada** — o valor já virou sangria com aquele
  número; reescrever a despesa sem reescrever o movimento faria a gaveta discordar do relatório
  em silêncio. A correção é um suprimento explícito, e a mensagem de erro diz isso.
- **Recorrência é marcador, não gerador automático.** Criar as parcelas futuras sozinho encheria
  a tela de dívida que ninguém conferiu — "o sistema criou uma conta que eu não reconheço" é pior
  do que digitar de novo. A repetição é comando do dono e **idempotente** (índice único parcial no
  banco, além da checagem na aplicação), mesmo critério do `POST /plans/suggested`.
- **`marginPercent` é nulo sem receita**, nunca `0%`: zero sugere margem nula quando o certo é
  "não há o que medir". Mesma regra para o `% do faturamento` de cada categoria.
- **O DRE reusa o `financialSummary`** em vez de recalcular a receita — duas telas mostrando
  faturamentos diferentes para o mesmo mês destruiria a confiança no painel inteiro. Há teste
  específico para isso.

**Verificado contra a API rodando**, com o seed: DRE de julho fechou receita 8.637,00 − despesa
2.930,50 = **5.706,50** (margem 66,1%), aluguel comendo 28,9% do faturamento; repetir recorrentes
criou 1 conta na 1ª vez e `[]` na 2ª; quitar duas vezes deu 400; valor negativo deu 400.

---

### 🕒 Expediente estruturado e taxa de ocupação *(V32 — 2026-07-28)*

> **482 testes verdes** (eram 461).

**O bug que estava em produção**

- [x] **`working_days` era texto livre que ninguém interpretava.** Era só concatenado numa frase
      do prompt da IA. O `AgendaService` monta os horários a partir de `opening_time`/`closing_time`
      e dos bloqueios (V16) — e de mais nada. Sem um bloqueio recorrente de domingo cadastrado à
      mão, **o bot oferecia e marcava no domingo** enquanto dizia ao cliente que abre de segunda a
      sábado. Prometia uma coisa e agendava outra.
- [x] **V32: `working_weekdays`** — lista ISO (1=seg…7=dom), a mesma numeração de
      `java.time.DayOfWeek`, para não existir conversão no meio do caminho. A frase mostrada ao
      cliente passou a ser **derivada** dos dias: não pode mais discordar do que a agenda faz.
- [x] **Seletor de dias no painel** (Configurações e assistente), no lugar do campo de texto.

**Decisões**

- **Backfill determinístico: todo mundo nasce Seg–Sáb.** Não é chute — `"Seg a Sáb"` é o próprio
  default do sistema, ou seja, o que o bot já vinha dizendo. A migração faz a *agenda* obedecer o
  que o bot já prometia. Parsear o texto foi descartado: `"Seg a Sáb"` é um **intervalo**, e
  procurar tokens devolveria `{segunda, sábado}` — dois dias em vez de seis, pior que o default.
- **Campo omitido no PUT preserva o expediente.** Cliente antigo da API não pode fechar a
  barbearia todo dia por não conhecer um campo novo.
- **`working_days` não foi dropado na mesma migração.** Apagar dado é irreversível; está marcado
  como deprecado e sai numa versão futura.

**Taxa de ocupação** (`/api/admin/reports/occupancy`)

Horas vendidas ÷ horas disponíveis, por barbeiro e no total. Disponível = dias **abertos** ×
jornada, menos folgas e almoços daquele barbeiro. Vendidas usam a **duração real** do serviço:
contar "atendimentos" faria uma agenda de combos parecer mais vazia que uma de cortes.
Agendamento sem barbeiro aparece à parte em vez de ser distribuído por chute, e a taxa vem
**nula** (não `0%`) quando não houve hora disponível.

> Conferido contra o seed: 24 dias abertos em julho (28 dias − 4 domingos) e Rafael com 260h
> contra 264h dos colegas — exatamente as 4 horas do almoço recorrente de quarta.

**Dois achados que só apareceram exercitando a API de verdade**

- [x] **Jackson lia número como *ordinal* do enum.** `Set<DayOfWeek>` no payload fazia
      `[1,2,3,4,5,6]` — Seg a Sáb em ISO — virar **Ter a Dom** em silêncio (ordinal 0=MONDAY), e
      `7` estourar com 400. Erro de um dia que só apareceria quando o bot recusasse segunda-feira.
      O payload passou a ser `Set<Integer>` ISO explícito, com conversão própria. É exatamente a
      armadilha das duas convenções que o javadoc do `WorkingDays` já alertava — e ela reapareceu
      na camada JSON, que o javadoc não cobria.
- [x] **Teste com data não-determinística.** `AgendaServiceTest` usava `hoje + 5 dias`, que uma vez
      por semana cai num domingo. Até a V32 não importava, porque a agenda ignorava o dia; agora
      quebraria em 1 de cada 7 execuções. Ancorado num dia aberto.

---

### 📄 Paginação das listas *(2026-07-28)*

> As telas carregavam a lista inteira e filtravam no navegador. Com 18 clientes de demonstração
> isso não incomoda; com a base real de uma barbearia de dois anos, a tela de Clientes baixaria
> milhares de linhas a cada abertura. **461 testes verdes** (eram 447).

**O que foi paginado — e o que não foi**

| Lista | Decisão |
|---|---|
| Clientes, Despesas, Cupons, Vales, Acertos | **paginadas** — crescem para sempre com o uso |
| Serviços, Produtos, Profissionais, Planos, Equipe | **não** — são catálogo/configuração, dezenas de linhas no teto |
| Comandas, Caixa, Vendas do dia | **não** — já são recortadas por dia |

Paginar catálogo seria ruído: dois botões mortos embaixo de uma lista de 7 serviços.

**Decisões**

- **Busca e filtro desceram para o banco junto com a paginação, não depois.** Sem isso a busca
  de Clientes só alcançaria a página carregada — o dono digitaria um nome que existe e concluiria
  que o cliente não está cadastrado. Foi por isso que a tela de Clientes mudou mais que as outras.
- **Os contadores do topo viraram um endpoint próprio** (`/customers/summary`). Deduzir "quantos
  assinantes" de uma página de 20 daria `3` numa base com 412 — número errado é pior que número
  nenhum, e essa é a mesma regra que tirou os dados fabricados do Painel.
- **A ordenação foi para o banco.** Ordenar a página já recortada daria uma lista "ordenada" por
  pedaço, com a página 2 começando antes do fim da 1. Conferido contra a API: A→E, F→J, K→O, P→T.
- **Teto de `size` (100).** Sem ele `?size=999999` devolveria a tabela inteira e anularia a
  paginação. Pedir mais não dá erro — entrega o máximo, porque recusar só atrapalharia quem
  integra.
- **A navegação é anterior/próxima, não numerada.** Com 3 mil clientes, numerar daria 150 botões,
  e ninguém procura "a página 87" — procura um nome, e a busca resolve isso.

**Achado ao validar contra o Postgres real**

- [x] **`could not determine data type of parameter`** — a primeira versão usava um único
      `(:createdSince is null or c.createdAt >= :createdSince)` para tornar o filtro de data
      opcional. O Postgres **recusa a consulta inteira**: um `LocalDateTime` nulo num `? is null`
      não tem contexto de tipo para inferir. Os 461 testes passavam — o repositório é mockado, e
      mock nenhum reproduz o planejador do Postgres. É a mesma classe de falha do bug de tipo da
      V28, e de novo só apareceu porque a rota foi chamada contra o banco de verdade.
      > **Correção e a que foi descartada:** virou duas consultas (`search` e
      > `searchCreatedSince`). Trocar o nulo por uma data-limite "bem antiga" também resolveria o
      > erro — mas `customers.created_at` é *nullable*, e a comparação excluiria silenciosamente
      > quem tem a coluna vazia. Cliente sumindo da lista sem erro nenhum é exatamente o tipo de
      > falha que este projeto trata como pior que uma exceção.

---

### 🔍 Rodada de revisão por agentes *(2026-07-27)*

> Quatro revisores especializados passaram no código depois da leva V19→V29: `react-reviewer`,
> `silent-failure-hunter`, `security-reviewer` e `java-reviewer`. O que valia corrigir foi
> corrigido nesta rodada; o que ficou está listado como débito, com o motivo.

**Corrigido**

- [x] **Dinheiro em `double` no `AgendaService`** — três resumos financeiros do bot (dia, semana e
      detalhado) somavam com `double`. Erro de arredondamento em ponto flutuante é invisível no
      teste e aparece no fim do mês, no valor que o dono compara com a gaveta. Os três passaram a
      delegar ao `ReportService.financialSummary`, que já usa `BigDecimal`, e a formatação virou
      `Locale.forLanguageTag("pt-BR")` explícito — sem locale, `String.format` seguia o do servidor
      e podia emitir `R$ 35.00` em vez de `R$ 35,00`.
- [x] **Estoque podia ficar negativo sob concorrência (V30)** — a checagem "tem saldo?" e a baixa
      eram duas operações separadas; duas vendas simultâneas do último item passavam nas duas.
      Agora `products.version` dá **lock otimista** (a segunda transação falha e refaz a leitura) e
      um `CHECK stock_quantity >= 0` no banco fecha a porta mesmo se um caminho novo esquecer a
      validação. Defesa redundante de propósito: a aplicação erra, o banco não deixa passar.
- [x] **Fidelidade emitindo cupom que se perdia** — a emissão ficava fora da transação que zerava
      os pontos: falha na emissão zerava os pontos do cliente sem entregar a recompensa. Extraído
      para `FidelityService.registerVisit`, com o cupom criado **antes** de zerar, na mesma
      transação — ou as duas coisas acontecem, ou nenhuma.
- [x] **Varreduras de tabela cheia nos relatórios** — `ReportService` carregava **todos** os
      agendamentos do tenant para filtrar por data em memória (três pontos), e buscava os itens de
      cada comanda numa consulta por comanda (N+1). Trocado por consulta com intervalo de datas e
      `findByComandaIdIn`. Mesmo problema no `PlanPricingService`, que relia benefícios e histórico
      do mês por item do catálogo — agora carrega uma vez só.
- [x] **Erro de catálogo engolido nas Comandas** — se `/services` ou `/products` falhasse, o select
      de itens ficava vazio, indistinguível de "nada cadastrado". Agora a tela avisa que o catálogo
      não carregou, em vez de deixar o operador recadastrar o que já existia.
- [x] **Requisições duplicadas no painel** — `Cockpit`, `ActivationChecklist`, `Dashboard` e
      `Settings` pediam os mesmos dois endpoints ao montar. `lib/dedupe.getOnce` reaproveita a
      requisição em voo pelo caminho. Não é cache — sai do mapa assim que assenta, todo reload bate
      no servidor; só elimina a duplicação simultânea (que também podia mostrar números diferentes
      em dois cards por terem lido o banco em instantes distintos).
- [x] **Acessibilidade dos formulários** — 57 rótulos em 16 telas eram `<label>` solto ao lado do
      campo, sem associação: leitor de tela anunciava "caixa de texto", sem dizer qual, e clicar no
      rótulo não focava o campo. Passaram a envolver o input (associação implícita, que dispensa
      `id` e portanto não colide quando o mesmo formulário aparece duas vezes na página).
- [x] **Modal sem semântica de diálogo** — base de ~10 fluxos de formulário do painel. Ganhou
      `role="dialog"`, `aria-modal`, título ligado por `aria-labelledby`, foco movido para dentro ao
      abrir e **devolvido ao elemento anterior** ao fechar, Esc fecha e Tab fica preso dentro. Sem o
      trap, o Tab caminhava para a página atrás do overlay — que o leitor de tela continuava lendo.

**Débitos aceitos (com prazo)**

- [x] **RBAC aplicado em `/api/admin/**` (2026-07-28)** — ver *Fechamento das pontas soltas*, abaixo.
- [x] **`TenantContext` fail-closed (2026-07-28)** — idem.
- [x] **`.env.prod-example` com placeholders inúteis se copiados (2026-07-28)** — idem.
- [ ] **JWT no `localStorage`** — vulnerável a XSS. A troca por cookie `httpOnly` + CSRF vai junto
      com a autenticação do cliente final (que exige contexto separado de qualquer forma).
- [ ] **`AgendaService.java` passou de 800 linhas.** Dívida de organização, não de comportamento.
- [x] **`ConfigWizard` × `Settings > BotCard` compartilhavam ~150 linhas quase idênticas
      (2026-11-17)** — os campos viraram grupos reutilizáveis em `BotConfigFields.tsx`
      (identidade, funcionamento, serviços, modo de fala, pagamento, mensagens) e `EMPTY_BOT`/
      `fillBot` foram para `lib/botSettings.ts`. O `BotCard` saiu: o modal é o único editor.
- [x] **JWT no `localStorage` (2026-11-17)** — o login agora entrega o JWT só num cookie
      `RAZOR_SESSION` (`HttpOnly; Secure; SameSite=Strict; Path=/api`, `Max-Age` = TTL); o corpo
      não traz mais o token e o painel não guarda nada no navegador (restaura a sessão via
      `/api/admin/me`). Escritas em `/api/admin/**` e `/api/auth/logout` exigem CSRF
      double-submit (`XSRF-TOKEN` → `X-XSRF-TOKEN`); webhook, onboarding, site e login ficam
      de fora. `Authorization: Bearer` segue aceito para clientes de API (sem CSRF, e sem
      fallback para o cookie). Flag Secure via `RAZOR_COOKIE_SECURE`.
- [ ] **`AgendaService.java` passou de 800 linhas** e `ConfigWizard` × `Settings > BotCard`
      compartilham ~150 linhas quase idênticas. Dívida de organização, não de comportamento.

---

### 🔒 Fechamento das pontas soltas *(2026-07-28)*

> Rodada dedicada aos bloqueadores que separavam "funciona na minha máquina" de "pode entrar
> um segundo usuário". Nenhum deles era feature; todos eram coisa que só apareceria como
> incidente. **427 testes verdes** (eram 419).

**A landing entrou no stack local**

- [x] **`site/` virou serviço do compose** (`http://localhost:8000`) — `Dockerfile` de estágio
      único (não há build: é HTML/CSS/JS escrito à mão) servido por nginx. Sobe junto com
      `docker compose up -d --build`, sem passo extra.
- [x] **O endereço do painel saiu do código** — estava adivinhado pelo `location.hostname`
      (`localhost` → `:5173`), o que só valia para quem roda o Vite. Agora vem de
      `RAZOR_PANEL_URL`, que um script em `/docker-entrypoint.d/` transforma em `config.js` no
      boot do container. Mesmo arquivo serve dev, Docker e produção; o fallback antigo continua
      para quem abre o `index.html` direto do disco.
- [x] **Volta do painel para o site** — link "← Voltar para o site" no login, com o endereço
      injetado como **build arg** (`VITE_SITE_URL`): o Vite congela `import.meta.env` no bundle,
      então variável de runtime no compose não alcançaria. Vazio esconde o link, em vez de
      deixar um caminho quebrado.
- [x] **Cabeçalhos de segurança na landing** (CSP, frame-deny, nosniff, referrer, permissions).
      > **Achado ao validar:** a primeira versão pôs os cabeçalhos no bloco `server` e eles
      > **não saíam na resposta**. No nginx, um `location` que declara qualquer `add_header`
      > próprio descarta *todos* os herdados — e todos os locations aqui definem `Cache-Control`.
      > Corrigido com um snippet `include` em cada bloco. Conferido por resposta HTTP real, não
      > por leitura do arquivo: a config *parecia* certa.
- [x] **CNPJ falso removido do rodapé** — `00.000.000/0001-00` num site público não é placeholder
      de layout, é declaração falsa de identidade jurídica. Volta quando a empresa existir.

**Segurança**

- [x] **RBAC em `/api/admin/**`** — centralizado no `SecurityConfig`, não espalhado em
      `@PreAuthorize`: a regra de quem enxerga dinheiro precisa caber numa tela só.
      | Papel | Alcança |
      |---|---|
      | **BARBER** | agenda do dia e o próprio perfil |
      | **RECEPTION** | + comandas, caixa, clientes, bloqueios e **leitura** do catálogo |
      | **OWNER** | tudo: comissões, acerto, vales, financeiro, planos, cupons, estoque, config |
      A separação leitura/escrita no catálogo é o ponto fino: sem ela, dar a tela de comanda à
      recepção daria junto o poder de reajustar a tabela de preços. O fecho de `/api/admin/**` é
      `hasRole(OWNER)`, então **controller novo nasce restrito ao dono** — o sintoma de esquecer
      de classificar é "a recepção não consegue usar a tela nova", visível e reversível, não um
      vazamento. Verificado contra a API rodando, com os três usuários do seed.
- [x] **403 com corpo `{"error": ...}`** — sem isso o painel exibiria "Erro 403" seco,
      indistinguível de bug.
- [x] **Menu e barra de comandos filtrados por papel** (`web/src/lib/access.ts`) — **espelho** da
      tabela do backend, declaradamente não a fonte da verdade: esconder item de menu não protege
      nada (a URL é digitável), só evita que o usuário clique em algo que vai recusar. Quem não vê
      financeiro entra direto na tela que usa, não no Painel.
- [x] **`TenantContext` fail-closed** — `getCurrent()` sem tenant definido agora **lança** em vez
      de recair na barbearia padrão. O silêncio era o problema: um caminho que esquecesse de
      resolver o tenant não quebrava — passava a ler e **gravar** na barbearia padrão, e com dois
      clientes o sintoma seria dado de um aparecendo no painel do outro, sem uma linha de log.
      > **Como foi feito sem reescrever 30 arquivos de teste:** a mudança quebrou **227 dos 419
      > testes** de uma vez — prova de que o fallback era carregado por toda a suíte. O conserto
      > não foi afrouxar a regra, e sim mover a conveniência para onde ela é verdade: uma
      > `DefaultTenantExtension` registrada por ServiceLoader define o tenant padrão antes de cada
      > teste. Produção ficou fail-closed, teste continuou legível, e teste novo já nasce com
      > tenant. Sobrou **1** ajuste real — um teste que usava `clear()` significando "volta pro
      > padrão", exatamente a confusão que a mudança elimina.
- [x] **Rate limiting geral da API** — até aqui só o login tinha freio. `RateLimitFilter` roda
      **antes** da cadeia do Spring Security (mil requisições com token inválido não devem custar
      mil validações de JWT), com cotas separadas para `/api/admin/**` (240/min) e webhook
      (120/min), por IP, e `Retry-After` na resposta. Janela fixa e em memória, pelos mesmos
      critérios já documentados no `LoginRateLimiter`. Verificado com rajada real: 238 passaram,
      o resto tomou 429.
- [x] **`.env.prod-example` reescrito** — tinha credenciais de exemplo *que funcionavam*, um
      **telefone real** commitado e faltavam os segredos novos (`RAZOR_JWT_SECRET`,
      `WEBHOOK_TOKEN`, `ONBOARDING_API_KEY`). Agora todo valor é `TROCAR_...`, escolhido para a
      aplicação recusar ou desabilitar o recurso se copiado sem editar, com o comando de geração
      de segredo ao lado.

**Bot falando o nome certo**

- [x] **Os 3 últimos "Barbearia Ficticia" cravados** — sobreviveram à rodada da V29 porque estavam
      em caminhos que ela não varreu: **disparo de aviso/promoção** (`AdminCommandService`),
      **reengajamento de 15 dias** (`RazorScheduler`) e **cartão fidelidade**
      (`CustomerConversationService`). Os três mandavam o nome de outra barbearia para o cliente
      final. Agora vêm do tenant; o `DEFAULT_DISPLAY_NAME` segue como default de quem não
      configurou nome, que é uso legítimo.

---

_Última atualização: 2026-07-27 (V19→V30: meta mensal, produtos, venda de balcão, comanda, estoque,
acerto de comissões, vales, motor de benefícios de plano, cupons/fidelidade, comanda ↔ agendamento;
rodada de revisão por agentes) · 419 testes · Documento vivo._

