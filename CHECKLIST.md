# 📋 Checklist Completo - BarberBot Assist

## ✅ Arquivos Criados e Implementados

### Infraestrutura Base
- [x] `pom.xml` - Configuração Maven com todas as dependências
- [x] `docker-compose.yml` - PostgreSQL + Evolution API
- [x] `src/main/resources/application.yml` - Configurações do Spring Boot
- [x] `README.md` - Documentação completa do projeto

### Banco de Dados
- [x] `src/main/resources/db/migration/V1__create_tables.sql` - Script de migração inicial
  - [x] Tabela `customers`
  - [x] Tabela `interactions`
  - [x] Tabela `scheduled_tasks`
  - [x] Índices otimizados

### Entidades JPA
- [x] `src/main/java/com/barberbot/api/model/Customer.java`
- [x] `src/main/java/com/barberbot/api/model/Interaction.java`
- [x] `src/main/java/com/barberbot/api/model/ScheduledTask.java`

### DTOs (Data Transfer Objects)
- [x] `src/main/java/com/barberbot/api/dto/EvolutionWebhookDTO.java` - Com métodos helper
- [x] `src/main/java/com/barberbot/api/dto/MessageDTO.java`
- [x] `src/main/java/com/barberbot/api/dto/AgendaDTO.java`

### Repositórios
- [x] `src/main/java/com/barberbot/api/repository/CustomerRepository.java`
- [x] `src/main/java/com/barberbot/api/repository/InteractionRepository.java`
- [x] `src/main/java/com/barberbot/api/repository/ScheduledTaskRepository.java`

### Services (Lógica de Negócio)
- [x] `src/main/java/com/barberbot/api/service/OrchestratorService.java` - Gerenciador central
- [x] `src/main/java/com/barberbot/api/service/CustomerService.java`
- [x] `src/main/java/com/barberbot/api/service/OpenAIService.java`
- [x] `src/main/java/com/barberbot/api/service/AgendaService.java`
- [x] `src/main/java/com/barberbot/api/service/WhatsAppService.java`

### Integração Externa
- [x] `src/main/java/com/barberbot/api/client/EvolutionClient.java` - Cliente REST para Evolution API
- [x] `src/main/java/com/barberbot/api/controller/WebhookController.java` - Endpoint de webhook

### Configuração
- [x] `src/main/java/com/barberbot/BarberBotApplication.java` - Classe principal
- [x] `src/main/java/com/barberbot/api/config/BarberBotProperties.java` - Propriedades customizadas
- [x] `src/main/java/com/barberbot/api/config/OpenAIConfig.java` - Configuração LangChain4j
- [x] `src/main/java/com/barberbot/api/config/WebClientConfig.java` - WebClient e ObjectMapper

### Agendamento
- [x] `src/main/java/com/barberbot/api/scheduler/TaskScheduler.java` - Processamento de tarefas agendadas

---

## ⚠️ Pendências Críticas (Para Funcionamento)

### 🔴 ALTA PRIORIDADE - Antes de Testar

#### 1. Configuração de API Key da OpenAI
- [x] Definir variável de ambiente `OPENAI_API_KEY` (via `.env` ou `run.ps1`)
- [ ] Testar se a API Key está válida e funcionando

#### 2. Configuração da Evolution API
- [x] Docker Compose rodando (PostgreSQL + Evolution API)
- [x] Acessar http://localhost:8080/manager (API Key: `barberbot`)
- [ ] Criar instância do WhatsApp no manager (nome sugerido: BarberBot)
- [ ] Escanear QR Code com WhatsApp na instância criada
- [ ] Verificar webhook global: `http://host.docker.internal:8081/api/webhook` (já no docker-compose)
- [ ] Testar se está recebendo webhooks corretamente

#### 3. Implementações Incompletas no Código

##### OpenAIService.java
- [ ] **IMPLEMENTAR**: Suporte completo para Vision API (GPT-4o Vision)
  - Atualmente tem placeholder
  - Precisa usar `OpenAiVisionModel` ou chamada direta à API
  - Arquivo: `src/main/java/com/barberbot/api/service/OpenAIService.java` linha ~115
  
- [ ] **IMPLEMENTAR**: Suporte para Whisper API (Transcrição de Áudio)
  - Atualmente tem placeholder
  - Precisa fazer chamada HTTP direta à API do Whisper
  - Arquivo: `src/main/java/com/barberbot/api/service/OpenAIService.java` linha ~140

##### OrchestratorService.java
- [ ] **IMPLEMENTAR**: Parser de comandos do admin
  - Comando "Disparo Geral" - enviar mensagem para todos os contatos
  - Comando "Listar Contatos" - retornar lista de clientes
  - Arquivo: `src/main/java/com/barberbot/api/service/OrchestratorService.java` linha ~120

---

## 🟡 Pendências Importantes (Melhorias)

### Menu (feito)
- [x] **Menu em texto** – opções 1 a 6 + Instagram; cliente digita número ou nome da opção (lista interativa deixada de lado)
- [x] **Configuração em `application.yml`** – `barberbot.menu` (endereço, serviços, link agendamento, Instagram)

### Configuração de Conteúdo
- [x] Respostas padrão por item do menu (via `barberbot.menu` no yml)
- [ ] **Configurar links reais** em `application.yml` (barberbot.menu)
  - Link do Google Maps da barbearia
  - Link do sistema de agendamento
  - Link do Instagram
  
- [ ] **Criar tabela de preços**
  - Adicionar preços reais dos serviços
  - Formato: "Corte: R$ XX,00"
  
- [ ] **Adicionar fotos dos produtos**
  - URLs ou arquivos das imagens
  - Integrar no menu de produtos

### Melhorias Técnicas
- [ ] **Tratamento de erros mais robusto**
  - Retry logic para chamadas à OpenAI
  - Fallback quando API estiver indisponível
  - Mensagens de erro amigáveis ao cliente
  
- [ ] **Validações adicionais**
  - Validar formato de telefone
  - Validar formato de JSON da agenda
  - Validar URLs de imagens
  
- [ ] **Logs e Monitoramento**
  - Adicionar mais logs detalhados
  - Métricas de uso (quando disponível)
  - Alertas para erros críticos

---

## 📋 Planejado: Excel CashBarber e Novos Clientes

### Lista de contatos do CashBarber (Excel)
- [ ] **Importar Excel com todos os contatos** do aplicativo CashBarber do administrador
  - Objetivo: evitar conflito entre “nome no agendamento” e “nome/contato salvo” – ao cruzar por **telefone**, o bot usa o mesmo cadastro (nome do CashBarber) para lembretes e mensagens do dia
  - Fluxo sugerido: endpoint (ex.: POST `/api/admin/import-contacts`) que recebe o Excel; ler colunas (telefone, nome); criar/atualizar `customers` por telefone; assim, agendamentos do dia e mensagens usam o mesmo cliente
- [ ] **Formato do Excel**: definir colunas (ex.: `telefone`, `nome`) e documentar para o administrador exportar do CashBarber no formato esperado

### Prospecção – novos clientes
- [ ] **Enviar mensagem para pessoas sem cadastro** (conhecer a barbearia)
  - Ex.: lista de números (ou Excel) de leads; o bot envia uma mensagem de apresentação + link do Instagram / agendamento
  - Pode ser um comando do admin: “disparo prospecção” + upload de Excel ou lista, ou uso da mesma planilha de contatos marcando “não cliente” para envio único

---

## 🟢 Pendências Opcionais (Futuras Melhorias)

### Funcionalidades Avançadas
- [ ] **Lembrete de Agendamento** - Enviar mensagem 1 hora antes do horário marcado
  - Modificar `AgendaService.processAgenda()` para criar 2 tarefas por agendamento:
    1. Tarefa de LEMBRETE (1 hora antes do horário) - taskType: "APPOINTMENT_REMINDER"
    2. Tarefa de AVALIAÇÃO (1 hora depois do horário) - taskType: "REVIEW_REQUEST"
  - Mensagem de lembrete: "Olá {nome}! Lembramos que você tem um agendamento hoje às {horário}. Esperamos você!"
  - Arquivo: `src/main/java/com/barberbot/api/service/AgendaService.java`
- [ ] Dashboard administrativo web
- [ ] Métricas e analytics de conversas
- [ ] Suporte para múltiplas instâncias/contas WhatsApp
- [ ] Backup automático do banco de dados
- [ ] Sistema de templates de mensagens
- [ ] Suporte para grupos do WhatsApp

### Testes
- [ ] Testes unitários dos Services
- [ ] Testes de integração do WebhookController
- [ ] Testes de integração com Evolution API (mocked)
- [ ] Testes de integração com OpenAI API (mocked)

---

## 📝 Resumo do que Precisa para Começar a Testar

### Antes de Rodar pela Primeira Vez:
1. ✅ Estrutura de código completa (FEITO)
2. ⚠️ Definir `OPENAI_API_KEY` (FAZER)
3. ⚠️ Iniciar Docker Compose (FAZER)
4. ⚠️ Configurar Evolution API e escanear QR Code (FAZER)
5. ⚠️ Implementar Vision API no OpenAIService (IMPORTANTE)
6. ⚠️ Implementar Whisper API no OpenAIService (IMPORTANTE)

### Para Funcionar Completamente:
- Implementar comandos do admin
- Adicionar conteúdo real (mensagens, links, preços)
- Testar fluxos end-to-end

---

## 🚀 Próximos Passos Recomendados

### Fase 1: Setup Básico (Hoje/Amanhã)
1. Obter API Key da OpenAI
2. Testar conexão com Evolution API
3. Implementar Vision API básica
4. Testar recebimento de webhook

### Fase 2: Funcionalidades Core (Esta Semana)
1. Implementar Whisper API
2. Implementar comandos do admin
3. Adicionar conteúdo real (mensagens, links)
4. Testar fluxo completo cliente → bot → resposta

### Fase 3: Refinamento (Próxima Semana)
1. Melhorar tratamento de erros
2. Adicionar validações
3. Otimizar performance
4. Documentar casos de uso

---

## 📞 Informações de Contato e Configuração

- **Número do Admin (Exemplo)**: 34999999999
- **Porta do Backend**: 8081
- **Porta da Evolution API**: 8080
- **Nome da Instância**: BarberBot

---

**Última atualização**: 27/01/2026
**Status Geral**: ✅ Estrutura Base Completa | ✅ Evolution API configurada (API Key: barberbot) | ⚠️ Próximo: criar instância WhatsApp e rodar aplicação
