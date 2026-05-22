# 🛠️ Guia de Inicialização do BarberBot AI (Passo a Passo)

Este documento descreve todos os passos necessários para ligar o sistema do absoluto zero e colocá-lo para funcionar. Siga a ordem abaixo para não esquecer nada!

---

## 📋 Checklist de Inicialização Rápida

- [ ] **Passo 1**: Subir os serviços Docker (PostgreSQL e Evolution API).
- [ ] **Passo 2**: Configurar as variáveis no arquivo `.env`.
- [ ] **Passo 3**: Ligar a aplicação Spring Boot (via script ou IDE).
- [ ] **Passo 4**: Criar a instância do WhatsApp na Evolution API.
- [ ] **Passo 5**: Configurar o Webhook de integração na Evolution API.
- [ ] **Passo 6**: Escanear o QR Code no celular para conectar o WhatsApp.

---

## 📖 Descrição Detalhada dos Passos

### 🐳 Passo 1: Subir os Serviços Externos (Docker)
O banco de dados e o gateway do WhatsApp rodam no Docker. Abra o terminal na raiz do projeto e execute:

```bash
docker-compose up -d
```
> [!NOTE]
> O parâmetro `-d` faz o Docker rodar em segundo plano. Certifique-se de que os containers do PostgreSQL e da Evolution API estão com status `Running` no Docker Desktop.

---

### 🔑 Passo 2: Configurar o Arquivo `.env`
Crie ou edite o arquivo chamado `.env` (exatamente com esse nome) na raiz do projeto contendo as seguintes configurações de ambiente:

```env
# 1. Configurações de Conexão com o PostgreSQL
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/barberbot_db
SPRING_DATASOURCE_USERNAME=barberbot_user
SPRING_DATASOURCE_PASSWORD=barberbot_pass

# 2. Credenciais de APIs Externas
OPENAI_API_KEY=sua_chave_da_openai_aqui
ADMIN_PHONE=55XXXXXXXXXXX # Seu número de WhatsApp com 55 e DDD (ex: 5511999999999)
EVOLUTION_API_KEY=barberbot # Chave definida no docker-compose da Evolution API
```

---

### ☕ Passo 3: Iniciar o Spring Boot (Backend)
Com o banco ativo e o `.env` preenchido, ligue a aplicação principal:

* **Opção A (PowerShell - Recomendado)**:
  Abra o PowerShell na raiz do projeto e execute:
  ```powershell
  ./run.ps1
  ```

* **Opção B (IDE - VS Code ou IntelliJ)**:
  Abra a classe principal `src/main/java/com/barberbot/BarberBotApplication.java` e clique no botão **Run** (Executar método main).

> [!IMPORTANT]
> Na primeira execução, o **Flyway** criará de forma automática todas as tabelas (incluindo fidelidade e lista de espera) no banco. Acompanhe os logs no console para confirmar se iniciou na porta **8081** sem erros.

---

### 📲 Passo 4: Criar a Instância na Evolution API
Precisamos criar o perfil de conexão com o WhatsApp na Evolution API.
1. Abra o **Evolution Manager** no seu navegador: `http://localhost:8080` (ou utilize o Postman).
2. Crie uma nova Instância com as informações:
   * **Instance Name**: `BarberBotOficial` (Exatamente como configurado no `application.yml`).
   * **Integration**: `WHATSAPP-BAILEYS`
   * **Token/ApiKey**: O token gerado para segurança da sua instância.

---

### 🔗 Passo 5: Configurar o Webhook na Instância
Para que a Evolution envie as mensagens recebidas de volta ao Spring Boot, configure o Webhook dentro do Manager ou via API:

1. Acesse as configurações da instância `BarberBotOficial` no Manager.
2. Ative a seção **Webhook**.
3. Configure a **URL do Webhook** apontando para o seu backend local:
   ```
   http://host.docker.internal:8081/api/webhook
   ```
   *(Ou `http://localhost:8081/api/webhook` caso esteja rodando fora de container)*.
4. Marque os eventos que deseja escutar:
   * [x] **`messages.upsert`** / **`MESSAGES_UPSERT`** (Garante o envio e leitura das conversas).
   * [x] **`qrcode.updated`** / **`QRCODE_UPDATED`** (Garante a sincronização de conexão).

---

### 📷 Passo 6: Conectar o WhatsApp (Sincronizar QR Code)
Agora, conecte o chip de teste:

1. No Evolution Manager, clique em **Connect** na instância `BarberBotOficial`.
2. O QR Code será gerado na tela. Abra o WhatsApp no celular, vá em **Aparelhos Conectados > Conectar um aparelho** e escaneie o código.
3. **Bug do Manager fallback**: Se o painel do Evolution Manager travar e não exibir o QR Code, abra no seu navegador:
   ```
   http://localhost:8081/api/webhook/qrcode
   ```
   *(Nosso backend intercepta o QR Code enviado via webhook, salva localmente como `qrcode-evolution.png` e exibe nessa URL para facilitar a sua vida!)*

---

## 📈 Verificação de Sucesso

Para ter certeza de que tudo está rodando perfeitamente:
1. Acesse a URL no navegador: `http://localhost:8081/api/webhook/health`. A resposta deve ser: `"BarberBot Assist está online!"`.
2. Mande um `"Oi"` ou `"Menu"` do celular do cliente para o número conectado. O bot deve responder com o menu interativo contendo o botão **"Ver Opções 📋"**.
3. Acesse o console e confirme se a aplicação Spring Boot está registrando logs no console.

*Tudo pronto! Seu MVP está inicializado e pronto para operar.* ✂️💈
