# Recorrência com Asaas: como organizar tudo (26/11)

Decisão: **Asaas** para a cobrança do clube. Cada barbearia tem a **própria conta Asaas no CNPJ dela**; o Razor
liga nela pela chave de API e opera tudo (planos, assinaturas, avisos, baixa, painel). O dinheiro cai na conta Asaas
da barbearia e vai sozinho para o banco dela (ex.: Sicoob). O Razor nunca recebe o dinheiro dos assinantes.

## O que o mercado faz (pesquisa de 26/11)
- **Muitas barbearias já usam o Asaas direto** para o clube: páginas de pagamento do Asaas com o plano, uma para
  **cartão** e outra para **Pix**, cobrança no mesmo dia do mês seguinte. Os termos se repetem: assinatura
  **pessoal e intransferível**, **sem fidelidade**, cobranças do clube **não são estornadas**. O problema delas é que
  isso fica solto: o Asaas cobra, mas a agenda, o benefício e o caixa não sabem quem pagou.
- **CashBarber:** gateway próprio integrado (DOM Pagamentos): a barbearia não vê taxa separada nem configura nada;
  o sistema sabe quem pagou. É isso que prende o cliente. O Razor precisa entregar a mesma sensação, com o Asaas.
- **Integrações com Asaas** (Pluga, Guru e outras) ligam a conta do cliente pela **chave de API** colada no sistema; o
  Asaas ainda não tem um "Conectar com Asaas" (OAuth). A chave aparece **uma vez só** quando é gerada, só o
  administrador da conta gera, e o Asaas avisa por webhook se ela for desativada ou expirar.

## 1. Ativar a cobrança de uma barbearia (uma vez, ~20 min comigo junto)
No Razor: **Configurações → Cobrança automática**, um passo a passo com a situação de cada etapa
(o console mostra a mesma situação para eu acompanhar todas as barbearias).

| Etapa | Quem | Situação no Razor |
|---|---|---|
| 1. Pré-requisitos: CNPJ (Pix Automático só para empresa; CPF fica só no cartão), e-mail do dono, documentos (RG/CNH, contrato social ou CCMEI, selfie), banco para receber | Dono, com a lista que o Razor mostra | "Falta começar" |
| 2. Criar a conta Asaas no CNPJ, pelo app ou site, em chamada comigo | Dono + eu | "Conta em análise" (o Asaas aprova em alguns dias úteis) |
| 3. No Asaas: chave Pix, **transferência automática para o banco** (ex.: Sicoob) e **desligar os avisos do Asaas para o cliente final** (quem avisa é o bot, para o cliente não receber mensagem em dobro) | Eu, com o dono na tela | — |
| 4. Gerar a **chave de API** e colar no Razor. O Razor confere a chave, guarda **criptografada**, cadastra o webhook sozinho e mostra "Conta ligada: <nome do CNPJ>" | Dono cola, Razor confere | "Ligada" |
| 5. Teste: assinatura de R$ 5 no cartão do dono, ver chegar no painel e cancelar/estornar | Eu | "Testada" → **"Ativa"** |

Cuidados:
- Se o Asaas deixar escolher as permissões da chave, criar a chave **sem permissão de transferência/saque**.
  Assim, mesmo se ela vazar, ninguém tira dinheiro da conta. Confirmar com o Asaas.
- A chave nunca aparece de novo no painel (só os 4 últimos dígitos). Trocar a chave = colar outra.
- Chave desativada ou expirada → o webhook avisa → o Razor mostra um alerta no painel e no console.

## 2. Como o cliente assina (três portas, o mesmo resultado)
1. **Pelo site da barbearia**, logado: escolhe o plano → paga.
2. **Pelo WhatsApp:** o dono (ou o bot, quando o cliente pergunta do clube) manda o link do plano.
3. **No balcão:** o dono abre o cliente no painel → "Assinar plano" → manda o link para o celular do cliente
   (ou o cliente preenche ali mesmo, no celular dele).

O pagamento é feito na **página segura do Asaas** (o Razor não toca em número de cartão):
- **Cartão:** paga o primeiro mês e o cartão fica salvo para os próximos.
- **PIX Automático:** autoriza uma vez no app do banco e o débito é mensal.
- Pago → o webhook avisa → o Razor liga o plano no cliente (💎 na agenda, benefício na comanda, bot sabe).

**Termos padrão do clube** (aparecem antes de pagar, a barbearia pode editar): pessoal e intransferível; sem
fidelidade, cancela quando quiser e o benefício vale até o fim do mês pago; cobrança no mesmo dia todo mês;
atrasou, o benefício fica suspenso até pagar; mensalidade já usada não é estornada.

## 3. O dia a dia: "Central do clube" (tela no painel)
Uma tela com filas, para ninguém se perder:
- **Aguardando 1º pagamento:** assinou e não pagou. Botão "reenviar link".
- **Em dia:** tudo certo.
- **Atrasados:** cartão recusado ou Pix não pago. Botões **"Cobrar no WhatsApp"** e **"Dar baixa"** (Pix avulso,
  maquininha, dinheiro). A baixa marca o mês como pago no Asaas ("recebido em dinheiro"), e no mês seguinte o
  cartão é cobrado normalmente.
- **Falta reativar (migração):** quem era assinante no sistema antigo e ainda não cadastrou cartão ou Pix aqui.
- **Cancelamentos do mês** e o motivo.
- Números: recebido no mês, a receber, atrasado, assinantes ativos, entradas e saídas do mês.

Regras automáticas (configuráveis por barbearia):
- Recusou → o Asaas tenta de novo → no **dia 1** o bot manda o link → no **dia 3** lembra → no **dia 5** fica
  **atrasado** e o benefício é suspenso.
- Mudar de plano: vale a partir da próxima cobrança (sem cálculo proporcional na primeira versão).
- Reajuste de preço: no cartão vale para os próximos meses (avisar antes); no **PIX Automático o cliente precisa
  autorizar o novo valor** (regra do Banco Central). O Razor manda o pedido de nova autorização.

## 4. Suporte: como eu organizo para ajudar várias barbearias
- **Console → Cobrança:** todas as barbearias com a etapa (falta começar / em análise / ligada / testada / ativa),
  chave ok?, webhook recebendo?, último pagamento recebido, erros do Asaas dos últimos dias.
- **Logs organizados** de cada evento do Asaas (o mesmo "copiar para o Claude" que já existe).
- **Manual curto para o dono** (PDF de 1 página + vídeo de 2 min): como criar a conta, como gerar a chave, como
  dar baixa, o que fazer quando o cliente quer cancelar ou trocar de cartão.
- **Mensagens prontas** para o WhatsApp: convite para assinar, link de pagamento, cartão recusado, lembrete,
  benefício suspenso, reativação (migração), boas-vindas ao clube.
- **Perguntas comuns** (FAQ no painel): cartão recusado; cliente quer trocar o cartão (link "trocar forma de
  pagamento"); cancelar; trocar de plano; pagou na maquininha; quando o dinheiro cai no banco.
- Agenda de ativação: no máximo 2 barbearias novas por semana no começo, para dar conta do suporte.

## 5. Migração de quem vem da CashBarber (dia D)
1. **Antes:** a barbearia exporta a planilha de clientes e assinantes; o Razor importa com prévia; a conta Asaas
   fica **ativa e testada** (etapa 1 inteira).
2. **Dia D:** o bot manda para cada assinante antigo a mensagem de reativação com o link; no primeiro acesso ao
   site aparece **uma vez** o pop-up "cadastre o cartão ou o PIX".
3. **Durante 1 a 2 semanas:** a fila "Falta reativar" diminui. Cada assinante reativado aqui → a barbearia cancela
   aquele assinante na CashBarber. **Nunca cobrar em dobro nem deixar alguém sem cobrança.**
4. **Fim:** quando a fila zera (ou sobram poucos, tratados um a um), a barbearia desliga a CashBarber.

## 6. O Razor cobrando as barbearias
A mensalidade do Razor também pelo Asaas, numa **conta do Razor** (CPF por enquanto: cartão, boleto, Pix;
PIX Automático só quando o Razor tiver CNPJ). Taxas do clube que o Razor cobrir no plano "tudo incluso": o Razor lê
pela API as taxas do mês da barbearia e **abate na mensalidade seguinte**.

## 7. Comissão sobre o clube (pergunta da barbearia, 26/11)
**Hoje no Razor:** a receita do clube é 100% da barbearia; a comissão é por atendimento = **preço de tabela do
serviço × % do barbeiro** (ou % próprio do serviço). Isso vale também para o corte do assinante, ou seja, o barbeiro
ganha como se o corte fosse avulso — num clube ilimitado a comissão pode passar do que o assinante pagou.
(Pacotes já usam o valor unitário do pacote, não a tabela.)

**O que entra (escolha por barbearia em Comissões → "Clube"):**
1. **Pote do clube (rateio por produtividade)** — o modelo pedido: **X% do que o clube recebeu no mês** vira um
   pote, dividido entre os barbeiros pela produtividade nos atendimentos de assinante.
   Ex.: clube recebeu R$ 10.000, pote de 30% = R$ 3.000. Atendimentos de assinante no mês: barbeiro A 60, barbeiro B 90
   → A R$ 1.200, B R$ 1.800.
   - Produtividade por **quantidade** de atendimentos ou **ponderada pelo valor de tabela** (corte + barba pesa mais
     que só corte) — a barbearia escolhe; padrão: ponderada.
   - Base = **o que foi efetivamente recebido** no mês (cobranças pagas no Asaas + baixas manuais), não o previsto.
   - O pote fecha no fim do mês (o barbeiro vê a prévia durante o mês em "Meus ganhos").
2. **Valor fixo por atendimento de assinante** (ex.: R$ 15 por corte do clube).
3. **Como hoje** (tabela × %).
Atendimentos avulsos e produtos continuam com o % normal.

**As quatro fontes de comissão (análise de 26/11):**
| Fonte | Hoje | Como fica |
|---|---|---|
| Serviço avulso | tabela × (% do serviço, senão % do barbeiro) | igual (comissão por serviço entregue em 26/11) |
| Serviço **coberto pelo plano** do assinante | tabela × % (como se fosse avulso) ❌ | **pote do clube** ou fixo por atendimento |
| **Extra** do assinante (o que o plano não cobre, ex.: plano só corte e fez barba) | tabela × % (não separa do coberto) | % normal do serviço, separado do coberto; desconto do plano (se houver) entra na base |
| Produto vendido | venda atribuída a um barbeiro × % de produto do barbeiro; venda sem barbeiro = sem comissão | **por produto**: "não paga comissão" / % próprio / % do barbeiro |
| Pacote | valor unitário do pacote × % | igual |

Para isso a comanda precisa marcar, em cada item do assinante, se foi **coberto pelo plano** ou **extra** (o Razor
já sabe os benefícios de cada plano: serviço, dias da semana e limite por mês). O relatório de comissões passa a
mostrar quatro colunas por barbeiro: **clube · serviços · extras · produtos**, e "Meus ganhos" do barbeiro também.

## 8. Regras definidas com o primeiro cliente (26/11)
- **Comissão do jeito que o dono quiser:** nada fixo no sistema. Pote do clube com % e critério de divisão
  escolhidos pelo dono (quantidade, valor, pesos por serviço, ou manual), % por serviço, % por produto (ou não
  paga), extra do assinante, e **taxa da maquininha descontada da comissão** (opcional): o dono informa a taxa de
  débito, crédito à vista e parcelado; a comissão é calculada sobre o valor líquido daquele pagamento.
- **Troca de cartão na migração (a parte mais difícil):** o cartão salvo na CashBarber/DOM não passa para o Asaas
  sozinho (os dados ficam guardados no cofre do gateway antigo, regra de segurança dos cartões — PCI).
  1. Perguntar ao Asaas e à CashBarber/DOM se fazem **migração da base de cartões entre gateways** (PCI para PCI).
     Se fizerem, o assinante antigo nem percebe.
  2. Se não: o assinante antigo **cadastra o cartão uma vez** (link no WhatsApp / pop-up no primeiro acesso),
     sem trocar o dia de cobrança nem o preço; enquanto isso continua sendo cobrado no sistema antigo.
  - **Assinantes antigos ficam no cartão**; **novos** (sem clube antes) podem escolher cartão ou **PIX Automático**.
- **Dois números:** o da barbearia vai para o bot; o pessoal do dono fica como contato/alertas do dono.
- Nota fiscal: precisa (o cliente tem CNPJ) → o Razor precisa de CNPJ antes de cobrar dele.
- Ele vai mostrar a CashBarber por dentro, como usuário, para comparar funções. Regra: olhar e anotar o que ela
  oferece; **não copiar** código, textos, telas nem dados de outras pessoas, e não usar automação/robô no site dela.

## 9. Depois: anúncios (Meta/Instagram)
- Painel "De onde vêm os clientes": link do site com marcação para a bio e os anúncios do Instagram (o
  agendamento guarda a origem), o bot pergunta "como nos conheceu?" no primeiro contato.
- Ligar a conta de anúncios da Meta (login do Facebook do dono; exige aprovação do app pela Meta) para mostrar
  gasto, alcance e cliques ao lado de **clientes novos e faturamento que vieram do anúncio** → custo por cliente
  novo. Primeiro a origem (simples), depois a integração com a Meta.

## Ordem de construção (2ª quinzena de dezembro)
1. Ligação da conta (chave criptografada, conferência, webhook automático) + situação no painel e no console.
2. Planos → assinaturas no Asaas; link de pagamento; webhook liga e desliga o plano do cliente.
3. Central do clube: filas, baixa manual, cobrar no WhatsApp, regras de atraso.
4. Mensagens do bot e termos do clube.
5. Importação da planilha + fila "Falta reativar" + pop-up único.
5b. Comissão do clube (pote por produtividade / fixo por atendimento), coberto × extra na comanda e comissão por produto.
6. Testes no **sandbox do Asaas** (ambiente de teste, sem dinheiro de verdade) e, por fim, o teste de R$ 5 real.
