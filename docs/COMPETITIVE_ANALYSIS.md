# 📊 Análise Competitiva — Razor vs. Frizzar vs. CashBarber

> Objetivo: entender exatamente o que os líderes oferecem, onde estamos, o que falta,
> e por que um barbeiro trocaria o concorrente pelo Razor.
>
> _Pesquisa realizada em 2026-07 nas fontes listadas ao final._

---

## 1. Os concorrentes

### Frizzar
Sistema de gestão para salões e barbearias. Escala por número de agendas
(Solo → Duo → Essencial → Pro → Premium).

- **Preço:** R$57–249/mês (anual/mensal, 12x).
- **Forças:** gestão completa e madura.
- **Funcionalidades:**
  - Agendamento online 24/7 com link personalizado do cliente.
  - Agenda multiprofissional (cada barbeiro abre/fecha comanda, marca folga, faz encaixe).
  - Financeiro: fluxo de caixa, DRE, relatórios financeiros, comandas.
  - Estoque: cadastro de produtos, reposição, relatórios de saída.
  - Comissões automáticas por profissional, com recibo/comprovante.
  - Clube de assinatura / pacotes recorrentes.
  - Fidelidade, lembrete de aniversário, pesquisa de satisfação, avaliações.
  - Chatbot de WhatsApp (confirmações, lembretes, coleta de avaliação) — **recente e roteirizado**.
  - Multi-dispositivo (celular, computador, tablet).

### CashBarber
Sistema de gestão + inteligência de retenção + mentoria. 3 mil+ barbearias.

- **Preço:** a partir de R$300/mês (inclui cursos + mentoria).
- **Forças:** dados de retenção e educação do dono.
- **Funcionalidades:**
  - Indicadores de performance: "quem volta, quem some, o que fazer pra crescer".
  - Financeiro em tempo real (faturamento, produtividade).
  - Alertas automáticos de risco de perda de cliente.
  - Clube de assinatura com planos personalizáveis (quantidade, procedimento, dia da semana);
    identifica automaticamente assinante x avulso no agendamento.
  - Comissões por serviço e por produto (não distribui comissão de assinatura).
  - Cursos de estratégia + mentoria.

---

## 2. Matriz de funcionalidades

Legenda: ✅ tem · 🟡 parcial · ❌ não tem · 🎯 alvo do roadmap

| Funcionalidade | Frizzar | CashBarber | **Razor (hoje)** | **Razor (alvo)** |
|---|:---:|:---:|:---:|:---:|
| **Atendimento IA no WhatsApp (linguagem natural)** | 🟡 chatbot | ❌ | ✅ | ✅ |
| Agendamento por conversa (sem menu rígido) | ❌ | ❌ | ✅ | ✅ |
| Transcrição de áudio (Whisper) | ❌ | ❌ | ✅ | ✅ |
| Leitura de agenda por foto (Vision) | ❌ | ❌ | ✅ | ✅ |
| Lista de espera reativa automática | ❌ | ❌ | ✅ | ✅ |
| Lembretes / confirmação automáticos | ✅ | 🟡 | ✅ | ✅ |
| Fidelidade / pontos | ✅ | 🟡 | ✅ | ✅ |
| PIX no fechamento | 🟡 | 🟡 | ✅ | ✅ |
| **Painel web de gestão** | ✅ | ✅ | ❌ | 🎯 Fase 3 |
| **Multi-tenant (N barbearias)** | ✅ | ✅ | ❌ | 🎯 Fase 1 |
| Agenda multiprofissional | ✅ | ✅ | ❌ | 🎯 Fase 1 |
| Catálogo de serviços (preço/duração) | ✅ | ✅ | 🟡 texto | 🎯 Fase 1 |
| Financeiro / caixa / comandas | ✅ | ✅ | ❌ | 🎯 Fase 2 |
| Comissões automáticas | ✅ | ✅ | ❌ | 🎯 Fase 2 |
| **Clube de assinatura (recorrência)** | ✅ | ✅ | ❌ | 🎯 Fase 2 |
| Relatórios / DRE | ✅ | ✅ | 🟡 resumo | 🎯 Fase 2 |
| Dashboard de retenção ("quem some") | 🟡 | ✅ | ❌ | 🎯 Fase 2/4 |
| Estoque | ✅ | ❌ | ❌ | 🎯 Fase 2 |
| Site / agendamento online próprio | ✅ | 🟡 | ❌ | 🎯 Fase 3 |

---

## 3. Onde ganhamos, onde perdemos

### 🟢 Nossa vantagem (a cunha)
O atendimento é **conversacional de verdade** — o cliente escreve ou manda áudio como
falaria com um humano, e a IA agenda, negocia, cancela e oferece vaga da lista de espera.
Frizzar tem "chatbot" roteirizado; CashBarber não tem. Isso reduz no-show, preenche
buracos de agenda sozinho e não exige que o cliente baixe app nenhum.

### 🔴 Nosso débito (o que este roadmap resolve)
1. **Não é multi-tenant** — só serve uma barbearia. _(Fase 1)_
2. **Não tem painel web** — gestão é por comando de WhatsApp, insustentável para vender. _(Fase 3)_
3. **Sem financeiro/comissões/assinatura** — o núcleo do valor pago dos concorrentes. _(Fase 2)_
4. **Sem multiprofissional** — barbearia real tem vários barbeiros. _(Fase 1)_

---

## 4. Tese de migração

> _"Por que eu trocaria meu Frizzar pelo Razor?"_

Quando as Fases 1–3 estiverem prontas, a resposta é:

> **"Porque o Razor faz tudo o que o Frizzar faz na gestão — E ainda atende meus clientes
> sozinho por IA no WhatsApp, enche minha agenda com a lista de espera e traz de volta quem
> ia sumir. Pago um e resolvo os dois lados: o balcão e o caixa."**

O painel web nos coloca em **paridade**; a IA de atendimento nos coloca **à frente**.

---

## Fontes

- [Frizzar — App para Barbearia: 7 Funcionalidades](https://frizzar.com.br/blog/app-para-barbearia-funcionalidades-dia-a-dia/)
- [Frizzar — Sistema (planos e recursos)](https://a.frizzar.com.br/)
- [Frizzar — Chatbot na Barbearia](https://frizzar.com.br/blog/chatbot-na-barbearia-melhore-atendimento-sem-elevar-custos/)
- [Cash Barber — Site oficial](https://sistemacashbarber.com.br/)
- [Barbeiro.app — Melhor Sistema para Barbearia 2026](https://www.barbeiro.app/blog/melhor-sistema-para-barbearia-2026)
- [Trinks — Sistema para barbearia](https://www.trinks.com/negocios/sistema-para-barbearia)
- [BestBarbers — Sistema com app próprio e clube de assinaturas](https://www.bestbarbers.app/sistema-para-barbearia)
