# 📚 Estudo — Decisões de Arquitetura do Razor

> Documento de estudo. Reúne as **decisões de arquitetura** tomadas até aqui (Fase 0 e
> Fase 1), com o *porquê* e os *trade-offs* de cada uma, e termina com **perguntas** para
> você aprofundar e decidir os próximos rumos. Complementa o [ARCHITECTURE.md](./ARCHITECTURE.md)
> (o mapa) e o [ROADMAP.md](./ROADMAP.md) (as fases).

---

## 1. Decisões tomadas (ADR resumido)

Cada item: **contexto → decisão → por quê → trade-off / o que observar**.

### 1.1 Multi-tenancy: *shared schema* com `barbershop_id`
- **Contexto:** transformar um app single-tenant num SaaS onde N barbearias coexistem.
- **Decisão:** um único banco e um único schema, com uma coluna `barbershop_id` em todas as
  tabelas (discriminador de tenant). Isolamento aplicado na **camada de dados** (repos/serviços).
- **Por quê:** simplicidade operacional (um deploy, uma migração), custo baixo por tenant
  (barbearias pequenas), e evolução incremental a partir do schema existente.
- **Trade-off:** o isolamento depende de **nunca esquecer** o filtro de tenant numa query.
  Não há barreira física entre os dados de tenants. Alternativas descartadas por ora:
  *schema-per-tenant* (isola melhor, mais complexo de migrar) e *database-per-tenant*
  (máximo isolamento, caro e operacionalmente pesado).

### 1.2 `TenantContext` via `ThreadLocal` com fallback no tenant padrão
- **Decisão:** o tenant ativo vive num `ThreadLocal`; `getCurrent()` cai no
  `DEFAULT_BARBERSHOP_ID` quando nada foi setado.
- **Por quê:** permite escopar queries sem passar `barbershop_id` por todos os métodos; o
  fallback mantém o comportamento single-tenant intacto durante a transição.
- **Trade-off:** `ThreadLocal` **não** se propaga por fronteiras `@Async`/reactor — por isso
  o contexto é setado **dentro** da thread de processamento (`WebhookRouter.processWebhook`,
  `RazorScheduler`), não no controller. Um `set` sem `clear` vaza contexto entre requisições
  (por isso o `clear()` sempre no `finally`).

### 1.3 Resolução de tenant pela instância da Evolution
- **Decisão:** `TenantResolver` mapeia o nome da instância que recebeu o webhook →
  `barbershops.whatsapp_instance` → `barbershop_id`. Instância desconhecida → tenant padrão.
- **Por quê:** cada barbearia terá seu número/instância de WhatsApp; a instância é a chave
  natural de roteamento de **entrada**.
- **Trade-off:** só cobre a **entrada**. A **saída** (enviar pela instância certa) ainda usa
  uma instância única — ver perguntas §2.

### 1.4 Ponte de transição → *fail-fast* (`DEFAULT` na V4, removido na V6)
- **Decisão:** a V4 adicionou `barbershop_id NOT NULL DEFAULT <tenant padrão>` (ponte para os
  INSERTs legados não quebrarem); depois que todo INSERT do app passou a setar o tenant, a V6
  **removeu o DEFAULT**.
- **Por quê:** migração incremental sem downtime, depois endurecimento: um INSERT que esqueça
  o tenant agora **falha na hora** em vez de gravar silenciosamente na barbearia errada.
- **Trade-off:** só foi seguro remover o DEFAULT **após o CI validar** (ITs Testcontainers)
  que os inserts reais setam a coluna.

### 1.5 Tipos de domínio fortes (datas, enum) — "Opção A"
- **Decisão:** datas de agendamento como `LocalDate`/`LocalTime` (não `String`), `PaymentStatus`
  como `enum`. Para "DD/MM" a data completa guarda o **ano**, inferindo a **próxima ocorrência
  futura**.
- **Por quê:** corrige o bug de virada de ano, remove o sentinela `"hoje"` e elimina
  comparações frágeis de string.
- **Trade-off:** exigiu backfill na migração `V3` (converter VARCHAR → DATE/TIME).

### 1.6 Parsing da IA com tipo selado (`AiIntent` + `AiTagParser`)
- **Decisão:** as tags que o GPT embute (`[NOVO_AGENDAMENTO|...]`) viram um `sealed interface`
  com casos (`NovoAgendamento`, `Cancelar`, `ListaDeEspera`, `Resposta`).
- **Por quê:** elimina o acesso cru a índices de array que estourava `ArrayIndexOutOfBounds`
  em tags malformadas; torna o tratamento exaustivo e testável.

### 1.7 Quebra das God classes
- **Decisão:** `OrchestratorService` (~994 linhas) → `WebhookRouter` (triagem/roteamento) +
  `AdminCommandService` (fluxo do dono) + `CustomerConversationService` (fluxo do cliente) +
  suporte (`WhatsAppMessenger`, `EvolutionMediaService`, `ConsoleText`).
- **Por quê:** coesão, testabilidade, e reduzir o raio de impacto de mudanças.
- **Trade-off:** `AgendaService` ainda é grande — quebra parcial (satélites extraídos).

### 1.8 Estado da conversa em `ConcurrentHashMap` (na memória)
- **Decisão (herdada, mantida):** o estado da máquina de conversa (esperando pagamento,
  cancelamento, etc.) vive em mapas na memória, agora **por instância** e **chaveados por
  tenant+telefone**.
- **Por quê:** simples e suficiente para um único processo.
- **Trade-off:** perde tudo no restart e **impede rodar mais de uma instância** (não escala
  horizontalmente). Migração para Redis está na Fase 5.

### 1.9 Agenda multiprofissional
- **Decisões:**
  - `appointments.professional_id` **nullable** (legados/não-atribuídos ficam sem barbeiro).
  - **Disponibilidade por profissional** (ocupação só daquele barbeiro).
  - **Auto-atribuição**: entre os barbeiros livres no horário, o **menos ocupado no dia**
    (balanceamento); **escolha explícita** respeitada se o preferido estiver livre.
  - **Capacidade** = nº de barbeiros ativos; a IA só marca um horário como indisponível
    quando o nº de agendamentos nele atinge a capacidade (**todas as cadeiras ocupadas**).
- **Trade-off:** a escolha explícita já é suportada pela primitiva (`preferredProfessionalId`),
  mas ainda **não está ligada** no menu/IA do cliente.

### 1.10 Migrações versionadas (Flyway) + `ddl-auto=validate`
- **Decisão:** toda mudança de schema via Flyway; o Hibernate **valida** (não gera) o schema.
- **Por quê:** schema como código, reprodutível; `validate` garante que entidade e banco batem.
- **Trade-off:** exige disciplina (nada de alteração manual), e cada entidade nova precisa
  casar exatamente com a migração.

### 1.11 Testes de integração com Testcontainers
- **Decisão:** ITs sobem um Postgres real (Testcontainers) e rodam as migrações + JPA no CI.
- **Por quê:** validar migração/backfill e mapeamento contra o banco de verdade, não H2.
- **Trade-off:** dependem de Docker; no dev local (Windows) o named-pipe do Docker Desktop
  não é alcançado pelo JVM, então as ITs **pulam** localmente e rodam no CI (Linux). O SQL foi
  validado manualmente via `docker` CLI + `psql`.

---

## 2. Perguntas para estudar (e decidir)

Use como roteiro. Cada uma tem um "porquê importa" e pistas do que pesquisar.

### Multi-tenancy e isolamento
1. **Quando (e se) migrar de _shared schema_ para _schema-per-tenant_ ou RLS?**
   O que muda no custo, no isolamento e na operação? Pesquisar: **PostgreSQL Row-Level
   Security (RLS)** como rede de segurança para "nunca esquecer o filtro de tenant".
2. **Como impedir por construção uma query sem tenant?** Hoje depende de disciplina.
   Opções: um `TenantAwareRepository` base, um **Hibernate `@Filter`** global por `barbershop_id`,
   ou RLS no banco. Qual o trade-off de cada um em complexidade vs. segurança?
3. **O tenant padrão como fallback é um risco?** Se um resolver falhar, dados podem cair no
   tenant errado. Vale trocar o fallback por **erro explícito** fora da transição?

### Canal de WhatsApp
4. **Evolution API (não-oficial) vs. WhatsApp Cloud API (oficial):** custo, risco de ban,
   templates, e o impacto na margem (ver `PRICING.md`). Quando trocar?
5. **Como modelar o roteamento de _saída_ por tenant?** Desenhar a interface `WhatsAppGateway`
   (uma instância/credencial por barbearia). Onde guardar as credenciais? Como o `RazorScheduler`
   e o envio de mensagens escolhem a instância a partir do `barbershop_id`?
6. **Admins por tenant:** hoje os admins são globais (`RazorProperties.admin.phones`). Como
   modelar admins/usuários **por barbearia** (tabela `users` + papéis)? Isso conecta com a Fase 3.

### Estado e escala
7. **Estado da conversa em Redis (Fase 5):** que estrutura usar (hash por telefone? TTL?), e
   como isso destrava rodar **múltiplas instâncias** do app atrás de um load balancer?
8. **Deduplicação de mensagens (`processedMessageIds`)** hoje é na memória (perde no restart,
   assume 1 instância). Como tornar idempotente de forma distribuída (Redis? unique no banco
   por `message_id`)?
9. **Agendador multi-tenant:** hoje o `RazorScheduler` é _tenant-aware nos dados_, mas processa
   todos juntos. Vale um **job por tenant**? Como evitar que uma barbearia lenta atrase as outras?

### Domínio e regras
10. **Precificação data-driven:** o financeiro hoje usa preços **hardcoded** por match de string
    ("combo"=60, "barba"=30, senão 35). Como migrar para o catálogo `services` (preço por serviço)
    sem regredir os números? Como casar o texto livre do agendamento com um item do catálogo?
11. **Duração do serviço na agenda:** o catálogo tem `duration_minutes`, mas a disponibilidade
    usa slots fixos de 30min. Como usar a duração real para bloquear os slots corretos por barbeiro?
12. **Folgas/horário de trabalho do profissional:** a disponibilidade assume 09:00–19:00 para
    todos. Como modelar jornada e folgas por barbeiro?

### Qualidade e segurança
13. **Cobertura e testes:** vale um **teste que garanta o isolamento de tenant** de ponta a
    ponta (dois tenants, um não vê o outro)? E **mutation testing** (PIT) para medir a qualidade
    dos testes, não só a cobertura?
14. **Segredos e credenciais por tenant:** com WhatsApp/PIX por barbearia, onde e como guardar
    segredos com segurança (não em `application.yml`)? Pesquisar: cofre de segredos, criptografia
    em repouso.

---

## 3. Como estudar em cima disto

- Pegue **uma** pergunta da §2 e escreva um mini-RFC: contexto, 2–3 opções, trade-offs, decisão.
- Valide contra o código atual: procure onde a mudança tocaria (ex.: pergunta 5 → `WhatsAppService`,
  `EvolutionClient`, `RazorScheduler`).
- Rode `./mvnw verify` antes/depois — a rede de testes existe justamente para experimentar com segurança.

_Documento vivo — atualize conforme as decisões evoluem._
