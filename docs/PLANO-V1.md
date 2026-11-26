# Plano da versão 1.0 — lançamento do piloto em 30/11/2026

Combinado em 25/11. A 1.0 é a primeira versão que as barbearias do piloto vão usar. Até lá: corrigir,
polir e montar o que o piloto precisa. Tudo vai para a AWS sozinho a cada envio ao GitHub (item 1).

✋ = depende do dono do Razor.

## Cronograma (5 dias)

| Dia | Entregas |
|---|---|
| 26/11 (qui) | 1. Deploy automático · 2. Preços fora da landing · 3. Páginas legais · 4. Termo de adesão |
| 27/11 (sex) | 5. Revisão de design (pente fino visual) · 6. Tela de criar conta · início do 7 (telas do dia a dia) |
| 28/11 (sáb) | 7. Telas do dia a dia mais claras e vendáveis · 8. Feedback obrigatório |
| 29/11 (dom) | 9. Console com controle total · 10. Versão, página "Sobre" e novidades |
| 30/11 (seg) | Testes completos, simulação do bot, conferência no ar · **lançamento da 1.0** |

## Itens

1. **Deploy automático.** Push no `master` → testes (CI) → se passarem: backup do banco, atualiza a
   EC2 por SSM, reconstrói, confere a saúde e **volta sozinho para a versão anterior** se falhar. GitHub
   entra na AWS por OIDC (papel só do repositório do Razor, só `master`, só a EC2 e o bucket do Razor).
   Servidor desligado = deploy pulado com aviso.
2. **Tirar os preços da landing** por enquanto: seção de planos vira "Piloto com 5 vagas — teste grátis"
   com o botão de cadastro / "Quero participar". Os preços voltam depois do piloto.
3. **Páginas legais (termos, privacidade, cancelamento):** botão "← Voltar para o início" no topo e no
   rodapé; preencher os campos `[ENTRE COLCHETES]` como **pessoa física (CPF) em fase de piloto**;
   incluir que os dados do piloto podem ser usados, de forma agregada e sem expor clientes, para
   **melhorar o sistema** (via de mão dupla: a barbearia testa de graça, o Razor aprende com o uso).
   ⚠️ Revisão de advogado antes de cobrar de qualquer pessoa.
4. **Termo de adesão ao piloto (2 páginas, Word + PDF):** o que é, duração (2 semanas), gratuidade, sem
   garantia de funcionamento contínuo, deveres da barbearia (usar, dar feedback, manter o próprio
   WhatsApp), LGPD (a barbearia é dona dos dados dos clientes; apagados se sair), uso dos dados para
   melhorar o sistema, o que acontece no fim, assinaturas. Assinatura impressa ou gov.br.
5. **Revisão de design (pente fino visual):** abrir cada tela do painel, landing, site das barbearias,
   cadastro e console no computador e no celular, nos temas claro e escuro; procurar coisas quebradas
   (sobreposição, texto cortado, botão sem ação, contraste ruim, rolagem errada) e corrigir.
6. **Tela de criar conta:** menos espremida — duas colunas no computador, uma no celular, campos maiores,
   etapas claras, prova de valor ao lado ("pronta em 10 minutos", o que vem incluso).
7. **Telas do dia a dia mais claras e vendáveis** (painel, agenda, comandas, clientes, caixa):
   - Hierarquia: o que o barbeiro precisa **agora** em destaque (próximo cliente, horários do dia, quanto
     entrou hoje); o resto recolhido. Menos texto por tela, números maiores, textos de apoio menores.
   - Contraste dos dois temas ajustado para uso o dia todo (texto principal ≥ 7:1, secundário ≥ 4.5:1),
     escuro sem preto puro cansativo, claro sem branco estourado.
   - Identidade menos genérica: a "cara" da marca (aço/navalha, azul elétrico) nos detalhes, não só no logo.
   - Toque fácil no celular (alvos ≥ 44 px), ações principais sempre no mesmo lugar.
   - Estudo rápido de tendências (SaaS para barbearias, marketing no Instagram/Reels, venda por WhatsApp)
     para conferir se o Razor fala a língua do público e o que falta (prova social, depoimentos, vídeo).
8. **Feedback obrigatório no teste grátis:** janela no painel do dono no 3º dia, no 7º e no fim do teste:
   nota 0–10 ("indicaria o Razor?"), "o que está achando", "o que mais ajudou", "o que falta/atrapalhou",
   print opcional; no fim do teste também "pagaria para continuar? quanto por mês?". Pode adiar 1 vez por
   24 h; depois bloqueia o painel até responder (confirmado em 26/11).
9. **Console com controle total:**
   - Aba **Barbearias**: detalhes, uso, WhatsApp, situação do teste; **estender teste, suspender/reativar,
     encerrar e apagar dados** (confirmação dupla), **redefinir a senha do dono**, anotação interna.
     "Entrar como a barbearia" (só leitura, registrado na trilha) se aprovado ✋.
   - Aba **Feedback**: todas as respostas, notas, quem ainda não respondeu.
   - Aba **Relatório para o Claude**: um botão gera um relatório **organizado** dos últimos N dias (erros
     agrupados, agendamentos que falharam, conversas em que o bot errou, escalonamentos e custo da IA,
     incidentes de saúde, feedbacks, ações do admin), com dados de clientes mascarados, para baixar e enviar.
   - **Trilha** registrando toda ação do admin (o que mexeu, quando, em quê).
10. **Versão, página "Sobre" e novidades:** número da versão no rodapé do painel e do console; página
    "Sobre o Razor" (o que é, versão, contato, links legais) e **"Novidades"** com o histórico por versão
    (o que foi adicionado, o que foi corrigido), escrito para o barbeiro entender. Começa na **1.0.0
    (30/11/2026)**; cada atualização seguinte ganha uma entrada (1.0.1, 1.1.0…). Fonte única:
    `CHANGELOG.md`, lido pela página.

## Estratégia do piloto (a partir da 1.0)

1. **Congelar funções novas depois da 1.0.** Área do cliente, multiunidade e metas ficam para depois. Nas
   2 semanas do piloto, só correção e ajuste a partir do que os barbeiros pedirem.
2. **Medir 3 coisas no piloto** (o console precisa mostrar isso, item 9):
   - **Eles usam todo dia?** Dias com acesso ao painel e ações por barbearia.
   - **A IA realmente marca horários sozinha?** Agendamentos feitos pela IA sem humano, e quanto renderam.
   - **Eles pagariam, e quanto?** Pergunta no feedback do fim do teste ("pagaria? quanto por mês?").
3. **Cobrar cedo.** Para quem quiser continuar depois do teste, um valor simbólico de **fundador**
   (preço travado enquanto for cliente). Pagamento é a única validação que não mente.
4. **Venda como trabalho principal.** Reels, direct e visita presencial em barbearias de Araguari. Daqui
   para frente, a venda pesa tanto quanto o código.

## Próxima fase (1.1): conta do cliente, agendamento e recorrência — refeito em 26/11

**Por quê:** o primeiro cliente pagante (barbearia com CNPJ e clube recorrente, hoje na CashBarber a ~R$ 350/mês)
troca de sistema **em janeiro de 2027**, por **R$ 250/mês tudo incluso** (o Razor absorve as taxas do gateway),
como pagante + usuário validador (feedback semanal, depoimento). O que mais o prende à CashBarber é a recorrência.

**Prazo:** construir na 2ª quinzena de dezembro (depois do piloto de 02–16/12), testar até 31/12, migrar em janeiro
com os dois sistemas em paralelo por 1–2 semanas. Fora isso, as funções do piloto continuam congeladas.

### 1. Conta do cliente dentro de cada barbearia
- A conta é **por barbearia** (o mesmo celular pode ter conta em duas, separadas).
- **Primeiro acesso:** celular → **código de 6 dígitos no WhatsApp** (pelo número da barbearia) → **cria a senha**.
  Se o celular já existe (cadastrado, importado ou atendido pelo bot), aproveita o cadastro; senão, pede o nome.
- **Depois:** celular + senha. **Esqueci a senha:** novo código no WhatsApp.
- Na conta: próximos horários (remarcar/cancelar), histórico, pacote, plano do clube, forma de pagamento.
- Segurança: código vale 5 min, limite de tentativas e de envios por número/IP, senha mínima de 8, sessão separada
  do painel, tudo no log. Sem SMS (custo).

### 2. Agendar horário melhor
- **Site:** agendar exige estar logado (ou entrar no meio do fluxo, item 1). Numa tela só no celular:
  serviço → barbeiro (ou "qualquer um") → dia → horário, com os próximos horários livres sugeridos
  ("hoje 16:30 com o Vinícius"); assinante vê o preço do plano; sinal PIX quando a barbearia exigir.
- **Painel:** marcar mais rápido (buscar cliente, sugerir horário livre, repetir o último serviço).

### 3. Recorrência do clube (cobrança automática)
**Decidido em 26/11: Asaas.** Organização completa (ativação por barbearia, como o cliente assina, Central do clube,
suporte, migração e ordem de construção) em **docs/RECORRENCIA-ASAAS.md**.
**Como o dinheiro anda:**
```
Assinante paga (cartão recorrente ou PIX Automático)
   → gateway, numa conta NO NOME/CNPJ da barbearia (o Razor configura e opera tudo)
   → transferência automática por Pix para o banco dela (ex.: Sicoob)
```
- O dono só **liga a conta de recebimento** ("Ligar minha conta de recebimento" em Configurações). A conta precisa
  ser dele, com os documentos dele (regra do Banco Central); o resto é com o Razor.
- **Nunca** o dinheiro dos assinantes passa pela conta do Razor/CPF (imposto sobre dinheiro alheio, risco de
  intermediação de pagamento sem autorização, estornos na nossa conta).

**Qual gateway (ordem de escolha):**
1. **A conta da maquininha que ele já tem**, se for **Mercado Pago (Point)** ou **PagBank** — conecta por OAuth,
   não cria nada. (Sipag/Sicoob, Stone, Ton, InfinitePay não têm API de assinatura para plataformas.)
2. Senão, **Asaas** ou **Mercado Pago** criado junto com o dono na reunião (~10 min, CNPJ + selfie), ligado por
   chave de API (Asaas) ou "Conectar" (MP). **Preferência: Asaas**, por causa da baixa manual (abaixo).
3. Depois, com CNPJ do Razor e histórico no Asaas: **subconta criada pelo Razor** (o dono só informa o banco) +
   split automático. Hoje não serve: no período de avaliação a subconta não cria assinaturas e tem teto de R$ 2.000.
- Integrar direto com banco (API do Sicoob) fica fora: não cobra cartão recorrente e é contrato/certificado por banco.

| | Asaas | Mercado Pago |
|---|---|---|
| Cartão recorrente | ✅ | ✅ com novas tentativas automáticas |
| PIX Automático | ✅ (CNPJ) | ✅ (CNPJ com +6 meses) |
| Baixa manual da mensalidade | ✅ nativa ("recebido em dinheiro"), próxima cobrança segue no cartão | ⚠️ sem baixa nativa: o Razor marca paga e trata a tentativa que vier depois (estorno automático se cobrar em dobro) |
| Tarifas publicadas | cartão ~2,99% + R$ 0,49; Pix ~R$ 1,99 | conferir tabela oficial |

**Mensalidades no Razor (fonte da verdade):** cada assinante tem uma mensalidade por mês com situação
**paga / pendente / atrasada / cancelada** e "como foi paga" (cartão, PIX Automático, Pix avulso, maquininha,
dinheiro). O gateway atualiza por webhook; o dono pode mexer à mão.

**Baixa manual (pedido de 26/11):** o cartão não passou no mês → o cliente paga por **Pix** ou **na maquininha**
no balcão → o dono abre o assinante e clica **"Dar baixa"** (forma: Pix / maquininha / dinheiro, valor, data).
- A mensalidade do mês fica **paga**, o benefício continua liberado.
- No gateway: Asaas → "recebido em dinheiro" na cobrança do mês (para as novas tentativas); MP → o Razor ignora
  a cobrança do mês e, se uma nova tentativa cobrar depois, estorna sozinho.
- **No mês seguinte o cartão é cobrado de novo, normalmente.** Se falhar de novo, o bot manda o link para trocar
  o cartão ou autorizar o PIX Automático.
- Tudo registrado (quem deu baixa, quando, como) e entra no caixa do dia com a forma certa.

**Atraso:** cartão recusado → o gateway tenta de novo → o bot avisa com o link de pagamento → depois de X dias
(configurável) a mensalidade fica **atrasada** e o benefício é suspenso até pagar; o 💎 aparece apagado na agenda.

**Painel:** recebido no mês, a receber, atrasados (com botão de cobrar pelo WhatsApp), cancelamentos, receita do
clube (o card "Recorrência do clube" já existe).

**Taxas do primeiro cliente:** o Razor lê pela API as taxas do mês e **abate na mensalidade do Razor**
(R$ 250 tudo incluso). Se o clube crescer muito, combinar um teto.

### 4. Migração da CashBarber
1. Serviços, barbeiros, comissões: cadastrados junto com o dono.
2. **Importar a planilha da CashBarber** (clientes, telefones, aniversários, quem assina qual plano), com prévia e
   correção de telefone antes de gravar.
3. Quem tinha recorrência fica marcado **"precisa reativar a assinatura"**.
4. No **primeiro acesso** dele (código no WhatsApp → cria a senha), aparece **uma única vez** o pop-up
   "Seu plano continua aqui — cadastre o cartão ou autorize o PIX recorrente". Quem não entrar recebe o link pelo bot.
5. **A cobrança antiga continua na CashBarber até o cliente reativar aqui**; cancelar lá só depois. Ninguém fica
   sem cobrar nem é cobrado em dobro (lista de "falta reativar" no painel).

### Perguntas para a barbearia
- Qual maquininha usa? Tem conta no Mercado Pago ou no Asaas?
- A CashBarber exporta clientes e assinantes em planilha? Quantos assinantes ativos e quanto entra por mês no clube?
- A DOM desconta taxa de cada mensalidade? Tem multa para sair da CashBarber?
- Precisa de nota fiscal? (o Razor ainda é pessoa física — bom motivo para abrir o CNPJ em dezembro)

## Decisões para mais adiante: nome, marca e domínio (26/11)

- **Busca no INPI (26/11):** "RAZOR" sozinha está registrada em vigor na classe 9 (Razor USA), foi
  **indeferida** na classe 42 (outra empresa, 2018) e tem duas em vigor na 35 → alto risco de recusa.
  Compostos (RAZOR BARBER, RAZORAPP, RAZOR AI, RAZOR GESTÃO) não têm processo. Detalhes em
  `documentos/guia-cnpj-e-registros.md`.
- **Domínio:** preferência por **razorapp.com.br** (fácil de falar e digitar, sem a dúvida do "ã", serve para
  qualquer nicho, padrão de SaaS) e **razorgestao.com.br** como reserva defensiva. "Gestão" entra no slogan.
  ✋ registrar os dois no registro.br; depois mover o piloto para `razorapp.com.br` / `app.razorapp.com.br`.
- **Aviso 1 — marca:** "Razor Gestão", "Razor App" e "Razor Barber" correm o mesmo risco no INPI (o termo
  descritivo não conta; a palavra principal continua "Razor"). O que mais ajuda: **marca mista** (nome + logo
  da navalha) e uma avaliação rápida com especialista em marcas antes de depositar (classes 42 e 9).
- **Aviso 2 — expansão para salões e estética:** "Razor" (navalha) soa muito "barbearia". Se a expansão for
  séria, considerar marca-mãe com linhas por nicho (Razor Barber / Razor Salão / Razor Estética) ou um nome
  neutro. Decidir antes de investir pesado em divulgação fora das barbearias.

## Decisões (26/11)

- Termos em nome de pessoa física, contato por e-mail, foro de Araguari/MG.
- Feedback: adia 1 vez por 24 h, depois bloqueia.
- "Entrar como a barbearia": só leitura, 30 min, faixa de "modo suporte", registrado na trilha.
- Termo de adesão: gov.br ou impresso com foto/PDF (`documentos/`, fora do git).

## Andamento

- ✅ 26/11 — 1. Deploy automático (OIDC + SSM, rollback) · 2. Preços fora da landing · 3. Páginas legais ·
  4. Termo de adesão (Word + PDF em `documentos/`) · legenda do Reels em `roteiros/legenda-reels.md`.
- ✅ 26/11 (noite) — plano PILOTO no sistema (sem cartão fidelidade, clube e pacotes: menu, API 403, bot e
  pontos); cadastro **só por convite** (`RAZOR_SIGNUP_INVITE`; link em `documentos/link-convite-piloto.txt`),
  landing manda para o Instagram @rogeliofraga.dev com resultado em **02/12**; 6. nova tela de cadastro;
  5. pente fino visual (100 combinações, contraste e botões no celular); 7. faixa "Hoje" no painel
  (próximo cliente, horários, quanto entrou) e "Comece por aqui" recolhido; 8. feedback obrigatório (V57);
  9. console: Barbearias (estender, suspender, plano, senha do dono, anotação), Feedback e Relatório para o
  Claude; suspensão vale no login e no bot (V58); 10. versão 1.0.0, página "Sobre" e Novidades (`web/CHANGELOG.md`).
- ✅ 26/11 (manhã) — revisão de agenda/comandas/clientes: ficha, janelas e tour por cima da tela toda (portal),
  horário de 15 min com nome, seed sem datas futuras; "entrar como a barbearia" (só leitura, 30 min, faixa amarela,
  escrita bloqueada no filtro, na Trilha); filtro de custo `projeto` ativo na AWS.

## Piloto (já ligado desde 25/11)

- ✋ Apagar a rota do `inovalayer` no túnel · ✋ entrar no console e ativar o 2FA · ✋ chave da OpenAI do
  piloto (US$ 20 com limite) · ✋ 5 barbearias pelo Reels, termo assinado, cadastro e WhatsApp de cada uma ·
  ativar a etiqueta de custo `projeto` na AWS.

## Depois do piloto

✋ advogado, ✋ CNPJ, ✋ INPI, ✋ domínio próprio · cobrança da assinatura · Parte 8 (área do cliente, metas,
multiunidade, "Reservar" no Instagram) · débitos (testes de navegador, Vite, painel em partes).
