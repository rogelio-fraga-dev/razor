# 🛠️ Guia de Inicialização do Razor

Como ligar o sistema do zero. O **Passo 1** já entrega o Razor rodando; os passos seguintes só
são necessários quando você for conectar um WhatsApp de verdade.

---

## 🐳 Passo 1: Subir o stack

Na raiz do projeto:

```bash
docker compose up -d --build
```

Isso sobe **banco + API + painel + landing** de uma vez. O primeiro build demora alguns minutos
(compila o Spring Boot e o painel dentro do Docker); os próximos são rápidos por causa do cache.

| Serviço | Endereço | O que é |
| --- | --- | --- |
| Landing | http://localhost:8000 | site institucional — é por onde o visitante chega |
| Painel | http://localhost:3000 | o sistema; o botão "Entrar" da landing leva para cá |
| API | http://localhost:8081 | o painel já fala com ela pelo próprio nginx, em `/api` |
| Postgres | localhost:5433 | |

> [!TIP]
> Para testar o caminho real do visitante, comece pela **landing** e clique em *Entrar* — é o
> mesmo trajeto que um barbeiro faria. O endereço do painel não está cravado no HTML: vem de
> `RAZOR_PANEL_URL`, que o container transforma em `config.js` no boot. Trocar de ambiente é
> trocar essa variável, não editar arquivo.

Acompanhe o boot com `docker compose logs -f backend`. O Flyway cria as ~30 tabelas na primeira
execução; a API está pronta quando o log mostra `Started RazorApplication`.

Conferir se está tudo de pé:

```bash
docker compose ps                                  # os 4 serviços em "Up"
curl http://localhost:8081/actuator/health          # {"status":"UP"}
curl -I http://localhost:8000                       # landing: 200
curl http://localhost:8000/config.js                # deve mostrar o endereço do painel
```

> [!NOTE]
> O banco fica num volume Docker (`razor_pgdata`) e **sobrevive** ao `docker compose down`.
> Para zerar de verdade: `docker compose down -v`.

---

## 🔑 Passo 2: Configurar (opcional)

O stack sobe sem nenhum arquivo de configuração — só com a IA desligada e um telefone admin
fictício. Isso já basta para navegar no painel. Para ligar a IA:

```bash
cp .env.docker.example .env
```

Preencha e reinicie com `docker compose up -d`:

| Variável | Para quê |
| --- | --- |
| `OPENAI_API_KEY` | chave real da OpenAI — sem ela o bot não gera resposta |
| `ADMIN_PHONES` | seu WhatsApp com 55 e DDD, para ter poder de admin no bot |
| `WEBHOOK_TOKEN` | segredo do webhook — vazio deixa o webhook desabilitado (fail-closed) |
| `ONBOARDING_API_KEY` | habilita criar barbearias |
| `RAZOR_JWT_SECRET` | assina os JWT do painel (< 32 caracteres = você cai do painel a cada restart) |

---

## 💈 Passo 3: Popular o banco

O banco nasce vazio. Escolha um dos dois.

### a) Seed de demonstração (recomendado para testar)

```powershell
.\scripts\seed.ps1
```

Cria a "Barbearia Navalha de Prata" com ~60 dias de movimento — 335 comandas, 52 caixas fechados,
estoque, comissões, cupons e planos — para o painel ter gráfico e relatório de verdade.

| Login | Senha | Papel | O que enxerga |
| --- | --- | --- | --- |
| `dono@navalhademo.local` | `demo12345` | OWNER | tudo |
| `recepcao@navalhademo.local` | `demo12345` | RECEPTION | comandas, caixa, clientes, agenda e o catálogo (só leitura) |
| `barbeiro@navalhademo.local` | `demo12345` | BARBER | a agenda do dia e o próprio perfil |

> [!NOTE]
> Os três papéis existem para **testar o RBAC**: entrar como recepção ou barbeiro mostra um menu
> menor, e a API recusa (403) o que o menu esconde. Menu diferente não é bug — é a regra
> funcionando. A tabela completa está no `SecurityConfig`.

O script só mexe no tenant da demo — rodar de novo regenera os dados fictícios sem tocar em
nenhuma outra barbearia do banco.

### b) Barbearia vazia (fluxo real de provisionamento)

Cria barbearia + catálogo + barbeiro + usuário do painel numa transação só:

```bash
curl -X POST http://localhost:8081/api/onboarding \
  -H "Content-Type: application/json" \
  -H "X-Onboarding-Key: dev-onboarding-key" \
  -d '{"name":"Barbearia Teste","slug":"barbearia-teste","whatsappInstance":"BarbeariaTesteWpp",
       "professionalName":"Rafael","adminPhones":["5511999999999"],
       "ownerEmail":"dono@teste.com","ownerPassword":"senha12345"}'
```

Agora entre em http://localhost:3000 com `dono@teste.com` / `senha12345`.

---

## 📲 Passo 4: Conectar o WhatsApp (só quando for testar o bot)

A Evolution API fica fora do `up` padrão. Suba junto:

```bash
docker compose --profile bot up -d      # Evolution em http://localhost:8080
```

O webhook já vem apontado para o backend com o token correto — não precisa configurar nada no
Evolution Manager. Só falta parear o aparelho:

1. Painel → **Configurações → Conexão do WhatsApp → Conectar**.
2. Escaneie o QR Code no celular (**Aparelhos Conectados → Conectar um aparelho**).

Se o QR não aparecer no painel, o backend também o expõe em
`http://localhost:8081/api/webhook/qrcode`.

---

## 📈 Verificação final

1. `curl http://localhost:8081/actuator/health` → `{"status":"UP"}`.
2. Painel abre em http://localhost:3000 e aceita o login criado no Passo 3.
3. Com o bot conectado, mande `"Oi"` do celular do cliente — o bot responde com o menu.

---

## 🔧 Comandos do dia a dia

```bash
docker compose logs -f backend    # logs da API
docker compose logs -f web        # logs do nginx do painel
docker compose restart backend    # reiniciar só a API
docker compose up -d --build      # rebuild depois de mexer no código
docker compose down               # parar tudo (banco sobrevive)
docker compose down -v            # parar e APAGAR o banco
```

Para abrir o banco direto:

```bash
docker compose exec postgres psql -U barberbot_user -d barberbot_db
```

---

## 💻 Desenvolvendo com recarga automática

O stack acima roda o código **compilado dentro da imagem** — mexeu no código, precisa de
`--build`. Para desenvolver, deixe só o banco no Docker:

```powershell
docker compose up -d postgres        # só o banco, em 5433
.\dev.ps1                            # backend em 8081, fora do Docker
cd web; npm run dev                  # painel em http://localhost:5173
```

Nesse modo o painel fala com a API pelo proxy do Vite; o `.env.local` (não o `.env`) é quem
alimenta o backend. Veja a seção 7.2 do `README.md`.

*Pronto! ✂️💈*
