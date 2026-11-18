// Glossário do painel em linguagem de balcão. Uma frase curta por termo — quem lê está com a
// tesoura na outra mão. Usado pelo <Hint term="..."/> ao lado de cada jargão; mudar o texto
// aqui muda em todas as telas.
export const GLOSSARY = {
  dre: "DRE é o resumo do mês: tudo que entrou menos tudo que saiu — mostra se sobrou dinheiro.",
  comanda: "Comanda é a conta do cliente: serviços e produtos que ele consumiu, fechada na hora de pagar.",
  sangria: "Sangria é tirar dinheiro do caixa no meio do dia (ex.: levar para o cofre ou pagar algo).",
  suprimento: "Suprimento é colocar dinheiro no caixa, como o troco do começo do dia.",
  acerto: "Acerto é o dia de pagar ao barbeiro a comissão que ele juntou no período, descontando vales.",
  ticketMedio: "Ticket médio é quanto cada cliente gasta, em média, por visita.",
  ocupacao: "Taxa de ocupação é quanto do horário da equipe ficou com cliente marcado. 100% = cadeira cheia.",
  comissaoProduto: "Comissão por produto é a parte do valor de um produto vendido que vai para o barbeiro que vendeu.",
  split: "Split é dividir o pagamento: parte vai para a barbearia e parte direto para o barbeiro.",
  comissao: "Comissão é a parte do valor do serviço que fica com o barbeiro que atendeu.",
  vale: "Vale é um adiantamento ao barbeiro; ele é descontado no próximo acerto.",
} as const;

export type GlossaryTerm = keyof typeof GLOSSARY;
