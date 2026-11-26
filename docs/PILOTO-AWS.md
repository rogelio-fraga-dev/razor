# Piloto na AWS — estrutura montada (25/11/2026)

Estado (25/11, 23h45): **LIGADA por ordem do usuário, até ele mandar desligar.** Túnel da Cloudflare
(`razor-teste`) instalado e funcionando: landing em `razor.<seu-domínio>`, painel em `app-razor.<seu-domínio>`,
ambos com HTTPS. Ainda sem barbearias cadastradas e com a chave da OpenAI local.

## O que existe (tudo com a tag `projeto=razor`)

| Recurso | Nome | Observação |
|---|---|---|
| Servidor | `razor-piloto` (c7i-flex.large: 2 vCPU, 4 GB + 2 GB de swap) | Ubuntu 24.04, disco gp3 de 20 GB criptografado, sem IP fixo |
| Firewall | `razor-sg` | **Nenhuma porta de entrada.** Acesso por SSM; público só pelo túnel da Cloudflare |
| Papel | `razor-ec2-ssm` | SSM + leitura/escrita só no bucket do Razor |
| Bucket | `razor-backup-…` | Privado, criptografado; `backups/` apaga sozinho depois de 30 dias |
| Orçamento | `razor-piloto` | Aviso por e-mail em US$ 5 e US$ 10/mês (o filtro pela tag passa a valer ~24 h depois da primeira cobrança; ativar a tag `projeto` em Billing → Cost allocation tags) |

O plano gratuito da conta só libera alguns tipos de máquina; os de 4 GB são `c7i-flex.large` e
`m7i-flex.large`. Dá para trocar para `t3.small` (2 GB, mais barato e apertado) com a máquina parada.

Nada do curso foi tocado (servidor `aulas`, bucket e papéis dele).

## No servidor

- Código em `/opt/razor/app` (o `master` de 25/11, com o pente fino do bot), `.env` de produção com
  segredos novos (cópia local em `.env.piloto`, fora do git). Subida:
  `docker compose -p razor -f docker-compose.yml -f docker-compose.prod.yml --profile bot up -d --build`
- Backup: `/opt/razor/backup.sh` todo dia às 03:00 (horário de Brasília) para o bucket. Restauração
  testada em 25/11 (banco separado, 52 migrações, dados íntegros).
- Logs do Docker com rotação (20 MB × 3). `cloudflared` instalado, túnel ainda não configurado.
- Banco limpo: só a barbearia fictícia que as migrações criam.

## Testado em 25/11 (no próprio servidor)

Saúde do backend, painel, landing, console `/plataforma`, Evolution no ar, webhook recusando sem
token (401), cadastro de barbearia, login, painel autenticado (200) e sem login (401), página
pública `/b/<slug>`, backup e restauração. Memória com tudo ligado: ~1 GB dos 4 GB.

## Custo

- **Desligada:** ~US$ 1,60/mês (disco) + centavos do bucket.
- **Ligada:** ~US$ 0,085/h → ~US$ 14 por semana, ~US$ 28 em 2 semanas. Sai do crédito de US$ 180.

## Falta para abrir

1. ✅ **Cloudflare (feito em 25/11):** criar um túnel (Zero Trust → Networks → Tunnels → "Create tunnel", tipo
   Cloudflared) e mandar o *token*. Com ele: `cloudflared service install <token>` no servidor e,
   no painel do túnel, dois "Public hostnames":
   - `razor.<seu-domínio>` → `http://localhost:8000` (landing)
   - `app-razor.<seu-domínio>` → `http://localhost:3000` (painel, sites das barbearias e API)
2. ✋ **Chave da OpenAI do piloto**, com limite de gasto na conta da OpenAI. Hoje o servidor usa a
   mesma chave do ambiente local; trocar no console `/plataforma` (aba IA) ou no `.env`.
3. Ligar, conferir HTTPS, entrar no console (senha nova em `.env.piloto`, ativar o 2FA) e cadastrar as
   barbearias.

## Estimativa de gasto da OpenAI (5 barbearias, 2 semanas)

Medido no pente fino: 361 mensagens de cliente usaram 287 chamadas do gpt-4o-mini (~2.375 tokens de
entrada e ~52 de saída cada) e 14 do gpt-4o (reserva). Isso dá **~US$ 0,0006 por mensagem**
(preços: mini US$ 0,15/0,60 por milhão; 4o US$ 2,50/10; áudio US$ 0,006/min). Supondo 30% das
mensagens em áudio de ~15 s:

| Movimento por barbearia | Mensagens/dia | 5 barbearias × 14 dias | Com folga (×2) |
|---|---|---|---|
| Calmo | 60 | US$ 4,40 | US$ 8,70 |
| Normal | 120 | US$ 8,70 | US$ 17,40 |
| Movimentado | 250 | US$ 18,20 | US$ 36,30 |

Recomendação: colocar **US$ 20** de crédito e um limite mensal de US$ 20 na conta da OpenAI.
O console `/plataforma` mostra o gasto real por dia.
