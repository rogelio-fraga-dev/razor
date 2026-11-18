# MVP para piloto — plano de trabalho

Objetivo: deixar o Razor pronto para rodar em 3–5 barbearias piloto, ainda local/sem domínio.
Pagamentos ficam de fora até a decisão de modelo (ver "Decisões pendentes").

**Como executamos:** em PARTES, no máximo 2 frentes por vez (para não estourar o limite de uso).
Cada parte termina com testes + build + Docker no ar + push, antes de abrir a próxima.

Migrações reservadas: V46 bot · V47 automações · V48 painel · V49 cadastro · V50 monitoramento ·
V51 console da plataforma · V52 área do cliente · V53 metas · V54 multiunidade · V55 vertical.

---

## Parte 1 — Bot correto e ligado ao painel (V46)  ← AGORA
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

## Parte 2 — Painel e tema claro (V48)
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
Acesso único do dono da plataforma (papel PLATFORM_ADMIN, fora dos tenants, com 2FA):
- [ ] Visão geral: barbearias, status (teste/ativa), uso de IA por barbearia, custo estimado de IA.
- [ ] Saúde: instâncias do WhatsApp (conectar/reconectar/QR), falhas da IA, fila de envio, incidentes.
- [ ] Chave da OpenAI e modelos trocáveis pelo console (guardada cifrada, nunca exibida inteira).
- [ ] Conversas do bot: ler por barbearia, filtros (erro, cliente irritado, agendamento falhou),
      marcar para revisão. Acesso registrado em log (LGPD).
- [ ] Gráficos: mensagens/dia, agendamentos da IA, tempo de resposta, erros.
- [ ] "Caixa do Razor": MRR/receita por plano registrada manualmente até haver cobrança.
- [ ] Alertas ao dono da barbearia e ao operador; testes E2E com Playwright; fotos em S3 opcional.

## Parte 4 — Automações (V47)
- [ ] Lista de espera automática ao cancelar.
- [ ] Campanhas: sumido há N dias, aniversário com cupom, retorno do corte.
- [ ] Relatório semanal no WhatsApp do dono.

## Parte 5 — Cadastro, landing e lançamento (V49)
- [ ] Cadastro sozinho + teste grátis de 14 dias (só status, sem cobrança).
- [ ] Assistente de 10 minutos (casa → horário → serviços → equipe → QR → teste → site).
- [ ] Termos, Privacidade (LGPD) e Cancelamento — RASCUNHO para advogado.
- [ ] Imagem de capa (og:image).
- [x] Seção de teste grátis em destaque na landing.

## Parte 6 — Vídeo para Reels
- [ ] Roteiro 30–45 s para narração + vídeo vertical dinâmico (limpo e legendado), fonte editável.

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
