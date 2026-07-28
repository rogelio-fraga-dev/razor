# 📚 Estudo — Decisões de Arquitetura do Razor

> Documento de estudo. Reúne as **decisões de arquitetura** tomadas até aqui (Fases 0 e 1, e a
> camada de dinheiro das V18→V30), com o *porquê* e os *trade-offs* de cada uma, e termina com **perguntas** para
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

## 1-B. Decisões da camada de dinheiro (V18 → V30)

> A partir da V18 o sistema deixou de só *agendar* e passou a *movimentar dinheiro*. Isso muda o
> padrão de erro aceitável: um horário errado o dono corrige na hora; um centavo errado ele só
> descobre no fim do mês, e não sabe mais de onde veio.

### 1.12 Todo dinheiro em `BigDecimal`, escala 2, `HALF_UP`
- **Decisão:** nenhum valor monetário trafega em `double`/`float`. Escala 2 e
  `RoundingMode.HALF_UP` explícitos em toda divisão (comissão, rateio, ticket médio).
- **Por quê:** `0.1 + 0.2 != 0.3` em ponto flutuante. Num relatório de comissão o erro se acumula
  por atendimento e vira diferença real no bolso do barbeiro.
- **Trade-off:** mais verboso. Three resumos do `AgendaService` ainda usavam `double` e só foram
  convertidos na revisão de 2026-07-27 — sinal de que a regra precisa de teste, não de disciplina.

### 1.13 Preço **congelado** no momento da venda
- **Decisão:** `product_sales` e `comanda_items` guardam **nome e preço unitário copiados** do
  catálogo, não uma FK que resolve o preço na leitura.
- **Por quê:** reajustar um preço não pode reescrever o histórico. Se a leitura resolvesse pela
  FK, o faturamento de março mudaria sozinho quando o dono subisse o preço em abril.
- **Trade-off:** duplica dado de propósito (viola 3FN conscientemente); relatório "quanto vendi
  do produto X" precisa da FK **além** do nome congelado.

### 1.14 Nulo significa "herda", nunca "zero"
- **Decisão:** `professionals.product_commission_percent` (V23) e `services.commission_percent`
  (V27) nascem **nulos**. Nulo = usa o percentual geral do barbeiro; `0` = não paga comissão.
- **Por quê:** a migração roda no banco de barbearias reais. Se a coluna nova nascesse `0`, subir
  a versão **cortaria a comissão de todo mundo** silenciosamente até alguém reclamar.
- **Trade-off:** três estados (nulo / zero / valor) onde havia um; toda leitura precisa do
  `coalesce` correto, e o `0` precisa ser inequívoco na UI.

### 1.15 Uma venda, um movimento de caixa
- **Decisão:** fechar comanda lança **um único** movimento com o total. Os produtos dela viram
  linha em `product_sales` apontando para **esse mesmo** `cash_movement_id`.
- **Por quê:** registrar a venda (para comissão e relatório de produto) sem lançar o dinheiro
  duas vezes na conferência da gaveta. A gaveta é conferida contra dinheiro físico — dobrar ali é
  o erro mais caro possível.
- **Trade-off:** o vínculo produto→movimento é indireto; quem for ler `product_sales` como fonte
  de caixa vai contar errado. A fonte de caixa é `cash_movements`, sempre.

### 1.16 Evitar contagem dupla entre comanda e agendamento (V28)
- **Decisão:** `comandas.appointment_id` opcional, com índice único **parcial** (uma comanda por
  agendamento). O `/reports/financial` soma o serviço de comandas **sem** vínculo e **ignora** o
  das comandas **com** vínculo.
- **Por quê:** o mesmo atendimento pode existir como agendamento (marcado pelo bot) e como comanda
  (aberta no balcão). Somar os dois dobra o faturamento; somar só agendamentos perde a venda de
  quem entrou sem marcar.
- **Trade-off:** a regra vive na query — quem escrever um relatório novo precisa repeti-la. O
  índice único parcial é a única barreira estrutural.

### 1.17 Estoque opt-in, com trava dupla (V24 + V30)
- **Decisão:** `track_stock` **desligado** por padrão. Ligado, a venda confere saldo **antes** de
  cobrar, grava linha em `stock_movements` com o saldo resultante, e a partir da V30 tem
  `@Version` (lock otimista) **mais** um `CHECK stock_quantity >= 0` no banco.
- **Por quê:** opt-in porque barbearia que não conta estoque não pode ter venda recusada por um
  saldo que ela nunca alimentou. Trava dupla porque "confere e depois baixa" são duas operações:
  duas vendas simultâneas do último item passavam nas duas checagens.
- **Trade-off:** o lock otimista faz a segunda venda simultânea **falhar** e precisar de retry —
  preferível a saldo negativo, que corrompe o inventário em silêncio.

### 1.18 Acerto de comissão congela o valor pago
- **Decisão:** `commission_payments` (V25) guarda o valor **apurado no momento do pagamento**, por
  período. Vales (V26) pertencem a uma **data** e são descontados no acerto do período que a contém
  — sem estado "quitado".
- **Por quê:** correção posterior num atendimento antigo não pode reescrever um recibo já pago. E
  estado booleano de quitação dessincroniza: dois lugares para a mesma verdade.
- **Trade-off:** o saldo **pode ficar negativo** quando o vale passa da comissão. É proposital —
  o barbeiro deve à barbearia, e zerar o número esconderia a dívida.

### 1.19 Sair do caixa é opcional em acerto e vale
- **Decisão:** pagamento de comissão ou vale **em espécie** vira sangria no caixa; por
  PIX/transferência não mexe na gaveta.
- **Por quê:** a conferência do caixa é contra dinheiro físico. Lançar um PIX ali faria a gaveta
  "faltar" um valor que nunca esteve nela.

### 1.20 Preço do plano resolvido por motor de benefícios (V27)
- **Decisão:** `plan_benefits` descreve o benefício (alvo = item específico **ou** todos de um
  tipo, desconto %, cota mensal, dias da semana) e `PlanPricingService` resolve o preço final.
  Benefício específico ganha do geral.
- **Por quê:** o modelo do concorrente é "corte 100% OFF, 999/mês, só seg-qua" — não cabe num
  campo de desconto único no plano.
- **Trade-off:** cota só é verificada em **serviço** (contando atendimentos do mês). Venda de
  produto não guarda o cliente, e no modelo real produto ganha percentual, não cota.

### 1.21 Cupom é entidade, não texto enviado ao cliente
- **Decisão:** o cartão fidelidade **grava** um `Coupon` com validade, limite de uso e histórico
  de resgate; a validação acontece no fechamento da comanda, **antes** de lançar no caixa.
- **Por quê:** antes o bot mandava um código que nunca era persistido — o caixa não validava, nada
  impedia usar duas vezes e o desconto dado não aparecia em lugar nenhum.
- **Trade-off:** emissão e zeragem dos pontos precisam ser **atômicas** (`FidelityService`,
  transacional): falhar no meio dava um cliente com pontos zerados e sem recompensa.

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
10. ~~**Precificação data-driven**~~ — **resolvido em parte.** O `PricingService` já resolve preço
    e duração pelo catálogo `services` do tenant; os números fixos (combo=60, barba=30, demais=35)
    sobraram só como *fallback* para quando a categoria não existe no catálogo. **O que continua em
    aberto:** o casamento ainda é por **palavra-chave** no texto livre que a IA gerou
    (`contains("combo")`), não por FK para o item do catálogo. Serviço com nome fora do padrão cai
    em SIMPLES e é faturado a R$ 35 sem ninguém perceber. Pergunta viva: vale a IA devolver o **id**
    do serviço em vez do nome, e o que fazer com os agendamentos legados que só têm string?
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

### Dinheiro e concorrência *(novas, a partir da V18)*
15. **Como impedir por construção um `double` em valor monetário?** A revisão de 2026-07-27 achou
    três resumos financeiros somando em ponto flutuante — disciplina não bastou. Opções:
    ArchUnit proibindo `double` em pacotes de domínio, Checkstyle com regexp, ou um tipo `Money`
    próprio que torna o erro impossível de escrever. Qual custa menos para manter?
16. **Onde mais existe "confere e depois grava"?** O estoque tinha essa janela e foi fechado com
    lock otimista + CHECK (V30). O mesmo padrão aparece no fechamento de caixa e no resgate de
    cupom — vale auditar cada um e decidir entre lock otimista, `SELECT ... FOR UPDATE` ou
    constraint no banco.
17. **Retry de lock otimista:** hoje a segunda venda simultânea do último item **falha** e o
    operador refaz. Vale um retry automático (ex.: Spring `@Retryable`) ou a falha explícita é
    melhor, já que o operador precisa saber que o item acabou?
18. **Idempotência de cobrança** (pré-requisito do item 6 do roadmap, pagamento pelo cliente): que
    chave usar para o gateway não cobrar duas vezes quando a rede cai entre o débito e o nosso
    `commit`? Pesquisar: chave de idempotência, webhook com verificação de assinatura, e
    reconciliação — o que fazer quando o gateway diz "pago" e nosso banco diz "pendente".
19. **Auditoria de dinheiro:** hoje `stock_movements` tem histórico, mas alterar o preço de um
    serviço ou o percentual de comissão não deixa rastro. Vale uma tabela de auditoria genérica
    (quem mudou o quê, quando) antes de haver funcionário com acesso ao painel?

---

## 3. Como estudar em cima disto

- Pegue **uma** pergunta da §2 e escreva um mini-RFC: contexto, 2–3 opções, trade-offs, decisão.
- Valide contra o código atual: procure onde a mudança tocaria (ex.: pergunta 5 → `WhatsAppService`,
  `EvolutionClient`, `RazorScheduler`).
- Rode `./mvnw verify` antes/depois — a rede de testes existe justamente para experimentar com segurança.

_Documento vivo — atualize conforme as decisões evoluem. Última revisão: 2026-07-27 (camada de
dinheiro V18→V30 documentada em §1-B; perguntas 15–19 abertas)._
