# DESIGN.md — Razor

Arquivo único que qualquer pessoa (ou assistente) lê **antes de mexer numa tela** do Razor: painel,
landing, modelos de site das barbearias e console da plataforma. Formato inspirado nos `DESIGN.md` do
repositório *awesome-design-md* (o mais próximo do nosso visual é o do Linear). A estratégia de marca
continua em `docs/BRANDING.md`; aqui ficam só as regras de tela.

## Visão geral

Escuro primeiro, um acento só (azul Voltage) e o gradiente-assinatura de 68° em pouquíssimos lugares.
Números sempre na fonte mono, alinhados em coluna. A barbearia vê o próprio dia num olhar: o que é
**de hoje** brilha, o resto fica quieto.

## Cores

| Papel | Token | Escuro | Claro |
|---|---|---|---|
| Fundo do app | `--obsidian` | `#0A0A0C` | `#F6F7F9` |
| Superfície (cartão) | `--graphite` | `#141519` | `#FFFFFF` |
| Superfície elevada | `--graphite-raise` | `#1A1C22` | `#F0F2F5` |
| Texto | `--chrome` | `#E9EBEF` | `#12141A` |
| Texto de apoio | `--steel` | `#8B93A1` | `#545C69` |
| Acento (ação, foco) | `--voltage` | `#3E63FF` | `#2F51E6` |
| Fio (detalhe) | `--edge` | `#37E2FF` | `#0789A3` |
| Sucesso / pago | `--signal` | `#25D366` | — |
| Atenção | `--amber` | `#FBBF24` | `#8A5200` |
| Erro | `--danger` | `#F87171` | — |

- Gradiente-assinatura: `linear-gradient(68deg, #3E63FF, #37E2FF)`. Usar em **um** destaque por tela
  (o cartão principal, a palavra do título, a barra de progresso). Nunca em fundo de seção inteira.
- Bordas: `rgba(255,255,255,.08)` normal e `.16` no hover. Sem sombra preta pesada no escuro.
- Proporção: 70% fundo, 20% superfície, 8% texto/acento e 2% gradiente.

## Tipografia

| Uso | Fonte | Peso | Observação |
|---|---|---|---|
| Títulos | Archivo | 700–800 | `letter-spacing: -0.02em` a `-0.04em`; `text-wrap: balance` |
| Texto | Inter | 400–600 | 15–16px no painel, 16–18px na landing |
| Números, horários, valores | JetBrains Mono | 500–700 | sempre `font-variant-numeric: tabular-nums` |
| Rótulos pequenos (olho) | JetBrains Mono | 600 | MAIÚSCULAS, `letter-spacing: .14–.18em`, 11–12px |

Modelos de site: cada um tem fonte própria (Clássico serifada, Estúdio editorial, Vitrine em cartaz),
carregada só quando o modelo é usado.

## Espaço e forma

- Raios: 8 (campo, chip), 12 (botão, cartão pequeno), 16–22 (cartão, janela). Pílula só em filtro.
- Espaço em múltiplos de 4. Seções da landing: `clamp(80px, 11vw, 136px)` em cima e embaixo.
- Painel: menu lateral de 240px (64px recolhido); no celular, barra inferior com os 4 atalhos + "Mais".

## Componentes

- **Botão primário:** fundo Voltage, texto branco, sombra azul suave. Desce 1px no clique.
- **Botão fantasma:** borda fina, fundo transparente. Um primário por área; o resto é fantasma.
- **Cartão:** superfície + borda fina. Cartão com link sobe 2px no hover. Cartão de destaque leva o
  canto com o gradiente (`card-edge`).
- **KPI:** rótulo em olho mono, valor grande em mono, linha de apoio em `--steel`. O valor conta até o
  número ao abrir a tela (`<CountUp>`).
- **Campos:** sempre com `<label>`, foco com anel Voltage (`--focus-ring`), erro dizendo como consertar.
- **Ação destrutiva** (apagar, zerar, cancelar): sempre com confirmação.
- **Botão só de ícone:** sempre com `aria-label`.

## Movimento

Três camadas, e nada além delas:

1. **Entrada:** o título do topo da landing sobe linha a linha; os KPIs do painel entram em cascata.
2. **Um momento por página:** na landing, a cena fixa "Um dia na barbearia"; em cada modelo de site, a
   frase da casa que acende palavra por palavra e a foto que revela; no painel, o carimbo **PAGO** ao
   fechar comanda e o gráfico que se desenha.
3. **Resposta:** hover, clique e troca de tela (View Transitions: só o conteúdo desliza; menu e topo
   ficam parados).

Regras:
- Animar só `transform` e `opacity` (exceção: `clip-path` da foto que revela, uma por página).
- Nunca `transition: all`.
- Conteúdo nunca depende de JS para aparecer: sem script, tudo nasce visível.
- `prefers-reduced-motion: reduce` desliga tudo (as cenas viram páginas estáticas).
- Nada de carrossel que troca sozinho, nem texto que se move em velocidade diferente da página.
- Curvas: entrada `cubic-bezier(0.16, 1, 0.3, 1)`; carimbo com "batida" `cubic-bezier(0.3, 1.6, 0.5, 1)`.

## Texto

Português direto, como o barbeiro fala. Evitar:
- palavras vazias ("poderoso", "incrível", "revolucionário", "transformar");
- pergunta respondida na hora ("O resultado? ...");
- travessão em toda frase, emoji como marcador e fecho de efeito no fim do parágrafo;
- `...` (usar `…`).

## Conferência antes de entregar uma tela

Lista da Vercel (*web-design-guidelines*): foco visível no teclado, `label` nos campos, `aria-label`
nos ícones, contraste AA nos dois temas, alvo de toque ≥ 40px, `touch-action: manipulation`,
`theme-color` certo, números em `tabular-nums`, aba/filtro na URL quando fizer sentido. Fotografar no
computador (1440) e no celular (390) com o Playwright (`video/tools/`).
