# Parte 3b — Console da plataforma (V51) — plano de retomada

Worktree `.claude/worktrees/parte-3b` (branch `parte-3b`). Feito: `V51__platform_console.sql` (não testada).

## Desenho
- **Acesso separado:** `platform_users`, cookie `RAZOR_PLATFORM_SESSION` em `/api/platform`, JWT com
  `typ=platform` (JwtService ganha issuePlatform/parsePlatform; o parse do tenant rejeita typ).
  `JwtAuthenticationFilter.shouldNotFilter` para `/api/platform/**`; `PlatformAuthFilter` depois dele.
  SecurityConfig: `/api/platform/auth/**` permitAll, `/api/platform/**` hasRole PLATFORM_ADMIN;
  CSRF_PATHS e CsrfCookieFilter incluem `/api/platform/**`.
- **2FA obrigatório (TOTP RFC 6238, implementação própria):** login → `{step: MFA|SETUP, challenge}`
  (JWT typ=mfa, 5 min); SETUP devolve segredo base32 + otpauth URI; `POST /verify {challenge, code}`
  liga o TOTP e emite a sessão. `last_totp_step` contra replay. LoginRateLimiter com prefixo.
- **Primeiro admin:** `RAZOR_PLATFORM_ADMIN_EMAIL` / `RAZOR_PLATFORM_ADMIN_PASSWORD` no boot, se não
  houver nenhum. **Segredos:** AES-GCM com `RAZOR_SECRETS_KEY` (base64, 32 bytes); sem ela o console
  responde 503. Passar as variáveis no `docker-compose.yml` (valores só no `.env`, que não vai ao git).
- **IA trocável:** `AiRuntime` (chave/modelos do `platform_settings` ou do ambiente; recria os
  `OpenAiChatModel` quando muda). `OpenAIService` usa `aiRuntime` quando não nulo (testes passam null).
  Chave nunca exibida inteira (só last4); teste da chave via `GET /v1/models` (grátis).
- **Uso/custo:** `AiUsageRecorder` soma `tokenUsage()` em memória e faz upsert em `ai_usage_daily`
  a cada minuto; preços por modelo configuráveis (gpt-4o-mini 0,15/0,60; gpt-4o 2,50/10 USD por 1M).
- **Conversas:** `ConversationEventRecorder` grava AI_ERROR (contingência) e BOOKING_FAILED no
  `CustomerConversationService`; filtro "irritado" por palavras-chave nas mensagens do cliente;
  leitura registrada em `platform_access_log`; marcar para revisão em `conversation_flags`.
- **Endpoints:** overview, incidents (todos os tenants), shops/{id}/whatsapp (estado/QR), ai (GET/PUT/test),
  conversations (lista/detalhe/flag), metrics (msgs/dia, agendamentos BOT/dia, tempo de resposta via
  LEAD(), erros/dia), revenue (CRUD + MRR).
- **Web:** rota `/plataforma/*` com layout e login próprios; gráficos em SVG simples (sem lib nova).
- Validar como nas outras partes: testes, cópia do banco, Evolution morta, Docker, merge, push.
