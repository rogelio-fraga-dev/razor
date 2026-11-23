# MVP para piloto — plano de trabalho

Objetivo: deixar o Razor pronto para rodar em 3–5 barbearias piloto, ainda local/sem domínio.
Pagamentos ficam de fora até a decisão de modelo (ver "Decisões pendentes").

**Ordem combinada (18/11):** Parte 5 → Parte 4 → Parte 3 → demais → vídeo por último.

**Situação em 23/11:** Partes 1, 2, 3, 4 e 5 ✅ no master e no Docker local · próximo: **3c → piloto na AWS (2–3 barbearias, 2–4 semanas) → pré-lançamento → Parte 8** (7, 9 e 6 só quando o usuário mandar). Contexto completo em `docs/CONTEXTO.md`.

**Como executamos:** em PARTES, no máximo 2 frentes por vez (para não estourar o limite de uso).
Cada parte termina com testes + build + Docker no ar + push, antes de abrir a próxima.

Migrações reservadas: V46 bot · V47 automações · V48 painel · V49 cadastro · V50 monitoramento ·
V51 console da plataforma · V52 área do cliente · V53 metas · V54 multiunidade · V55 vertical ·
V56 segurança do console (3c).

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

**3c ✅ (23/11) — acesso ao console: 2FA fácil e sessão melhor (V56)**
Causa do erro no Authy confirmada e corrigida: a chave em configuração agora é **estável** (só muda com
"gerar novo QR") e fica separada da ativa até ser confirmada.
- [x] **QR code** gerado no servidor (zxing, a chave não vai para serviço de terceiros) + chave manual com
      copiar + "abrir no app"; instruções para Google/Microsoft Authenticator, Authy, 1Password e Senhas do iPhone.
- [x] **Carência de 7 dias** (`RAZOR_PLATFORM_MFA_GRACE_DAYS`/`mfa-grace-days`): entra só com a senha e vê a faixa
      "faltam X dias"; ler conversas, trocar a chave da IA e parear WhatsApp ficam bloqueados (`MFA_REQUIRED`).
      Vencido o prazo, o login leva à configuração e as sessões abertas só alcançam a configuração
      (`MFA_SETUP_REQUIRED`).
- [x] Aba **Segurança**: ativar, desativar (troca de celular, pede código), novos códigos de recuperação,
      sessões recentes (aparelho, IP, como entrou, encerrar), aparelhos lembrados (esquecer), sair de todos.
- [x] Código com contador dos 30 s, aceita "123 456", envia sozinho ao completar; mensagem de erro clara.
- [x] **10 códigos de recuperação** (uso único, só o hash no banco), copiar/baixar .txt.
- [x] **Sessão:** expira após 30 min sem uso (aviso 2 min antes com "continuar conectado"), máximo de 8 h,
      "lembrar este aparelho por 30 dias" (cookie próprio, só o hash no banco), sair de todos.
- [x] 9 testes novos (1019 no total); validado numa cópia do banco local (backup
      `backups/pre-v56-20261123-1941.dump`) com todos os cenários pela API.

## Piloto na AWS — 1 semana, 2–3 barbearias ← PRÓXIMO (decidido em 23/11)
Objetivo: rodar **uma semana** com barbearias reais em `razor.<seu-domínio>`, gastando o mínimo, e só então
decidir o que o lançamento precisa. Ordem: **3c (acesso ao console) → montar a estrutura → (desligada)
→ "pode abrir" → 1 semana → avaliação → pré-lançamento → Parte 8**.

> 🔴 **Regra:** a estrutura é montada e testada, e fica **DESLIGADA** até o usuário dizer "pode abrir o
> piloto". Nada de ligar a máquina, conectar WhatsApp de barbearia ou divulgar o endereço antes disso.
> Nada é criado na AWS antes de o usuário mandar montar.

**1. A conta (verificada em 23/11, só leitura)**
- Conta da AWS do curso (us-east-1), **plano gratuito: US$ 180 de crédito até 05/2027**. Nela roda o servidor
  de produção do curso (t3.small) — **não pode ser tocado**: nada de parar, reiniciar, mudar security group,
  papel ou bucket dele. Tudo do Razor leva a tag `projeto=razor` e nome começando com `razor-`.
- O usuário de CLI desta máquina (`claude-deploy`) tem **AdministratorAccess** — consegue criar tudo, e
  também consegue mexer no curso. Toda ação do Razor confere o ID/tag antes; comandos de parar/apagar
  sempre com o ID da máquina do Razor, nunca por filtro amplo. (Opcional depois: trocar por uma política
  limitada à tag `projeto=razor`.)
- Orçamento: hoje há um alerta de US$ 20/mês para a conta. Criar um **orçamento só do Razor** (filtro pela
  tag) com aviso em US$ 5 e US$ 10.

**2. Arquitetura barata (o que será montado)**
- **1 EC2 t3.small** (2 GB — o stack Java + Postgres + Evolution + painel + site não cabe com folga em 1 GB),
  Ubuntu 24.04, disco gp3 de 16 GB, **sem Elastic IP** (IP público só enquanto ligada).
- **Cloudflare Tunnel** (`cloudflared` na máquina) para `razor.<seu-domínio>`: o domínio já está na
  Cloudflare, o HTTPS vem dela, o endereço não muda quando a máquina liga/desliga e **nenhuma porta fica
  aberta** (security group sem entrada; nem 22, nem 80/443). Alternativa se o túnel não servir: IP fixo +
  certbot como no curso (+ ~US$ 3,65/mês pelo IP).
- Acesso administrativo por **SSM** (papel `razor-ec2-ssm`, sem SSH, sem chave .pem).
- Tudo em Docker com `docker-compose.prod.yml` (revisar o `setup-ec2.sh`, que ainda diz "BarberBot");
  Postgres dentro da máquina; `RAZOR_STRICT_SECRETS=true` e segredos novos de produção no `.env` do
  servidor (JWT, webhook, onboarding, `RAZOR_SECRETS_KEY`, senha do banco, chave da OpenAI do piloto).
- **Backup:** `pg_dump` diário para um bucket `razor-backup-…` (S3, centavos) com restauração testada uma
  vez; e um backup final ao encerrar o piloto.
- Deploy: no piloto, por SSM (copiar o jar/imagens e subir); GitHub Actions com OIDC fica para depois.

**3. Custo esperado (estimativa, us-east-1)**
- **Desligada:** só o disco (16 GB gp3 ≈ US$ 1,30/mês) + backups no S3 (centavos). Sem IP fixo, sem máquina.
- **Ligada 1 semana:** t3.small ≈ US$ 0,021/h + IP público ≈ US$ 0,005/h → ~US$ 4–5 na semana, mais a OpenAI
  (com limite mensal na conta da OpenAI). Tudo sai do crédito de US$ 180.
- Ligar por algumas horas para montar e testar custa centavos.

**4. Montagem (só quando o usuário mandar montar)**
- [ ] Revisar `docker-compose.prod.yml`/`setup-ec2.sh` (limites de memória por container, swap de 1–2 GB
      por segurança, logs com rotação).
- [ ] Criar: security group `razor-sg` (sem entrada), papel/perfil `razor-ec2-ssm` (SSM + escrita só no
      bucket do Razor), bucket `razor-backup-…`, a EC2 com tag `projeto=razor`, o orçamento do Razor.
- [ ] Instalar Docker e `cloudflared`; criar o túnel e o registro `razor` no DNS da Cloudflare (precisa de
      um token da Cloudflare com permissão só nessa zona, ou o usuário cria pelo painel).
- [ ] Subir o sistema, conferir HTTPS, cadastro, painel, site, console `/plataforma` (com 2FA da 3c) e um
      agendamento de teste com um número de WhatsApp de teste.
- [ ] Backup + restauração testados. **Desligar a máquina** e anotar o estado ("pronto, desligado").

**5. Abertura (só com "pode abrir o piloto")**
- [ ] Ligar a máquina (o túnel volta sozinho), conferir saúde no console.
- [ ] Acordo simples com cada barbearia (o que é, 1 semana, dados apagados se sair).
- [ ] Cadastrar as 2–3 barbearias (uma com 1 barbeiro, outra com equipe), conectar o WhatsApp de cada uma,
      grupo de suporte no WhatsApp.

**6. Durante a semana**
- [ ] Olhar o console todo dia: custo de IA, mensagens, agendamentos da IA, tempo de resposta, erros, saúde;
      memória/CPU da máquina. Anotar problemas e pedidos dos donos.

**7. Encerramento**
- [ ] Backup final, relatório do piloto em `docs/` (números + o que precisa para lançar), decidir: desligar,
      manter ligado ou apagar (apagar tudo do Razor = custo zero).

## Pré-lançamento (depois do piloto)
- [ ] Jurídico/LGPD: preencher e revisar com advogado Termos, Privacidade, Cancelamento e o contrato
      com as barbearias (o Razor é operador dos dados dos clientes delas).
- [ ] Abrir CNPJ (ver com contador: desenvolvimento de software geralmente não cabe no MEI → ME no Simples).
- [ ] Registro da marca no INPI (busca de anterioridade antes de fixar o nome "Razor").
- [ ] Registro do programa de computador no INPI (opcional, barato, prova de autoria).
- [ ] Domínio (`.com.br` no registro.br, exige CPF/CNPJ), e-mail profissional, HTTPS definitivo.
- [ ] Cobrança da assinatura e o que acontece no fim do teste grátis.
- [ ] Decidir o WhatsApp oficial (Parte 7) conforme o que o piloto mostrar.

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
