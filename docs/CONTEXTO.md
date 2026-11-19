# Contexto de trabalho — Razor

Arquivo para retomar o trabalho de onde parou (também numa sessão nova do Claude Code).
Plano detalhado: `docs/MVP-PILOTO.md` · Visão de produto e histórico: `docs/ROADMAP.md`.

_Última atualização: 2026-11-19_

---

## Onde estamos

- **Fase:** MVP para 3–5 barbearias piloto, **só local** (sem domínio, sem clientes, sem cobrança).
- **Ordem combinada (18/11):** Parte 5 → Parte 4 → Parte 3 → demais → vídeo (Parte 6) por último.
- **Regra de execução:** no máximo 2 frentes por vez; cada parte termina com testes + build +
  Docker no ar + push antes de abrir a próxima.
- **master:** `a4d1457` (merge da Parte 5), já no GitHub.

| Parte | Migração | Situação |
|---|---|---|
| 1 — Bot correto e ligado ao painel | V46 | ✅ master |
| 2 — Painel e tema claro | V48 | ✅ master |
| 5 — Cadastro, assistente, páginas legais, og:image | V49 | ✅ master (19/11) |
| **4 — Automações** | V47 | 🟡 **próxima** — rascunho no worktree `agent-a893800d6accfaad7` |
| 3 — Console da plataforma / monitoramento | V50 + V51 | 🟡 rascunho no worktree `agent-a7c84d8b86e8eb264` |
| 7, 8, 9 — WhatsApp oficial, onda 2, salões/estética | V52–V55 | ⏳ não começou |
| 6 — Vídeo para Reels | — | ⏳ por último |

## O que foi feito em 19/11

1. Sessão anterior caiu: **a máquina ficou sem memória** (≈1,3 GB livres de 11,8 GB; o fork do
   Maven morreu com "insufficient memory"). Nada se perdeu, estava tudo nos worktrees.
   - Sobras que ocupavam RAM: jar de validação na porta 8094 + container `pg-v49-signup` → removidos.
   - Rodar testes com heap limitado: `./mvnw test "-DargLine=-Xmx768m -XX:+UseSerialGC"`.
2. **Parte 5 concluída:** ajuste no `SignupController` (JSON inválido → `{error}` em português),
   957 testes unitários verdes, build do web ok, merge no master, push.
3. Stack Docker local tinha sido parada por fora (`docker compose stop`, 15:49) — religada.
   Backup antes da V49: `backups/pre-v49-20261119-1550.dump`. V49 aplicada no banco local;
   backend healthy; `/cadastro`, `termos.html`, `og-image.png` respondendo.
4. Trabalho sem commit das Partes 4 e 3 salvo em commits `wip` (não testados) nos worktrees:
   - Parte 4: `ba3cabf` (sobre `9366c9b`) — campanhas, relatório semanal, preferências do cliente.
   - Parte 3: `9608fcc` — saúde, incidentes, aviso ao operador, storage de imagens (banco/S3), V50.

## Próximo passo (Parte 4 — Automações, V47)

No worktree `.claude/worktrees/agent-a893800d6accfaad7`:
1. `git merge master` (o worktree é anterior à Parte 5).
2. Compilar e rodar os testes; revisar `WaitlistOfferService`, `AutomationCampaignService`,
   `WeeklyReportService`, `CustomerPreferencesService`.
3. Validar a V47 subindo o jar contra um `postgres:15-alpine` descartável (lembrar de derrubar
   depois — sobra de jar/container foi o que estourou a memória).
4. Telas no painel (configurar campanhas/relatório), backup do banco, Docker, merge, push.
5. Cuidado: a instância demo do WhatsApp pode estar CONECTADA — campanhas e lista de espera
   podem mandar mensagem para números reais. Testar com `notifyCustomer=false` / dados de teste.

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
- Worktrees de agente antigos se acumulam em `.claude/worktrees/` (≈26); os já mesclados podem
  ser removidos com `git worktree remove` quando quiser liberar disco.
