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

## Próxima fase (1.1 → 1.2): agendamento, área do cliente e recorrência — pedido de 26/11

Origem: demonstração para a LH Barbearia (Araguari), que hoje paga ~R$ 350/mês na CashBarber e cujo maior
vínculo é o **clube com cobrança recorrente** (LH BARBERCLUB, gateway DOM Pagamentos).
⚠️ Conflita com "congelar funções no piloto": decidir se entra antes do piloto terminar (para converter a LH)
ou logo depois. Sugestão: 1.1 começa no fim do piloto, ou antes se a LH fechar.

1. **Agendar horário melhor** (site da barbearia e painel):
   - Fluxo mais curto no site: serviço → barbeiro (ou "qualquer um") → dia → horário, numa tela só no celular,
     com os próximos horários livres já sugeridos ("hoje 16:30 com o Vinícius").
   - Mostrar preço do plano para assinante, sinal PIX quando a barbearia exigir, confirmação clara.
   - No painel: marcar horário mais rápido (buscar cliente, sugerir horário livre, repetir o último serviço).
2. **Conta do cliente DENTRO de cada barbearia (login próprio)** (era a Parte 8, V52, sobe de prioridade):
   - Cada barbearia tem os seus clientes: a conta vale para a barbearia onde ele corta (o mesmo celular pode ter
     conta em duas barbearias, separadas).
   - **Primeiro acesso:** o cliente digita o celular → recebe um **código no WhatsApp** (enviado pelo número da
     barbearia) → digita o código → **cria uma senha**. Se o celular já está no banco (cadastrado, importado da
     CashBarber ou atendido pelo bot), aproveita o cadastro: só confirma o código e cria a senha. Se não está,
     informa o nome e cria a senha.
   - **Próximos acessos:** celular + senha. **Esqueci a senha:** novo código no WhatsApp e senha nova.
   - **Agendar exige estar logado** (ou entrar na hora, no próprio fluxo): o site já sabe quem é, se é assinante e
     qual o preço do plano; o horário fica na conta dele.
   - Na conta: próximos horários (remarcar/cancelar), histórico, pacote, plano do clube, cartão/PIX cadastrado.
   - Cuidados: código de 6 dígitos com validade de 5 min, limite de tentativas e de envios por número/IP, senha com
     mínimo de 8 caracteres, sessão própria do cliente (separada do painel), tudo registrado; sem SMS por custo.
3. **Recorrência de verdade (cobrança automática do clube)** — **cartão recorrente ou PIX Automático**:
   - O cliente escolhe na hora de assinar: cartão (cobra todo mês sozinho) ou PIX Automático (autoriza uma vez no app
     do banco, debita todo mês). O dinheiro cai na conta da barbearia; o Razor não segura dinheiro.
   - Situação de cada assinante: em dia / atrasado / cancelado; benefício suspenso se atrasar; bot avisa o atraso
     com o link de pagamento; painel com renovações, vencimentos, cancelamentos (churn) e receita do clube.
   - **Gateway — recomendação: Asaas** (comparação abaixo, 26/11).

   | | **Asaas** | **Mercado Pago** |
   |---|---|---|
   | Modelo para plataforma (vários negócios) | **Subcontas** criadas pela API: cada barbearia vira uma conta Asaas "dentro" do Razor, com cadastro e saque próprios | Cada barbearia conecta a **conta Mercado Pago dela** (OAuth) |
   | Assinatura no cartão | Sim, cobra no vencimento todo mês | Sim (preapproval), com **novas tentativas** quando o cartão recusa |
   | PIX Automático | Sim, sem custo extra (tarifa de Pix), **só para empresa (CNPJ)** | Sim, exige **CNPJ ativo há mais de 6 meses** e valor fixo |
   | Taxa do Razor por transação | **Split** automático (valor fixo ou %) | Possível no modelo marketplace, mais trabalhoso |
   | Tarifas publicadas | Cartão ~2,99% + R$ 0,49; Pix/boleto ~R$ 1,99 (promo menor no início); negociável com volume | Variam pelo prazo de recebimento; conferir a tabela oficial |
   | Vantagem | Feito para SaaS cobrar em nome dos clientes; também serve para o Razor cobrar a mensalidade das barbearias | Marca conhecida; muita barbearia já usa a maquininha/conta MP |

   Por que Asaas: subcontas + split + assinatura + PIX Automático numa API pensada para plataforma; a barbearia não
   precisa ter conta em lugar nenhum antes; e o mesmo Asaas cobra a assinatura do Razor das barbearias.
   Mercado Pago fica como opção para quem já usa e prefere receber na conta MP (fase 2).
   ⚠️ PIX Automático exige CNPJ da barbearia (e do Razor, para o split): quem é pessoa física fica só no cartão.
   - **Começo (Razor ainda sem CNPJ), ex.: LH/Luiz que tem CNPJ:** a barbearia abre a **própria conta Asaas no CNPJ
     dela** e cola a chave de API no Razor; as assinaturas são criadas na conta dela, o dinheiro cai direto lá e o
     Asaas desconta as taxas lá. O Razor cobra só a mensalidade dele, à parte. Se combinarmos "o Razor cobre as
     taxas", o Razor lê as taxas do mês pela API e **abate na mensalidade seguinte** (ou preço fechado com teto de taxa).
   - **Depois (Razor com CNPJ):** subconta Asaas por barbearia + split automático (taxa do Razor por cobrança).
   - **"A gente cuida de tudo, ele só liga a conta de recebimento" (26/11):** possível, com um limite fixo: a conta
     que recebe tem que estar no nome/CNPJ da barbearia, com os documentos dela (regra do Banco Central). O resto
     (planos, assinaturas, cobranças, avisos, painel) o Razor faz.
     - **Mercado Pago "Conectar conta" (OAuth):** o dono clica, entra na conta MP dele e autoriza; o Razor cria e
       gerencia as assinaturas na conta dele. É o mais próximo de "só ligar a conta".
     - **Asaas com a chave de API:** mesma ideia, mas a ligação é colar a chave (fazemos junto numa chamada).
     - **Subconta Asaas criada pelo Razor:** fica para depois — no período de avaliação do Asaas a subconta
       **não pode criar assinaturas** e tem teto de R$ 2.000 em cobranças, e o ideal é o Razor ter CNPJ.
     - **Nunca:** o dinheiro dos assinantes cair na conta do Razor/CPF para depois repassar (imposto sobre dinheiro
       que não é nosso, risco de atuar como intermediador de pagamento sem autorização, estornos na nossa conta).
     - ⇒ Para janeiro: implementar a conexão **por OAuth do Mercado Pago ou pela chave do Asaas**, com o botão "Ligar minha conta
       de recebimento" em Configurações; decidir o gateway quando o dono responder qual conta já tem/prefere.
   - **Primeiro cliente pagante (decisão de 26/11):** uma barbearia com CNPJ e clube recorrente, trocando o sistema
     **no começo de 2027** (janeiro). Preço simbólico de **R$ 250/mês, tudo incluso**: o Razor absorve as taxas do
     gateway (abate da mensalidade). Papel: pagante + usuário validador (feedback toda semana, depoimento).
     ⇒ Prazo: área do cliente + recorrência Asaas + importação da CashBarber prontas e testadas **até o fim de
     dezembro** (logo depois do piloto de 02–16/12). Migração com os dois sistemas em paralelo em janeiro.
4. **Migração da CashBarber (clientes e assinantes)**:
   - Importar a **planilha exportada da CashBarber** (clientes, telefones, aniversários e quem é assinante de qual
     plano) pelo console/painel, com prévia e correção de telefone antes de gravar.
   - Todo cliente que **tinha recorrência** fica marcado "precisa reativar a assinatura".
   - No **primeiro acesso** dele (código no WhatsApp → cria a senha, item 2), aparece **uma única vez** um pop-up:
     "Seu plano Barberclub continua aqui — cadastre o **cartão** ou autorize o **PIX recorrente** para manter a
     assinatura", que cria a recorrência nova no gateway. O bot também manda esse link no WhatsApp para quem não entrar.
   - Enquanto o cliente não reativa, a cobrança antiga continua na CashBarber; cancelar lá só depois de reativado aqui.
   - Perguntas para a barbearia: a CashBarber exporta em planilha? Quantos assinantes ativos? Multa de saída? Taxas da DOM?

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
