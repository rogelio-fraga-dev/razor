# Contexto de trabalho — Razor

Arquivo para retomar o trabalho de onde parou (também numa sessão nova do Claude Code).
Plano detalhado: `docs/MVP-PILOTO.md` · Visão de produto e histórico: `docs/ROADMAP.md`.

_Última atualização: 2026-11-25 — Parte 6 (upgrade visual + vídeos) em andamento no branch `parte-6-visual`_

---

## FOCO ATUAL (25/11): Parte 6 — upgrade visual e vídeos ✅ (vídeos entregues)

O usuário pausou o piloto e pediu: **upgrade visual → vídeo do professor → Reels**. Tudo entregue em 25/11.
Branch/worktree `parte-6-visual` (`.claude/worktrees/parte-6-visual`), no GitHub e **no master** (merge `0b5eec8`, 25/11).

- **Upgrade visual (pronto):** landing com a cena fixa "Um dia na barbearia", título linha a linha,
  luz nos cartões e placar na calculadora (`site/motion.css`); modelos de site com a frase que acende
  e a foto que revela; painel com transição de tela, KPIs contando, gráfico que se desenha e carimbo
  PAGO. Regras de tela em `docs/DESIGN.md`. Referências em `c:/projetos/curso-sistema/DESIGN`.
- **Vídeos prontos** em `C:/projetos/razor/videos/` (fora do git):
  - `razor-professor.mp4` — 4min27, voz do dono, **sem música** (a pedido).
  - `razor-reels-so-voz.mp4` — 35 s vertical, voz do dono, sem música (ele põe a trilha dele).
  - `razor-reels.mp4` — mesma coisa com o blues de fundo (só para comparar).
- **Como os vídeos são feitos** (pasta `video/`):
  - Telas do professor: Playwright (`professor/record.mjs`, `tools/rec-lib.mjs`), desktop 1600x900.
  - Telas do Reels: `reels/record.mjs` + `tools/rec-sharp.mjs` (screencast do Chrome em 3x; o
    recordVideo do Playwright grava em px CSS e fica borrado no celular).
  - Voz: o dono gravou; `tools/prep-voice.py` corta e normaliza para `public/professor/voice/`.
    Falas do Reels em `public/reels/voz1..5.wav`. A voz de IA (Piper, `tools/tts.py`) ficou só
    como ritmo das gravações (`public/professor/durations.json`).
  - Remotion edita (`src/Professor.tsx`, `src/Reels.tsx`); `npm run render:professor` /
    `render:reels` (concorrência 1: com 2 o Windows ficou sem memória). Música desligada no código
    (`MUSIC = false`); trilha em `public/music/` (Jimmy Blythe, 1924, domínio público).
  - `public/`, `out/` e `recordings/` ficam fora do git. Áudios e gravação de tela originais do dono
    ficam em `C:/projetos/razor/roteiros/` (fora do git; nomes de arquivo com dado pessoal).
  - ffmpeg completo: `imageio-ffmpeg` (pip); o do Remotion não tem setpts/drawtext/tile.
- **Dados demo:** `scripts/seed-demo.sql` + `scripts/seed-demo-extra.sql`; recarregados no fim do dia
  (backups em `backups/antes-*-20261125-*.dump`).
- **Bot corrigido (25/11):** o gpt-4o-mini às vezes escrevia "Agendamento Confirmado / Como deseja
  pagar?" sem a tag `[NOVO_AGENDAMENTO]` (nada gravado). Agora escala para o modelo forte e, se ainda
  faltar a tag, pede o SIM de novo (`OpenAIService.claimsBookingWithoutTag`). Testado ao vivo: agendou.
- **CUIDADO — Evolution:** subir o `--profile bot` reconecta sozinho a sessão do número real
  (instância `DemoNavalhaWpp`) e o bot responde qualquer pessoa. Deixar PARADO; só ligar quando o
  usuário pedir e desligar quando ele terminar.
- **Próximo:** ✋ o usuário publica o Reels (com a música dele) e junta os interessados; depois
  retomar a ordem (piloto na AWS só quando ele mandar montar).

## Onde estamos

- **Fase:** MVP. Até aqui só local. **Decidido em 23/11:** piloto de **1 semana** com 2–3 barbearias em
  `razor.<domínio pessoal>` na AWS (conta do curso, plano gratuito), o mais barato possível. A estrutura é
  montada e fica **DESLIGADA até o usuário dizer "pode abrir o piloto"**. Nada é criado na AWS antes de ele
  mandar montar. Jurídico, CNPJ, marca e domínio próprio só depois do piloto.
- **Ordem combinada:** Partes 4, 3 e 3c ✅ → **piloto na AWS** (montar só quando o usuário mandar) → pré-lançamento → Parte 8 → débitos. Partes **7, 9 e 6 (vídeo)** só
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
| 3b — Console da plataforma (`/plataforma`, 2FA) | V51 | ✅ master (23/11) |
| 3c — 2FA com QR, carência, recuperação, sessões | V56 | ✅ master (23/11) |
| **8 — Onda 2** (área do cliente, metas, multiunidade) | V52–V54 | 🟡 **próxima** |
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

## O que foi feito em 23/11 (Parte 3b — console da plataforma, V51)

1. Worktree `.claude/worktrees/parte-3b` (branch `parte-3b`). Backend em `com.razor.api.platform`:
   `PlatformAuthService` (login em 2 passos + TOTP), `SecretCipher` (AES-GCM), `AiRuntime` (chave e
   modelos trocáveis sem reiniciar), `AiUsageRecorder` (tokens/custo em lote), `ConversationEventRecorder`
   (IA fora / agendamento não salvo), `PlatformConsoleService` (visão geral, saúde, conversas,
   métricas, caixa), `PlatformAuditLog` (trilha LGPD). Web: `web/src/platform/*`, rota `/plataforma`.
2. Achados só subindo o jar: ciclo de beans (filtro da plataforma ↔ `PasswordEncoder`) e SQL de
   text block concatenado sem espaço (`?group by`). Corrigidos.
3. 14 testes novos (TOTP com os vetores da RFC 6238, cifra, separação dos tokens, fluxo de login,
   custo, máscara, heurística) → **1015 verdes**.
4. Validação numa cópia do banco local (backup `backups/pre-v51-20261123-1502.dump`): admin criado no
   boot, 2FA de ponta a ponta (senha/código errados, reuso de código e desafio como sessão recusados),
   filtros de conversa, CSRF, métricas (tempo de resposta), caixa, chave da IA cifrada, trilha e
   isolamento entre sessão do console e do painel. Jar e container derrubados depois.

### Como entrar no console (local)
- Variáveis no `.env` (não vai para o git): `RAZOR_SECRETS_KEY` (`openssl rand -base64 32`),
  `RAZOR_PLATFORM_ADMIN_EMAIL`, `RAZOR_PLATFORM_ADMIN_PASSWORD` (12+). Sem a chave o console fica desligado.
- Abrir `http://localhost:3000/plataforma`, entrar com e-mail/senha e, no primeiro acesso, cadastrar a
  chave mostrada no app autenticador (Google Authenticator, Authy…). Perdeu o celular: no banco,
  `update platform_users set totp_enabled=false` e entrar de novo.
- **Não troque o `RAZOR_SECRETS_KEY` depois de usar:** o que foi cifrado com a chave antiga fica
  ilegível (a IA volta para a chave do ambiente e o 2FA precisa ser refeito).

## O que foi feito em 23/11 (Parte 3c — acesso ao console, V56)

- Worktree `.claude/worktrees/parte-3c`. O erro no Authy era a chave mudando a cada tentativa antes de
  confirmar — agora a chave em configuração é estável e separada da ativa.
- QR code gerado no servidor (dependência `com.google.zxing:core`), carência de 7 dias com aviso,
  bloqueio de ações sensíveis sem 2FA, configuração forçada depois do prazo, códigos de recuperação,
  sessões no banco (`platform_sessions`) com expiração por inatividade (30 min) e aviso no painel,
  "lembrar este aparelho" (30 dias), aba Segurança, sair de todos. Trilha com os eventos novos.
- Validado numa cópia do banco local, todos os cenários pela API; 1019 testes.
- **Para entrar agora:** `http://localhost:3000/plataforma`, e-mail `admin@razor.local` e a senha que
  está em `RAZOR_PLATFORM_ADMIN_PASSWORD` no `.env`. Entra só com a senha; a faixa amarela leva à aba
  Segurança para ler o QR.

## Próximo passo: piloto na AWS (só quando o usuário mandar montar)

### Depois: piloto na AWS

Plano completo em `docs/MVP-PILOTO.md` (seção "Piloto na AWS"): 1 EC2 t3.small só do Razor, Cloudflare Tunnel
para `razor.<domínio>` (HTTPS, sem IP fixo e sem porta aberta), SSM, backup no S3, orçamento próprio.
Desligada custa ~US$ 1,30/mês (só o disco); ligada uma semana ~US$ 4–5 + OpenAI. **Nunca tocar no servidor
do curso**, que roda na mesma conta (o usuário de CLI tem acesso de administrador).

### Parte 8 — Onda 2 (depois do pré-lançamento)

Área do cliente no site (V52: código pelo WhatsApp, ver/remarcar/cancelar, pacote, clube), metas e
ranking com comissão variável (V53), multiunidade (V54), botão "Reservar" no Instagram.

## Lista do que falta (resumo — detalhes em `docs/MVP-PILOTO.md`)

✋ = depende do usuário.

1. **Montar o piloto na AWS — só com "pode montar":** revisar produção (memória, swap, logs, `setup-ec2.sh`);
   criar `razor-*` com tag `projeto=razor` (security group fechado, papel SSM, bucket de backup, orçamento
   US$ 5/10, EC2 t3.small 16 GB sem IP fixo); ✋ token da Cloudflare (ou túnel criado no painel); Docker +
   Cloudflare Tunnel em `razor.<domínio>`; segredos de produção; ✋ chave da OpenAI do piloto com limite;
   subir e testar tudo; backup + restauração; **desligar**.
2. **Abrir o piloto — só com "pode abrir o piloto":** ✋ 2–3 barbearias e acordo simples; ligar; cadastrar e
   conectar WhatsApp; ✋ grupo de suporte.
3. **Semana do piloto:** acompanhar o console todo dia, anotar pedidos, corrigir o urgente.
4. **Encerramento:** backup final, relatório em `docs/`, ✋ desligar/manter/apagar.
5. **Pré-lançamento:** ✋ jurídico/LGPD com advogado, ✋ CNPJ (contador), ✋ marca e registro de software no
   INPI, ✋ domínio `.com.br` + e-mail, cobrança da assinatura, ✋ WhatsApp oficial, ✋ o que fazer quando o
   plano gratuito da AWS acabar (05/2027, vale também para o curso).
6. **Parte 8:** área do cliente (V52), metas/ranking (V53), multiunidade (V54), "Reservar" no Instagram.
7. **Débitos:** Playwright E2E, Vite 6+ (`npm audit`), code-split do painel, ITs com Testcontainers nesta máquina.
8. **Só quando o usuário mandar:** Parte 7 (WhatsApp oficial), Parte 9 (salões/estética), resto da Parte 6
   (tutoriais "Como configurar o site" e "Como configurar a IA"; o Reels e o vídeo do professor já saíram).

**Pendente do usuário agora:** ✋ publicar o Reels; ✋ testar o 2FA novo em `http://localhost:3000/plataforma`
(`admin@razor.local`, senha em `RAZOR_PLATFORM_ADMIN_PASSWORD` no `.env`).

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
