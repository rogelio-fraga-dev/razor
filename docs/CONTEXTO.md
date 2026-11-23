# Contexto de trabalho — Razor

Arquivo para retomar o trabalho de onde parou (também numa sessão nova do Claude Code).
Plano detalhado: `docs/MVP-PILOTO.md` · Visão de produto e histórico: `docs/ROADMAP.md`.

_Última atualização: 2026-11-23_

---

## Onde estamos

- **Fase:** MVP para 3–5 barbearias piloto, **só local** (sem domínio, sem clientes, sem cobrança).
- **Ordem combinada:** Parte 4 ✅ → **Parte 3** → Parte 8 → débitos. Partes **7, 9 e 6 (vídeo)** só
  quando o usuário mandar (22/11).
- **Regra de execução:** no máximo 2 frentes por vez; cada parte termina com testes + build +
  Docker no ar + push antes de abrir a próxima.
- **master:** merge da Parte 4 (`68fc4ec`) + docs.

| Parte | Migração | Situação |
|---|---|---|
| 1 — Bot correto e ligado ao painel | V46 | ✅ master |
| 2 — Painel e tema claro | (V48 não usada) | ✅ master |
| 5 — Cadastro, assistente, páginas legais, og:image | V49 | ✅ master (19/11) |
| 4 — Automações | V47 | ✅ master (23/11) |
| **3 — Console da plataforma / monitoramento** | V50 + V51 | 🟡 **próxima** — rascunho no worktree `agent-a7c84d8b86e8eb264` |
| 8 — Onda 2 | — | ⏳ |
| 7, 9 — WhatsApp oficial, salões/estética | V52–V55 | ⏸ só quando o usuário mandar |
| 6 — Vídeos: Reels + tutoriais "Como configurar o site" e "Como configurar a IA" (landing, Ajuda e 1º acesso) | — | ⏸ por último, quando o usuário mandar |

## O que foi feito em 23/11 (Parte 4)

1. Worktree `agent-a893800d6accfaad7`: `git merge master`; teste antigo ajustado ao contrato novo da
   lista de espera; mock de `CustomerPreferencesService` faltando no teste da conversa.
2. **Faltava ligar tudo:** criado `AutomationScheduler` (expira ofertas a cada 1 min; relatório
   semanal e campanhas a cada 15 min por barbearia) e `AdminAutomationController`
   (`GET/PUT /api/admin/settings/automations`, `POST .../preview`, só dono).
3. Bugs achados: "amanhã" virava período *manhã* na lista de espera; "quero marcar pro meu
   aniversário dia 12/03" era engolido como cadastro de aniversário; JPQL com enum aninhado em
   `AutomationSendRepository` derrubava o boot.
4. `spring.flyway.out-of-order: true` — a V47 entra depois da V49. (Não existe V48.)
5. 22 testes novos → **979 verdes**. Painel: aba **Configurações → Automações**; build ok.
6. Validação: cópia do banco local num Postgres descartável, jar com a Evolution apontada para uma
   porta morta; rodada de campanha e relatório semanal exercitados (ficaram FAILED, nada saiu).
   Jar e container derrubados depois.
7. Banco local: backup `backups/pre-v47-20261123-1243.dump`; V47 aplicada; stack no ar
   (backend/web/site). **Evolution (`--profile bot`) segue parada.**

### Atenção — WhatsApp
- O relatório semanal vem **ligado por padrão** (segunda 8h, recupera no mesmo dia) e vai para os
  admins do tenant. O admin cadastrado no tenant demo é um número real de pessoa: com a Evolution
  parada, as tentativas desta semana falham e param após 3; com o bot conectado, o relatório **vai**
  para esse número na próxima segunda — desligue em Configurações → Automações se não quiser.
- Lista de espera automática também vem ligada: um cancelamento oferece a vaga a quem está na fila.
- Campanhas vêm desligadas.

## Próximo passo (Parte 3 — Console da plataforma, V50 + V51)

No worktree `.claude/worktrees/agent-a7c84d8b86e8eb264` (commit `9608fcc`, não testado):
1. `git merge master` (o worktree é anterior às Partes 4 e 5).
2. Compilar, testes, revisar saúde/incidentes/aviso ao operador/storage de imagens.
3. Validar V50 numa cópia do banco local (como na V47: `pg_dump` → `postgres:15-alpine` descartável,
   `EVOLUTION_BASE_URL=http://127.0.0.1:9`), derrubar depois.
4. Telas do console, V51, backup, Docker, merge, push.

## Pendências fora das partes

- **Legal:** preencher `[ENTRE COLCHETES]` em `site/termos.html`, `privacidade.html`,
  `cancelamento.html` e revisar com advogado antes de divulgar.
- **Domínio (no final):** trocar `og:url`, `og:image`, `twitter:image`, `canonical` em
  `site/index.html`; Play Store via TWA; subdomínio por barbearia; HTTPS e deploy.
- **Decisões:** cobrança da assinatura (o fim do teste grátis hoje não bloqueia nada),
  pagamentos para barbearias, NFS-e, empresa/marca (INPI).
- **Débitos:** `npm audit` (esbuild/vite → vite 6+); ITs com Testcontainers não rodam nesta
  máquina (Docker 29); bundle do painel > 500 kB (code-split).

## Ambiente local

- Painel :3000 (proxy /api) · backend :8081 · site :8000 · Evolution :8080 (`--profile bot`)
  · Postgres :5433 (`barberbot_db` / `barberbot_user`).
- Logins demo: `dono@` / `recepcao@` / `barbeiro@navalhademo.local`, senha `demo12345`.
- Subir: `docker compose up -d --build backend web site` e `docker compose --profile bot up -d`.
- Antes de migração no banco local: `pg_dump -Fc` para `backups/`.
- Testes de backend: `./mvnw test "-DargLine=-Xmx768m -XX:+UseSerialGC"` (memória da máquina é curta).
- Worktree novo precisa de `npm ci` em `web/` antes do build do painel.
- Worktrees de agente antigos se acumulam em `.claude/worktrees/` (≈26); os já mesclados podem
  ser removidos com `git worktree remove` quando quiser liberar disco.
