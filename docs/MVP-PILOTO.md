# MVP para piloto — plano de trabalho

Objetivo: deixar o Razor pronto para rodar em 3–5 barbearias piloto, ainda local/sem domínio.
Pagamentos ficam de fora até a decisão de modelo (ver "Decisões pendentes").

**Ordem combinada (18/11):** Parte 5 → Parte 4 → Parte 3 → demais → vídeo por último.

**Situação em 23/11:** Partes 1, 2, 3, 4 e 5 ✅ no master e no Docker local · próximo: Parte 8 e débitos (7, 9 e 6 só quando o usuário mandar). Contexto completo em `docs/CONTEXTO.md`.

**Como executamos:** em PARTES, no máximo 2 frentes por vez (para não estourar o limite de uso).
Cada parte termina com testes + build + Docker no ar + push, antes de abrir a próxima.

Migrações reservadas: V46 bot · V47 automações · V48 painel · V49 cadastro · V50 monitoramento ·
V51 console da plataforma · V52 área do cliente · V53 metas · V54 multiunidade · V55 vertical.

---

## Parte 1 — Bot correto e ligado ao painel (V46) ✅
Achados no teste real de 18/11 (agendamento #380, 5 serviços, áudio):
- [x] **Valor total:** com vários serviços o bot não diz o total nem a duração somada.
- [x] **Oferecer combo:** se o cliente pede corte + barba e existe o combo, oferecer o combo
      dizendo quanto economiza.
- [x] **Painel = bot:** o card "O que a IA fez" mostrou R$ 35 para um atendimento muito mais caro,
      porque precifica por "categoria" antiga. Criar UM cálculo de preço/duração do agendamento
      (soma do catálogo, combo quando houver) usado por bot, card da IA, faturamento previsto e
      comanda.
- [x] **Pedir o serviço antes do horário** (o filtro "barbeiro que faz o serviço" vale sempre).
- [x] **Avaliação pós-atendimento:** nota 1–5; 4–5 recebe o link do Google; nota baixa só para o dono.
- [x] **Áudio mais rápido:** hoje ~7 s entre receber e entender. Trocar para `gpt-4o-mini-transcribe`
      (com `whisper-1` de reserva), evitar conversões/baixadas duplas, mostrar "gravando/digitando"
      enquanto processa; e usar `gpt-4o-mini` por padrão no chat (4o só quando ambíguo).
- [x] **Serviços na config do bot:** editor em linhas (nome | preço | duração | +) em vez de texto livre.

## Parte 2 — Painel e tema claro (V48) ✅
- [x] Recepção sem "Editar" em Serviços; barbeiro só mexe nos próprios agendamentos.
  - Recepção: Serviços/Produtos só leitura (sem Novo/Editar/Estoque, também no Ctrl+K); a lista
    da equipe passou a abrir para ela (comanda, venda e folgas precisavam) sem os percentuais de
    comissão.
  - Barbeiro: remarca, cancela e marca comparecimento só dos horários do profissional ligado ao
    login (`AgendaPermissions`, 403 no resto); arrastar só os próprios e só para a própria coluna;
    criar horário, sinal e comanda seguem com dono/recepção.
- [x] Editar nome e e-mail de usuário do painel (e-mail único, minúsculo; trocar e-mail derruba as
      sessões da pessoa; o próprio dono continua logado com cookie novo).
- [x] Editor do site: aviso de alteração não salva ao trocar de aba/navegar (`useBlocker`);
      "Como agendar" com os 3 passos editáveis; prévia em tela cheia (computador/celular, Esc).
- [x] Faturamento previsto sem faltas: resumo financeiro, "a receber", gráfico e comissão ignoram
      `NO_SHOW` (cancelado já não entra — cancelar apaga o agendamento). Se o cálculo único de
      preço da Parte 1 entrar depois, manter o filtro `ReportService.countsAsRevenue`.
- [x] react-router 7 (data router; `npm audit` sem o alerta); agenda compacta no celular e no
      notebook (‹ Hoje › + "⋯"); PWA instalável (ícones 192/512 + maskable, screenshots, atalhos)
      com botão "Instalar app" (no iPhone, o passo a passo do "Adicionar à Tela de Início").
- [x] **Tema claro refeito:** paleta única em `web/src/theme-light.css` (contraste AA medido),
      sem halo/grade no fundo, cartões com sombra curta, selos, gráfico, agenda e diálogos revisados.
- Pendente: `npm audit` ainda acusa esbuild/vite (só servidor de dev) — exige vite 6+, fora deste
  item.

### Play Store (quando houver domínio com HTTPS)
O PWA já passa nos critérios de instalação do Chrome; a Play Store aceita o mesmo app empacotado
como TWA (Trusted Web Activity):
1. Publicar o painel no domínio definitivo com HTTPS (ex.: `app.<dominio>`), com o
   `manifest.webmanifest` e o `sw.js` na raiz.
2. `npm i -g @bubblewrap/cli` e `bubblewrap init --manifest https://app.<dominio>/manifest.webmanifest`
   (pacote ex.: `br.com.<dominio>.painel`; ícones e cores saem do manifest).
3. `bubblewrap build` gera o `.aab` e a chave de assinatura — guardar a chave fora do repositório.
4. Publicar `https://app.<dominio>/.well-known/assetlinks.json` com o SHA-256 da chave
   (`bubblewrap fingerprint`; com "Assinatura de apps do Google Play", usar o SHA-256 que o Play
   Console mostra). Sem ele o app abre com a barra do navegador.
5. Play Console: conta de desenvolvedor, ficha (screenshots de `web/public/screenshots`), política
   de privacidade (Parte 5) e envio para teste interno antes da produção.

## Parte 3 — Console do desenvolvedor / plataforma (V50 + V51)
**3a ✅ (merge `651d71e`, 23/11) — monitoramento (V50):** monitor a cada 2 min (WhatsApp por barbearia,
IA, envios, fila de lembretes, motor de tarefas, Evolution fora), incidentes deduplicados no banco
(um aberto por barbearia+tipo), aviso ao dono pelo WhatsApp só quando o canal funciona (intervalo
mínimo 6h) e ao operador por webhook/log (opt-in, `RAZOR_OPERATOR_WEBHOOK_URL`). Painel: faixa de
alerta para dono e recepção + card **Saúde do sistema** em Configurações → Status. Fotos do site em
banco (padrão) ou S3 (`RAZOR_STORAGE_TYPE=s3`). Corrigido na revisão: JPQL com enum aninhado e
construtor ambíguo do `OperatorNotifier` (os dois derrubavam o boot), horários em UTC.

**3b ✅ (23/11) — console da plataforma (V51)** em `/plataforma` no painel (chunk próprio, 29 kB):
sessão separada (cookie `RAZOR_PLATFORM_SESSION` só em `/api/platform`, JWT com `typ`; o token de um
lado nunca vale no outro) e **2FA obrigatório** (TOTP RFC 6238, configurado no primeiro login, sem
reuso de código). Primeiro admin por `RAZOR_PLATFORM_ADMIN_EMAIL/PASSWORD`; segredos (chave da OpenAI,
segredo do 2FA) cifrados com AES-GCM e `RAZOR_SECRETS_KEY` — sem ela o console fica desligado (503).
Corrigido ao subir o jar: ciclo de beans (filtro ↔ PasswordEncoder) e SQL de text block sem espaço.
Acesso único do dono da plataforma (papel PLATFORM_ADMIN, fora dos tenants, com 2FA):
- [x] Visão geral: barbearias, status (teste/ativa), uso de IA por barbearia, custo estimado de IA
      (tokens registrados em lote em `ai_usage_daily`; preços por modelo configuráveis).
- [x] Saúde: incidentes de todas as barbearias, estado do WhatsApp por casa e gerar QR de pareamento.
- [x] Chave da OpenAI e modelos trocáveis pelo console sem reiniciar (cifrada; só os 4 últimos aparecem;
      teste da chave via `/v1/models`).
- [x] Conversas do bot: por barbearia, filtros (IA falhou, agendamento falhou, cliente irritado —
      heurística por palavras —, marcadas), marcar para revisão. Telefone mascarado na lista; abrir a
      conversa entra na **trilha** (`platform_access_log`, aba Trilha).
- [x] Gráficos: mensagens/dia, respostas, agendamentos da IA, tempo de resposta do bot, chamadas e erros.
- [x] "Caixa do Razor": receita lançada à mão, MRR do mês, por plano e 12 meses.
- [x] Alertas ao dono e ao operador (3a); fotos em S3 opcional (3a).
- [ ] Testes E2E com Playwright (adiado — vai para os débitos).

**3c ⏳ — acesso ao console: 2FA mais fácil e sessão melhor (pedido em 23/11, antes da Parte 8)**
Problema relatado: no Authy o código não funcionou. Causa provável: até confirmar, **cada tentativa de
login gera uma chave nova** — a cadastrada no app deixa de valer se a pessoa volta/tenta de novo. E o
campo aceita só os 6 dígitos que o app mostra (não a chave longa) — a tela não deixa isso claro.
- [ ] **QR code** na configuração do 2FA (lido pela câmera no Google Authenticator, Authy, 1Password,
      app Senhas do iOS/iPhone), com a chave manual ao lado e botão **copiar**; link "abrir no app" no celular.
- [ ] **Chave estável até confirmar:** a mesma chave/QR em todas as tentativas; botão **"gerar novo QR"**
      (invalida a anterior) quando a pessoa quiser recomeçar.
- [ ] **2FA não obrigatório no primeiro acesso:** entra só com senha e vê um aviso fixo "ative a verificação
      em duas etapas — faltam X dias" (prazo configurável, padrão 7). **Vencido o prazo, vira obrigatório**
      (o login leva direto à configuração). Recomendação: enquanto não ativar, bloquear só o que é sensível
      (ler conversas com telefone, trocar a chave da IA) — o resto do console funciona.
- [ ] Tela **"Segurança"** dentro do console: ativar/desativar 2FA (pedindo um código), ver o QR de novo
      (só reconfigurando), status e prazo.
- [ ] Mensagens de erro claras: "digite os 6 números que o app mostra agora", aviso de relógio do celular
      fora de hora, contador dos 30 s do código; aceitar código com espaço ("123 456").
- [ ] **Códigos de recuperação** (10 de uso único, mostrados uma vez) para quem perder o celular.
- [ ] **Sessão:** "sair de todos os dispositivos" (`token_version`), expiração por inatividade, aviso antes
      de expirar, "lembrar este dispositivo por 30 dias" (pula o código no mesmo navegador), lista de acessos
      recentes na aba Trilha com aparelho/IP.
- [ ] Testes: QR/URI, chave estável entre tentativas, prazo de carência vencendo, códigos de recuperação.

## Parte 4 — Automações (V47) ✅ (merge `68fc4ec`, 23/11)
- [x] Lista de espera automática ao cancelar: oferta persistida com prazo (padrão 15 min), uma vaga
      por pessoa e uma pessoa por vaga (índices únicos), SIM marca / NÃO ou prazo passa ao próximo.
- [x] Campanhas: sumido há N dias, aniversário com cupom (o bot pergunta a data uma vez), retorno do
      corte por serviço. Só em horário comercial, 1 mensagem por cliente a cada N dias, "parar" =
      opt-out permanente, rodapé de opt-out fixo, idempotência em `automation_sends`.
- [x] Relatório semanal no WhatsApp do dono (padrão segunda 8h; se o servidor estava parado, sai
      mais tarde no mesmo dia).
- [x] `AutomationScheduler` (ofertas a cada 1 min; relatório e campanhas a cada 15 min),
      `/api/admin/settings/automations` (só dono) e aba **Configurações → Automações** com prévia.
- Corrigido na revisão: "amanhã" era lido como período manhã; aniversário dentro de um pedido de
  horário era engolido antes da IA; JPQL com enum aninhado derrubava o boot; V47 exige
  `spring.flyway.out-of-order` (entra depois da V49).
- Padrões: campanhas **desligadas**; lista de espera e relatório semanal **ligados**.

## Parte 5 — Cadastro, landing e lançamento (V49) ✅ (merge `a4d1457`, 19/11)
- [x] Cadastro sozinho + teste grátis de 14 dias (só status, sem cobrança).
      `POST /api/signup` (público) cria barbearia em TRIAL + dono pelo mesmo caminho do
      provisionamento da plataforma e já abre a sessão; tela `/cadastro?plano=` no painel, ligada
      aos `[data-cta]` da landing. Freios: 10 cadastros/IP/hora (`RAZOR_RATE_LIMIT_SIGNUP`),
      honeypot, e-mail único, senha ≥ 8. Desligável com `RAZOR_SIGNUP_ENABLED=false`; dias do
      teste em `RAZOR_TRIAL_DAYS`. O fim do teste NÃO bloqueia nada (só a faixa "teste grátis até
      dd/mm") — falta decidir a cobrança.
- [x] Assistente de 10 minutos (casa → horário → serviços → equipe → QR → teste → site).
      `/comecar`, tela cheia, progresso por passo no servidor (`/api/admin/onboarding`), retoma
      pela faixa do topo e pelo checklist "Comece por aqui". Teste do bot ao vivo pela última
      mensagem recebida/respondida.
- [x] Termos, Privacidade (LGPD) e Cancelamento — RASCUNHO para advogado.
      `site/termos.html`, `privacidade.html`, `cancelamento.html` (noindex). Antes de divulgar:
      preencher os campos `[ENTRE COLCHETES]` (razão social, CNPJ, e-mails, DPO, prazos de
      retenção, regra de reembolso), revisar com advogado e tirar o aviso de rascunho.
- [x] Imagem de capa (og:image) — `site/og-image.png` (1200×630), fonte em `site/og/og-image.html`
      (`python site/og/render.py` regera). **Precisa do domínio:** WhatsApp, Instagram e Facebook
      só leem `og:image` com URL ABSOLUTA e pública (https) — caminho relativo é ignorado e a
      prévia sai sem imagem. Hoje a meta aponta para `https://razor.com.br/og-image.png`
      (provisório); ao ter o domínio, trocar `og:url`, `og:image`, `twitter:image` e o `canonical`
      no `site/index.html` e testar no depurador de compartilhamento da Meta.
- [x] Seção de teste grátis em destaque na landing.
- Pendente da Parte 5 (depende de terceiros/decisão):
  - [ ] Preencher os `[ENTRE COLCHETES]` dos termos/privacidade/cancelamento e revisar com advogado.
  - [ ] Trocar as URLs do `og:image`/`canonical` quando houver domínio e testar no depurador da Meta.
  - [ ] Decidir o que acontece ao fim do teste grátis (hoje não bloqueia nada) — depende da cobrança.

## Parte 6 — Vídeos (POR ÚLTIMO, depois de todas as atualizações, para mostrar o produto final)
- [ ] **Reels:** roteiro 30–45 s para narração + vídeo vertical dinâmico (limpo e legendado), fonte editável.
- [ ] **Tutorial "Como configurar o site"** (vídeo 1) e **"Como configurar a IA"** (vídeo 2) —
      dois vídeos separados (23/11), gravados no painel final, com legenda e fonte editável.
      Onde aparecem:
  - [ ] **Landing page:** seção "Veja como é fácil configurar" com os dois vídeos (carregar só ao
        clicar — sem pesar a página; capa estática + play).
  - [ ] **Ajuda dentro do sistema:** seção/menu "Ajuda" no painel com os dois vídeos.
  - [ ] **Primeira vez:** ao abrir pela primeira vez a configuração do site (editor do site) ou da IA
        (assistente/aba Bot & IA), mostrar o vídeo correspondente num modal, com "pular" e "não
        mostrar de novo"; o "já viu" fica salvo por usuário no servidor (como o progresso do
        assistente), não só no navegador.
  - Formato: horizontal 16:9 (tela do computador) — o Reels continua vertical. Hospedagem: arquivo
    próprio (MP4/WebM leve) ou YouTube não listado — decidir junto com o domínio/deploy.

## Parte 7 — WhatsApp oficial (Cloud API)
Antes de vender em escala (a Evolution, não oficial, pode ter o número banido):
1. Criar conta no Meta Business Manager e verificar a empresa (CNPJ).
2. Criar app em developers.facebook.com → produto WhatsApp → WABA (conta do WhatsApp Business).
3. Número: novo ou migrado (sai do app WhatsApp Business do celular). Cada barbearia vira um número
   na WABA do Razor (modelo "Tech Provider"/BSP) ou conecta a própria via Embedded Signup.
4. Webhook HTTPS público (exige domínio) + token permanente de sistema.
5. Templates aprovados para o que sai fora da janela de 24 h: lembrete, confirmação da véspera,
   avaliação, campanhas (categoria marketing, paga por mensagem).
6. No código: interface `WhatsAppGateway` com duas implementações (Evolution e Cloud API), escolhida
   por barbearia; áudio via endpoint de mídia da Cloud API.
Custo: respostas dentro de 24 h sem custo; templates utility/marketing pagos por mensagem.

## Parte 8 — Onda 2 do produto
- Área do cliente no site (V52): código pelo WhatsApp; ver/remarcar/cancelar; pacote; clube.
- Metas e ranking com comissão variável (V53). Multiunidade (V54). Botão "Reservar" no Instagram.

## Parte 9 — Outros segmentos: salões e estética (V55)
Mesma base, outra marca. O código vira multivertical: `vertical` por tenant (BARBEARIA, SALAO,
ESTETICA) mudando vocabulário (barbeiro → profissional), serviços sugeridos, templates do site,
textos do bot e landing. Um só backend/painel; domínio e marca próprios por vertical.
Ordem: terminar e validar o piloto de barbearias primeiro.

---

## Estratégia de lançamento
**Foco:** plano Equipe, barbearias de 3 a 8 barbeiros (mais dor de gestão, pagam melhor, cancelam menos).

**Piloto:** 3 a 5 barbearias (de preferência conhecidas), grátis ou com desconto em troca de opinião
e depoimento real. Medir por 30–60 dias:
- taxa de faltas antes × depois;
- agendamentos feitos sozinhos pela IA;
- tempo que o dono deixou de gastar no WhatsApp;
- se o dono abre o painel toda semana.
Números bons viram a prova social da landing; só então anúncio e Reels.

**Riscos:**
1. WhatsApp não oficial (Evolution) pode banir o número → Parte 7 antes de escalar.
2. Concorrência (Trinks, AppBarber, CashBarber, Booksy, Frizzar) → o diferencial é a IA que conversa,
   entende áudio e marca sozinha + site premium.
3. Cliente sensível a preço e barbearias pequenas fecham → foco no Equipe.
4. Suporte: barbeiro não lê manual → cadastro guiado + suporte rápido no WhatsApp.

## Empresa, marca e domínio
- **Uma empresa, várias marcas:** abrir UMA pessoa jurídica (ex.: LTDA/SLU no Simples Nacional;
  desenvolvimento/licenciamento de software não cabe no MEI) que é dona do Razor e, depois, da marca
  de salões/estética. Cada marca com domínio e identidade próprios, mesmo CNPJ e mesma plataforma.
  Separar em empresas diferentes só se entrar sócio/investidor específico de uma marca.
  Confirmar CNAE, anexo do Simples e "fator R" com um contador.
- **Marca:** registrar no INPI (classe 42 – software/SaaS, e 35) antes do lançamento público.
- **Nomes e domínios:** levantamento em andamento (disponibilidade .com.br no Registro.br e
  conflito no INPI) — ver `docs/NOMES-E-DOMINIOS.md` quando pronto.

## Decisões pendentes (não fazer ainda)
- Cobrança da assinatura do Razor (recorrência, teste com cartão, inadimplência).
- Pagamentos para as barbearias: sinal PIX automático, pagamento online, clube recorrente; gateway.
- NFS-e (provedor + prefeitura). Direct do Instagram (app Meta verificado).
- App Android na Play Store (TWA/Bubblewrap, exige domínio + assetlinks.json — passos na Parte 2); iOS via "Adicionar à Tela".
- Domínio, subdomínio por barbearia, HTTPS e deploy.
