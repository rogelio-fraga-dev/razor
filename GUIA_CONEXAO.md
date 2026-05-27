# 📱 Guia Passo a Passo: Conexão e Gestão do BarberBot AI

Este guia contém todos os links, credenciais e instruções detalhadas para você conectar o seu **WhatsApp Business (Bot)** e gerenciar o sistema usando o seu **número pessoal (Administrador)**.

---

## 🔗 Links Rápidos do Sistema

| Serviço | Link de Acesso | Objetivo |
| :--- | :--- | :--- |
| 🚀 **Evolution Manager** | [http://localhost:8080/manager](http://localhost:8080/manager) | Painel visual para conectar o WhatsApp e ler o QR Code |
| 🟢 **Health Check do Bot** | [http://localhost:8081/api/webhook/health](http://localhost:8081/api/webhook/health) | Verificar se o bot principal do Spring Boot está online |
| 📸 **Fallback do QR Code** | [http://localhost:8081/api/webhook/qrcode](http://localhost:8081/api/webhook/qrcode) | Exibir o QR Code em imagem caso o painel do Evolution trave |

---

## 🔑 Credenciais e Configurações

* **🔑 API Key Global (Evolution):** `barberbot`
* **🏷️ Nome Exato da Instância:** `BarberBotOficial`
* **🌐 URL do Webhook do Bot:** `http://host.docker.internal:8081/api/webhook`
* **👑 Seu Número Administrador:** `5534984141504`

---

## 📲 Passo 1: Conectar o WhatsApp Business (QR Code)

1. Abra o navegador no link do gerenciador: [**http://localhost:8080/manager**](http://localhost:8080/manager).
2. Na tela de login, insira a **API Key Global**: **`barberbot`** e clique em **Entrar**.
3. No menu lateral esquerdo, vá em **Instâncias**.
4. Clique no botão azul **Criar Instância** (ou *Create Instance*) e configure com estes dados exatos:
   * **Nome da Instância:** `BarberBotOficial` *(Atenção: use exatamente este nome, respeitando maiúsculas e minúsculas!)*
   * **Integração:** `WHATSAPP-BAILEYS`
5. Clique em **Salvar / Criar**.
6. A instância criada aparecerá na listagem. Clique no botão **Conectar** (ou *Connect*).
7. O **QR Code** será exibido na tela.
8. No celular do seu **WhatsApp Business** (o número que será o bot), abra o aplicativo WhatsApp, toque nos três pontinhos e vá em **Aparelhos Conectados > Conectar um aparelho**, então escaneie o QR Code na tela do computador.
9. **Dica de Ouro:** Se o painel do Evolution Manager travar e não exibir o QR Code, abra no seu navegador a nossa URL de fallback: [**http://localhost:8081/api/webhook/qrcode**](http://localhost:8081/api/webhook/qrcode) (nosso backend intercepta o QR Code e gera essa imagem estática para facilitar a leitura!).

---

## 🔗 Passo 2: Configurar a Integração (Webhook)

Para que as mensagens recebidas na Evolution cheguem até o cérebro de Inteligência Artificial do BarberBot, ative o Webhook:

1. No Evolution Manager, clique no ícone de engrenagem (**Configurações**) na linha correspondente à instância `BarberBotOficial`.
2. Acesse a aba **Webhook** e marque a opção **Ativar Webhook** (ou *Enable Webhook*).
3. No campo **URL do Webhook**, cole o seguinte link:
   ```
   http://host.docker.internal:8081/api/webhook
   ```
4. Nos **Eventos a Escutar** (Webhook Events), certifique-se de marcar apenas os necessários para a operação saudável do bot:
   * [x] **`MESSAGES_UPSERT`** (Lê e responde as mensagens dos clientes)
   * [x] **`QRCODE_UPDATED`** (Sincroniza a imagem de conexão)
   * [x] **`CONNECTION_UPDATE`** (Monitora se o bot está conectado/desconectado)
5. Clique em **Salvar** no final da página.

---

## 🧪 Passo 3: Testar o Atendimento do Bot

1. Com outro celular (qualquer número de cliente, que **não seja** o seu administrador nem o do próprio bot), envie a palavra `"Oi"` ou `"Menu"` para o número do seu bot (WhatsApp Business).
2. O bot deve responder enviando o menu interativo com o botão **"Ver Opções 📋"**.
3. No console/terminal onde o Spring Boot está rodando, os logs coloridos começarão a registrar:
   `[RECEBIDO] Nova mensagem de: Rogelio Claro (553484141504) | Tipo: TEXTO`

---

## 👑 Passo 4: Painel do Administrador (Seu Celular Pessoal)

Como o seu número pessoal **`5534984141504`** é o administrador cadastrado, o sistema tem recursos exclusivos para você. Se você mandar mensagens ou **áudios** para o número do bot, ele responderá como o **Painel Administrativo**.

### 📋 Lista de Comandos de Gestão

Você pode enviar ou **gravar mensagens de voz (áudios)** com estas palavras-chaves:

| Comando | O que o Bot faz |
| :--- | :--- |
| **`menu`** ou **`ajuda`** | Exibe os botões de ações do painel de controle. |
| **`dashboard`** ou **`resumo`** | Retorna o total de clientes na base e o faturamento estimado do caixa no dia. |
| **`aviso`** | Permite que você digite um comunicado para enviar para todos os clientes da base. |
| **`promocao`** | Envia uma oferta promocional especial em lote para a base. |
| **`agenda`** | Lista todos os horários e clientes agendados. |
| **`matinal`** | Exibe o relatório matinal de expediente (bom dia). |
| **`noturno`** | Gera o relatório detalhado de fechamento diário (PIX / Dinheiro). |
| **`pausar`** | Pede o telefone de um cliente para silenciar o bot por 15 minutos (transbordo humano). |
| **`retomar`** | Exibe a lista de clientes pausados para você reativar o atendimento da IA. |
| **`listar`** | Exibe toda a lista de clientes salvos no banco. |
| **`apagar`** | Permite deletar um cliente e histórico de mensagens permanentemente do banco. |
| **Enviar foto da Agenda** | A IA do GPT-4o Vision lerá a imagem da sua agenda física de papel e cadastrará todos os clientes e horários no banco automaticamente! |
| **Enviar planilha CSV** | Cadastra centenas de contatos em lote na base de dados em segundos. |

---

## ☁️ Plano de Implantação: Hospedagem na AWS EC2

Para colocar o BarberBot AI em produção operando 24 horas por dia de forma profissional e escalável na nuvem da AWS, siga este planejamento de arquitetura e implantação.

### 1. Dimensionamento da Instância EC2
* **Instância Recomendada:** `t3.small` (2 vCPUs, 2 GB RAM) ou `t3.medium` (2 vCPUs, 4 GB RAM).
  * *Por que?* O ambiente rodará a Java Virtual Machine (Spring Boot), o banco de dados PostgreSQL e o NodeJS do Evolution API de forma simultânea. Para produção, 2 GB a 4 GB de RAM garantem excelente estabilidade.
* **Sistema Operacional:** `Ubuntu Server 22.04 LTS` (64-bit x86).
* **Armazenamento:** Mínimo de `20 GB SSD gp3` (garante espaço suficiente para o SO, imagens do Docker e banco de dados).

### 2. Configuração do Security Group (Firewall da AWS)
Configure as seguintes regras de entrada (Inbound Rules) no console da AWS:

| Tipo | Protocolo | Porta | Origem | Objetivo |
| :--- | :--- | :--- | :--- | :--- |
| **SSH** | TCP | `22` | Seu IP pessoal | Acesso terminal seguro |
| **HTTP** | TCP | `80` | `0.0.0.0/0` | Nginx (redirecionamento SSL) |
| **HTTPS** | TCP | `443` | `0.0.0.0/0` | Acesso seguro e Webhooks (SSL Let's Encrypt) |

*(Nota: Deixe as portas internas do Postgres e Evolution fechadas para o mundo externo. Todo o tráfego passará pelo Nginx reverso na porta 443).*

---

### 3. Preparação do Servidor (Terminal SSH)

Conecte-se na sua instância EC2 e execute os comandos para instalar o Docker, Docker Compose e o JDK 21:

```bash
# 1. Atualizar pacotes do sistema
sudo apt update && sudo apt upgrade -y

# 2. Instalar dependências necessárias
sudo apt install -y curl apt-transport-https ca-certificates gnupg lsb-release

# 3. Instalar o Docker oficial
curl -fsSL https://get.docker.com | sudo bash
sudo usermod -aG docker $USER
newgrp docker

# 4. Instalar o Docker Compose v2
sudo apt install -y docker-compose-plugin

# 5. Instalar o OpenJDK 21
sudo apt install -y openjdk-21-jdk
```

---

### 4. Deploy da Aplicação no Servidor

1. Crie uma pasta para o projeto no servidor:
   ```bash
   mkdir -p ~/barberbot && cd ~/barberbot
   ```
2. Transfira seus arquivos (`docker-compose.yml`, `application.yml`, `.env` e o arquivo `.jar` compilado) para o servidor usando `git clone` ou via comando `scp` local:
   ```bash
   # Exemplo de comando SCP (rodar no seu computador local):
   scp -i sua-chave.pem -r ./* ubuntu@ip-da-sua-ec2:~/barberbot
   ```
3. No servidor, certifique-se de preencher o arquivo `.env` com a sua `OPENAI_API_KEY` de produção e o seu número administrador.
4. Suba o banco de dados e a Evolution API no servidor:
   ```bash
   docker compose up -d
   ```

---

### 5. Compilação e Execução do Spring Boot em Produção

Para rodar o backend Java em segundo plano como um serviço do sistema (systemd), garantindo que ele reinicie automaticamente caso o servidor sofra queda de energia:

1. Compile o projeto localmente (com o Maven que instalamos globalmente):
   ```bash
   mvn clean package -DskipTests
   ```
   *(Isso gerará o arquivo `barberbot-assist-1.0.0.jar` dentro da pasta `target/`)*.

2. Envie o arquivo `.jar` para o servidor.
3. Crie um arquivo de serviço do Linux:
   ```bash
   sudo nano /etc/systemd/system/barberbot.service
   ```
4. Cole o seguinte conteúdo (ajustando os caminhos se necessário):
   ```ini
   [Unit]
   Description=BarberBot AI Backend Service
   After=network.target

   [Service]
   User=ubuntu
   WorkingDirectory=/home/ubuntu/barberbot
   # Carrega o .env antes de rodar o Java
   ExecStart=/usr/bin/env $(cat /home/ubuntu/barberbot/.env | grep -v '^#' | xargs) /usr/bin/java -jar /home/ubuntu/barberbot/target/barberbot-assist-1.0.0.jar
   SuccessExitStatus=143
   Restart=always
   RestartSec=10

   [Install]
   WantedBy=multi-user.target
   ```
5. Salve o arquivo (`Ctrl + O`, depois `Ctrl + X`) e inicie o serviço:
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable barberbot.service
   sudo systemctl start barberbot.service
   ```

---

### 6. Configuração de Domínio, Nginx Reverso e HTTPS (SSL Grátis)

WhatsApp exige conexões criptografadas **HTTPS (SSL)** para webhooks estáveis em produção. Mapeie dois subdomínios no seu DNS (ex: `bot.seudominio.com` apontando para o IP da EC2, e `evolution.seudominio.com` apontando para o mesmo IP).

1. Instale o Nginx e o Certbot no Ubuntu:
   ```bash
   sudo apt install -y nginx certbot python3-certbot-nginx
   ```
2. Configure o Nginx para encaminhar o tráfego com segurança:
   ```bash
   sudo nano /etc/nginx/sites-available/barberbot
   ```
3. Cole a configuração de proxy reverso:
   ```nginx
   server {
       listen 80;
       server_name bot.seudominio.com;

       location / {
           proxy_pass http://127.0.0.1:8081; # Backend Spring Boot
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }
   }

   server {
       listen 80;
       server_name evolution.seudominio.com;

       location / {
           proxy_pass http://127.0.0.1:8080; # Gateway Evolution API
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }
   }
   ```
4. Ative a configuração e reinicie o Nginx:
   ```bash
   sudo ln -s /etc/nginx/sites-available/barberbot /etc/nginx/sites-enabled/
   sudo nginx -t
   sudo systemctl restart nginx
   ```
5. Gere os certificados de segurança SSL grátis Let's Encrypt com renovação automática:
   ```bash
   sudo certbot --nginx -d bot.seudominio.com -d evolution.seudominio.com
   ```
   *(Siga os passos na tela informando seu e-mail e aceitando os termos).*

6. **Pronto!** Agora seu sistema está 100% acessível via HTTPS:
   * **Seu painel Evolution:** `https://evolution.seudominio.com/manager`
   * **Seu webhook oficial:** `https://bot.seudominio.com/api/webhook` *(Use esta URL segura ao configurar o webhook no Evolution Manager!)*

---
*BarberBot Assist AI — Pronto para rodar em produção de alto nível, com segurança SSL e resiliência AWS.* 💈✂️🤖
