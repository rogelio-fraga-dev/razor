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
| 3a — Monitoramento, alertas, storage de fotos | V50 | ✅ master (23/11) |
| **3b — Console da plataforma (PLATFORM_ADMIN)** | V51 | 🟡 **próxima** — não começou |
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

## O que foi feito em 23/11 (Parte 3a — monitoramento, V50)

1. Worktree `agent-a7c84d8b86e8eb264`: merge do master (3 conflitos, todos "os dois lados
   acrescentaram": sinais de saúde + escalonamento da IA + presença "digitando").
2. Corrigido: JPQL com enum aninhado no `SystemIncidentRepository` e construtor ambíguo do
   `OperatorNotifier` (os dois derrubavam o boot — achados subindo o jar); horários dos incidentes
   no fuso de São Paulo.
3. Painel: faixa de alerta (dono e recepção; barbeiro não) e card **Saúde do sistema** na aba Status.
4. 12 testes novos → **1001 verdes**. V50 validada numa cópia do banco local (backup
   `backups/pre-v50-20261123-1331.dump`) com a Evolution "morta": abriu `EVOLUTION_UNAVAILABLE`
   como incidente de plataforma; permissões conferidas. V50 aplicada no banco local; stack no ar.
5. Por volta de 13:10 o Docker parou todos os containers (inclusive de outro projeto) por fora —
   religado só o do Razor.
6. Como a Evolution está parada, o painel local mostra a faixa "serviço de WhatsApp fora do ar" —
   é o monitor funcionando; some quando o `--profile bot` subir.

## Próximo passo (Parte 3b — console da plataforma, V51)

Acesso único do dono da plataforma (papel `PLATFORM_ADMIN`, fora dos tenants, com 2FA):
1. V51: usuário de plataforma + 2FA (TOTP), uso de IA por barbearia (tokens/custo), chave da
   OpenAI cifrada, log de acesso a conversas (LGPD), receitas manuais ("Caixa do Razor").
2. Telas: visão geral, saúde de todas as barbearias (reusa os incidentes da V50), chave/modelos
   da IA, conversas com filtros, gráficos, caixa.
3. Mesmo roteiro: testes, cópia do banco, Docker, merge, push.

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
