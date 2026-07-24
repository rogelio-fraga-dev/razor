# 🎛️ UX Blueprint — Razor (área do dono + super admin)

> Análise de UX/UI sênior (2026-07-23): mapa de fricção do painel atual, design system de
> estados, arquitetura proposta tela a tela, o **fluxo completo de compra→site no ar**, o
> tutorial de primeiro acesso e o console do super admin (com logs e backup).
> **Este documento é a especificação para as próximas rodadas de código.**

Princípio-mestre (pedido do dono):
> O barbeiro precisa **sentir que tem o controle total do negócio na internet** (painel) e
> **as funções boas na mão** (WhatsApp admin no celular). Web = controle; WhatsApp = ação rápida.

Relacionado: [BRANDING](./BRANDING.md) (tokens/estética) · [PLATFORM](./PLATFORM.md) · [ROADMAP](./ROADMAP.md)

---

## 1. Mapa de fricção (estado atual)

| # | Fricção | Onde | Custo para o usuário |
|---|---|---|---|
| F1 | **Primeiro acesso "mudo"**: login cai num dashboard quase vazio, sem próximo passo | Painel | Usuário não sabe que precisa conectar WhatsApp → acha que "não funciona" |
| F2 | Configurações é um **corredor de cards empilhados** (status, WhatsApp, perfil, bot, admins) | Configurações | Rolagem longa; difícil achar "onde mudo X" |
| F3 | **Sem feedback assíncrono padronizado**: cada card tem sua própria mensagem inline | Todas | Salvou? Não salvou? O olho precisa procurar |
| F4 | Tabelas puras para tudo (clientes, agenda) | Clientes/Painel | Em celular, tabela com scroll lateral é hostil para o dia a dia |
| F5 | **Sem estados vazios desenhados**: "Nenhum cliente na base ainda." e nada mais | Todas | Momento perfeito de guiar ("importe seu CSV", "conecte o WhatsApp") desperdiçado |
| F6 | Carregamento = texto "Carregando…" | Todas | Percepção de lentidão; layout "pula" quando os dados chegam |
| F7 | Ações principais a 3+ cliques (ex.: ver comissão de ontem: menu → comissões → trocar data) | Várias | Quebra a regra dos 2 cliques |
| F8 | **Não existe fluxo de compra** (plano → conta → site) nem editor do site | — | O produto vendável da Fase 4 ainda não tem jornada desenhada |

---

## 2. Design system — estados e acessibilidade

Base: tokens do [BRANDING §5–10]. O que falta padronizar:

### 2.1. Estados de componente (obrigatórios em TODO componente novo)

| Estado | Especificação |
|---|---|
| `hover` | borda acende (`voltage` 40%) + `translateY(-1/-2px)`; NUNCA mudar layout |
| `focus-visible` | anel `--focus-ring` (3px, voltage 25%) — teclado sempre visível |
| `active` | volta a `translateY(0)`; sem bounce |
| `disabled` | `opacity .55` + `cursor: not-allowed` + remove hover |
| `loading` | botão mantém largura, label → spinner de 14px + verbo ("Salvando…"); NUNCA encolher |
| `error` | borda `--red` + mensagem de 13px abaixo do campo, no momento do `blur` (não a cada tecla) |
| `success` | confirmação via **toast** (ver §5), não texto inline permanente |

### 2.2. Contraste (WCAG 2.1 AA)

- Texto normal ≥ 4.5:1; textos grandes/labels ≥ 3:1. Auditar `--text-faint` (#62697A sobre
  #0A0A0C ≈ 4.6:1 ✓, mas **não usar** faint para texto essencial, só metadados).
- `signal`/`amber` nunca como única codificação: sempre par **cor + ícone/texto**
  (ex.: badge "● Conectado", não só bolinha).
- Alvos de toque ≥ 44×44px no mobile (rail de ícones já cumpre; revisar botões de tabela).

### 2.3. Progressive disclosure (regra geral)

- Formulários mostram **só o essencial**; o avançado vive atrás de "Mostrar opções avançadas".
- Densidade em camadas: visão-resumo (cards) → detalhe (drawer lateral) → edição (modal/página).
- Nada de tela com 2 formulários longos empilhados (caso atual de Configurações → vira abas, §4.7).

---

## 3. A jornada completa de COMPRA → site no ar (Fase 4, desenhada já)

Fluxo self-service ponta a ponta ("wizard de onboarding"), 6 passos, barra de progresso
com o gradiente-assinatura, cada passo salvável e retomável:

```
Landing razor.com.br
  └─ [Escolher plano] → Checkout
       1. CONTA        e-mail + senha + nome da barbearia (3 campos. Só isso.)
       2. PAGAMENTO    cartão/PIX via gateway (beta: cupom "beta30" pula esta etapa)
       3. WHATSAPP     QR code na tela (reusa o card já construído) — pode "pular por agora"
       4. SERVIÇOS     3 serviços padrão pré-preenchidos; edita preço/duração inline
       5. SEU SITE     escolhe 1 dos 3 templates (preview ao vivo) → sobe logo + 3 fotos
                       → escolhe cor de destaque → slug conferido (suabarbearia.razor.com.br)
       6. NO AR 🎉     tela de sucesso com: link do site, QR do WhatsApp conectado,
                       botão "Entrar no painel" e o tutorial (§3.1) armado
```

Regras: cada passo com **um** objetivo; "pular" disponível em 3–5 (nada trava o acesso);
estado persistido (`onboarding_step` no tenant) para retomar de onde parou.

### 3.1. Tutorial de primeiro acesso (first-run tour)

- **Trigger:** primeiro login de um usuário OWNER com `tour_done = false`.
- **Formato:** coach-marks (spotlights) de **5 passos, max 20s**, com "Pular tour" sempre visível:
  1. *Status do bot* — "aqui você confirma que está tudo verde";
  2. *Agenda de hoje* — "o que a IA marcou aparece aqui";
  3. *Sidebar → Site* — "seu site e suas fotos";
  4. *Sidebar → Configurações* — "textos do bot e equipe";
  5. *User-chip* — "seu perfil e senha".
- **Checklist de ativação** (substitui o tour após fechado): card no Painel
  "Comece por aqui — 3/5" com itens clicáveis (conectar WhatsApp ✓, cadastrar serviços ✓,
  subir fotos do site, testar o bot com seu número, primeiro disparo). Some ao completar.
  *Este checklist é a resposta ao F1 e o maior gerador de ativação.*

### 3.1.b. Assistente de configuração do bot (substitui a lista seca)

**Problema:** Configurações hoje é um formulário longo de campos vazios. O dono olha e pensa
"o que eu escrevo aqui?" — sensação de dado jogado na tela, não de controle.

**Solução:** um **wizard** dentro de Configurações, com CTA *"Começar a configurar"*:

- Um **cartão por etapa** (7 etapas, §Fase 3 do ROADMAP), barra de progresso com o
  gradiente-assinatura, botões *Voltar* / *Salvar e continuar*, e **"Pular por agora"**.
- **Retomável**: o progresso fica no tenant; sair no meio não perde nada. Concluído, o card
  vira um resumo com *"Reconfigurar"*, e a edição campo a campo continua disponível.
- **Nunca um campo em branco esperando redação**: toda etapa abre com valor padrão pronto —
  serviços vêm do catálogo, mensagens automáticas já vêm escritas, e o tom de voz é
  **escolhido entre 5 modos** (Clássico · Parceiro · Direto · Premium · Brincalhão) em vez
  de digitado. *Personalizado* existe para quem quer controle fino.
- **Última etapa = prévia**: "veja como o bot fala" com uma conversa de exemplo renderizada
  com as configurações escolhidas. É o momento em que o dono *sente* que domina o bot.

### 3.2. Editor do site (módulo novo "Site" na sidebar)

- **Layout split**: esquerda = formulário por seções (Identidade/Fotos/Textos/Redes);
  direita = **preview ao vivo** do template (iframe do renderer real, atualiza on-change).
- Progressive disclosure: seção "Identidade" aberta; demais colapsadas.
- Upload de fotos com crop 1:1/16:9 guiado, compressão automática, máx. 10 fotos.
- Trocar template = modal com os 3 cards de preview (mantém o conteúdo, muda a pele).
- CTA fixo no rodapé do form: `Publicar alterações` (disabled sem mudanças) + "última
  publicação há X min" em mono. Publicar = toast "Site atualizado ✓ · ver site".
- Card "Template personalizado" no fim: "Quer um site exclusivo? Fale com a gente" (avulso).

---

## 4. Arquitetura por módulo (reestruturação do painel)

### 4.1. Painel (home) — de "4 KPIs + tabela" para cockpit

- Linha 1: **checklist de ativação** (se incompleto) OU alerta de saúde (bot off/IA sem chave).
- Linha 2: KPIs atuais (mono) + **sparkline de 7 dias** em cada card.
- Linha 3 esquerda: **Agenda de hoje como timeline vertical** (blocos por horário com avatar
  do barbeiro, badge 💎/pagamento; clique → drawer com detalhes/ações) — tabela some no mobile.
- Linha 3 direita: "Atividade da IA" (últimos agendamentos/conversas resolvidas — futuro).

### 4.2. Clientes — de tabela para lista rica

- Busca no topo (já existe) + filtros-chip: `Todos · 💎 Assinantes · Sumidos >30d`.
- Desktop: tabela mantém; **mobile: vira cards** (nome, telefone, plano, última visita).
- Linha/card clicável → **drawer lateral** do cliente: dados, plano (dropdown atual),
  histórico de agendamentos, ações (pausar bot para ele, apagar) — kebab ⋮ para destrutivas.
- Empty state: ilustração leve + "Importe sua base por CSV" (botão) + "ou conecte o
  WhatsApp — clientes entram sozinhos quando falam com o bot".

### 4.3. Serviços / Planos — cards em grid (não tabela)

- Card por serviço: nome, preço mono, duração como pill, switch ativo inline, kebab
  (editar/desativar). "+ Novo" = primeiro card fantasma (borda tracejada).
- Validação em tempo real no modal (preço > 0, nome obrigatório no blur).

### 4.4. Profissionais — cards com foto/iniciais

- Card: avatar, nome, comissão %, agenda do dia dele (mini), switch ativo.
- Empty: "Cadastre seu primeiro barbeiro — a IA já começa a agendar para ele."

### 4.5. Comissões — período em 1 clique

- Chips de período: `Hoje · Ontem · Semana · Mês` + date-picker só como avançado (F7 resolvido).
- Cards por barbeiro (foto, atendimentos, faturamento, comissão em destaque mono) + total.
- Futuro: coluna "produtos" quando vendas de balcão entrarem.

### 4.6. Agenda (módulo novo, pós-beta) — visão semanal por barbeiro, drag-and-drop.

### 4.7. Configurações — de corredor para ABAS

`Configurações` vira página única com tabs (some o scroll infinito — F2):
- **Status** (card semáforo atual + botão diagnóstico),
- **WhatsApp** (conexão/QR),
- **Bot & IA** (textos + liga/desliga + [Fase 5] tom de voz),
- **Equipe & acesso** (admins WhatsApp + [futuro] usuários do painel),
- **Barbearia** (perfil/slug).

### 4.8. Perfil — ok como está (nome/senha); adicionar avatar futuro.

---

## 5. Micro-interações e feedback (padrão único)

| Evento | Feedback |
|---|---|
| Salvamento OK | **Toast** canto inferior direito, 3.5s, borda `signal`: "Serviço salvo ✓" (+ undo quando aplicável) |
| Erro de API | Toast borda `red` com a mensagem do backend + botão "Tentar de novo" quando idempotente |
| Ação assíncrona longa (QR, import CSV) | Botão em loading + linha de progresso fina no topo do card |
| Toggle (bot on/off, serviço ativo) | Otimista: muda na hora, reverte com toast de erro se falhar |
| Form | Validação por campo no blur; botão primário disabled até o form ficar válido |
| Carregamento de página | **Skeletons** com shimmer (formato do conteúdo real: barras p/ KPI, blocos p/ cards) — nunca "Carregando…" |
| Delete/destrutivo | Modal de confirmação nomeando o alvo ("Apagar *Bruno Avulso*?") + verbo específico no botão |

Infra necessária no front: componente `<Toast/>` global (context), `<Skeleton/>`,
`<Drawer/>`, `<Tabs/>`, `<EmptyState/>`, `<Kebab/>` — construir uma vez, usar em tudo.

### Regra dos 2 cliques (auditoria-alvo)

| Ação | Hoje | Alvo |
|---|---|---|
| Ver comissões de ontem | 3+ | 2 (menu → chip "Ontem") |
| Ligar/desligar bot | 2 ✓ | 2 (Config → toggle) — futuro: 1 (atalho no topo do Painel) |
| Trocar plano de um cliente | 2 ✓ | 2 |
| Publicar foto nova no site | ∞ (não existe) | 3 (Site → Fotos → Publicar) — aceito por ser raro |
| Pausar bot p/ um cliente | ∞ no painel (só WhatsApp) | 2 (Clientes → drawer → Pausar) |

---

## 6. Console do Super Admin (visão do dono da plataforma)

Área `/platform` (papel `PLATFORM_OWNER`), **separada visualmente** do painel do tenant
(topbar com fundo `plasma` sutil — você sempre sabe "estou como plataforma").

| Tela | Conteúdo |
|---|---|
| **Visão geral** | MRR, tenants ativos/trial/inadimplentes, custo total de IA no mês, alertas |
| **Barbearias** | Tabela: nome, slug, plano, WhatsApp (estado da instância ao vivo), custo IA/mês, últimas 24h de atividade; ações: suspender/reativar, **entrar como** (impersonate com trilha de auditoria), reconectar WhatsApp |
| **Saúde do sistema** ⚙️ | uptime do backend, fila de webhooks, erros/min, latência da OpenAI/Evolution — cards semáforo |
| **Logs ao vivo** 📜 | *(pedido do dono)* stream dos logs da aplicação com filtro por tenant/nível/texto (v1 pragmática: `tail` do log via WebSocket/SSE + filtros; v2: Loki/Grafana). Erros `ERROR` viram linha destacada em `red` |
| **Backups** 💾 | *(pedido do dono)* status do backup diário do Postgres (`pg_dump` agendado → storage externo), último backup ok/falhou, botão "backup agora", teste de restauração mensal documentado |
| **Custos** | IA por tenant (tokens/R$), WhatsApp por tenant, margem por plano — dados p/ [PRICING §5](./PRICING.md) |
| **Beta/planos** | cupons (ex.: `beta30`), trial, mudar plano de um tenant manualmente |

---

## 7. WhatsApp Admin = controle rápido no bolso (posicionamento)

O celular NÃO é um painel menor — é o **controle remoto**:
- Menu enxuto atual (agenda, disparos, pausar/retomar) ✓ já implementado.
- **Alertas proativos** (a fortalecer): novo agendamento 🔔, cancelamento, cliente pedindo
  humano, fechamento do dia às 21h — o barbeiro *sente* o sistema trabalhando sem abrir nada.
- Toda resposta "pesada" aponta para o painel com **deep link** (ex.: `painel > Comissões`).

---

## 8. Ordem de implementação sugerida (UX)

1. **Infra de feedback**: Toast global + Skeletons + EmptyStates (desbloqueia tudo).
2. **Checklist de ativação + tour** no Painel (ataca F1 — ativação do beta).
3. **Configurações em abas** (F2) + auditoria dos 2 cliques (chips em Comissões).
4. **Clientes: drawer + cards mobile** (F4) + pausar bot pelo painel.
5. **Módulo Site (editor + 3 templates + preview)** — pré-requisito do fluxo de venda.
6. **Wizard de compra** (com cupom beta antes do gateway).
7. **Console super admin** (visão geral + barbearias + logs v1 + backups).

---

_Documento vivo · specs de UI seguem os tokens do [BRANDING](./BRANDING.md)._
