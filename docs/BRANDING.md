# 🪒 Branding — Razor

## Nome

**Razor** (navalha, em inglês) — o instrumento essencial do barbeiro. Curto, afiado,
memorável, forte identidade visual e pronunciável em português e inglês (bom para escala).

Substitui o nome de trabalho anterior `barberbot-core`.

## Posicionamento

> **Razor — o barbeiro digital que atende e gere sua barbearia.**

O Razor une duas coisas que hoje vivem em produtos separados:
- **Atende** seus clientes por IA no WhatsApp (a nossa cunha).
- **Gere** sua barbearia num painel web completo (paridade com Frizzar/CashBarber).

## Frase-âncora

> _"Pago um e resolvo os dois lados: o balcão e o caixa."_

## Impacto técnico do rebrand

| Item | De | Para |
|---|---|---|
| Pacote Java | `com.barberbot` | `com.razor` |
| Classe principal | `BarberBotApplication` | `RazorApplication` |
| Config properties | `BarberBotProperties` (`barberbot:`) | `RazorProperties` (`razor:`) |
| Maven `groupId`/`artifactId` | `com.barberbot` / `barberbot-assist` | `com.razor` / `razor` |
| Repositório GitHub | `barberbot-core` | `razor` *(rename externo — a confirmar)* |

> **Nota:** o rename do repositório no GitHub muda a URL pública e é feito à parte
> (`gh repo rename razor`). Só executar com confirmação explícita, pois quebra links/clones existentes.

## Identidade visual (para vender o Razor)

Precisamos de uma identidade própria e coesa para o **site institucional** e todo material
de venda (ver [`PLATFORM.md §5`](./PLATFORM.md#5-site-institucional-do-razor)). Escopo:

- **Logo:** a navalha como símbolo central; versões horizontal, ícone e monocromática.
- **Paleta:** preto/grafite + metálico (aço da navalha) + um acento quente (âmbar/cobre).
- **Tipografia:** um par com caráter (display marcante + texto legível) — nada de fonte padrão.
- **Tom de voz:** confiante, direto, "de barbearia" — sem jargão corporativo.
- **Aplicações:** site institucional, favicon, templates de e-mail, painel web, material de onboarding.

> A identidade é dupla: a marca **Razor** (o SaaS que você vende) é fixa; já a marca de
> **cada barbearia** é white-label (configurável por tenant no painel — Fase 3).

## Ativos a produzir

- [ ] Logo + variações
- [ ] Guia de marca (paleta, tipografia, uso)
- [ ] Site institucional `razor.com.br` (Fase 4)
- [ ] Templates de e-mail transacional (boas-vindas, cobrança, recuperação)
