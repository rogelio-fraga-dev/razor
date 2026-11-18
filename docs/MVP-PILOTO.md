# MVP para piloto — plano de trabalho

Objetivo: deixar o Razor pronto para rodar em 1–3 barbearias piloto, ainda local/sem domínio.
Pagamentos ficam de fora até a decisão de modelo (ver "Decisões pendentes").

Migrações reservadas por frente (evita colisão entre agentes): V46 bot, V47 automações,
V48 painel, V49 cadastro, V50 monitoramento, V51 área do cliente, V52 metas, V53 multiunidade.

## Onda 1 (em produção)

### 1. Bot — conversa e avaliação (V46)
- [ ] Pedir o SERVIÇO antes do horário: o filtro "barbeiro que faz o serviço" passa a valer sempre.
- [ ] Avaliação pós-atendimento: o bot pede uma nota de 1 a 5 após a comanda fechar; nota 4–5 recebe
      o link de avaliação do Google (configurável); nota baixa vai só para o dono, como alerta.

### 2. Automações (V47)
- [ ] Lista de espera automática: ao cancelar, oferecer o horário a quem espera (ordem de chegada,
      prazo para aceitar, um por vez).
- [ ] Campanhas automáticas configuráveis: "sumiu há N dias", aniversário com cupom (campo de
      aniversário no cliente), lembrete de retorno do corte (intervalo por serviço).
- [ ] Relatório semanal no WhatsApp do dono: faturamento, faltas, melhor barbeiro, horários vazios.
- Regras: opt-out do cliente ("parar"), limite de frequência, horário comercial, nunca para inativo.

### 3. Painel — permissões e acabamento (V48)
- [ ] Recepção não vê "Editar" em Serviços (UI alinhada à API).
- [ ] Barbeiro só mexe nos próprios agendamentos (API + UI).
- [ ] Editar nome e e-mail de usuário do painel.
- [ ] Editor do site: aviso de alterações não salvas ao trocar de aba/navegar; passos de
      "Como agendar" editáveis; prévia em tela cheia.
- [ ] Faturamento previsto sem as faltas.
- [ ] react-router 7 (alerta de segurança).
- [ ] Agenda no celular: barra do topo compacta.
- [ ] PWA instalável de verdade (ícones 192/512 maskable, screenshots, prompt de instalação).

### 4. Cadastro guiado e landing (V49)
- [ ] Cadastro sozinho pela landing: barbearia + dono, período de teste (só status, sem cobrança).
- [ ] Assistente de 10 minutos: dados da casa → horário → serviços → equipe → QR do WhatsApp →
      teste do bot → site.
- [ ] Páginas de Termos, Privacidade (LGPD) e Cancelamento — RASCUNHO, revisar com advogado.
- [ ] Imagem de capa (og:image 1200×630) para a prévia do link.

### 5. Técnico (V50)
- [ ] Monitoramento e alertas: WhatsApp desconectado, falha da IA, fila de envio parada → aviso ao
      dono (WhatsApp/painel) e ao operador da plataforma.
- [ ] Testes de ponta a ponta com Playwright (roteiros dos principais fluxos).
- [ ] Armazenamento de fotos com interface trocável: banco (padrão) ou S3 (opcional por config).

### 6. Vídeo para Reels (marketing)
- [ ] Roteiro de 30–45s para narração por cima (gancho nos 3 primeiros segundos, dor → solução →
      prova visual → chamada), com marcação de tempo, texto da narração e o que aparece na tela.
- [ ] Vídeo vertical 1080×1920, bem dinâmico (cortes rápidos, zooms, textos cinéticos, celular 3D com
      a conversa do bot, agenda se preenchendo, site do cliente, card "O que a IA fez"), sem
      narração gravada — com espaço para a voz e versão com legendas queimadas.
- [ ] Fonte do vídeo versionada (cenas em HTML/CSS animado, renderizadas para MP4) para dar para
      editar textos e regravar.

## Onda 2 (depois da onda 1)
- Área do cliente no site (V51): entrar com código pelo WhatsApp; ver, remarcar e cancelar
  agendamentos; saldo de pacote; status do clube.
- Metas e ranking dos barbeiros com comissão variável por meta (V52).
- Várias unidades na mesma conta (V53): troca de unidade no painel, relatórios consolidados.
- Instagram: botão "Reservar" apontando para a página de agendamento (guia de configuração).

## Decisões pendentes (não fazer ainda)
- Cobrança da assinatura do Razor (recorrência, teste grátis com cartão, inadimplência).
- Pagamentos para as barbearias: sinal PIX com baixa automática, pagamento online no agendamento,
  cobrança recorrente do clube. Escolher gateway (Mercado Pago, Asaas, Efí, Stripe…).
- NFS-e: depende de provedor (Focus NFe, eNotas, PlugNotas) e da prefeitura de cada piloto.
- Resposta automática no Direct do Instagram: exige app Meta verificado (API do Instagram).
- App Android na Play Store: dá para empacotar o PWA como TWA (Bubblewrap) quando houver domínio
  com HTTPS e o arquivo assetlinks.json; iOS instala pelo "Adicionar à Tela de Início".
- Domínio, subdomínio por barbearia, HTTPS e deploy (deixado para o final).
