# 💈 BarberBot AI - Sistema de Agendamento Autônomo com GPT-4o

O **BarberBot** é um SaaS (Software as a Service) completo construído em Spring Boot, projetado para revolucionar a forma como barbearias, salões e clínicas gerenciam suas agendas. Integrado ao WhatsApp e alimentado por Inteligência Artificial (OpenAI), ele atua como uma recepcionista virtual 24/7 capaz de conversar humanamente, negociar horários e realizar agendamentos de forma 100% autônoma.

Este projeto foi construído do zero com foco em arquitetura limpa, processamento assíncrono e resiliência de banco de dados.

---

## 🚀 Principais Funcionalidades

### 🤖 Para o Cliente (Interface Inteligente)

- **Menu de Autoatendimento:** Resposta imediata a saudações com um menu interativo (Localização, Preços, Redes Sociais).
- **Agendamento por IA (NLP):** O cliente não precisa preencher formulários engessados. Ele manda um áudio ou digita "Quero cortar sexta às 15h" e a IA verifica a disponibilidade, negocia alternativas caso esteja ocupado, e salva no banco de dados.
- **Gestão de Agendamento:** O próprio cliente pode consultar ou cancelar seu horário diretamente pelo chat, sem intervenção humana.
- **Lembretes e Pós-Venda Automáticos:** O sistema envia uma notificação automática 1 hora antes do corte e, 40 minutos após o término, solicita uma avaliação no Google Meu Negócio.

### 🛠️ Para o Administrador (Mini-CRM no WhatsApp)

- **Dashboard Financeiro (CRON):** Resumo diário disparado automaticamente mostrando o total de clientes atendidos e o faturamento estimado do dia.
- **Disparo de Mensagens em Massa:** Capacidade de enviar _Avisos_ ou _Promoções do Dia_ para toda a base de clientes cadastrada no banco de dados, respeitando o limite de taxa do WhatsApp.
- **Transbordo Humano Inteligente:** Se o cliente exigir falar com o dono, a IA realiza uma "triagem", pergunta o motivo, pausa o próprio atendimento por 15 minutos e envia um alerta ao administrador.
- **CRUD Via Chat:** O administrador consegue listar e excluir clientes do banco de dados (PostgreSQL) usando apenas comandos no WhatsApp.

---

## 🏗️ Arquitetura e Tecnologias Utilizadas

O sistema foi arquitetado priorizando a separação de responsabilidades (Controllers, Services e Repositories).

- **Java 17+ & Spring Boot 3:** Core da aplicação (API REST).
- **Spring Data JPA & PostgreSQL:** Persistência relacional. Proteção rigorosa contra anomalias com integridade referencial (`Foreign Keys` e `@Transactional`).
- **LangChain4j & OpenAI (GPT-4o / Whisper):** Processamento de Linguagem Natural (NLP) dinâmico, alimentado por um prompt de sistema contextual que se atualiza com um calendário gerado em tempo real e o estado da memória da agenda. Visão computacional para leitura de imagens e Whisper para transcrição de áudios.
- **Evolution API:** Gateway de comunicação com o WhatsApp via Webhooks assíncronos.
- **Spring Scheduling (@Scheduled):** CRON Jobs responsáveis pelas rotinas de lembretes, monitoramento temporal de agenda e relatórios de fechamento de caixa.

---

## 🧠 Como a "Mágica" do Agendamento Funciona (Under The Hood)

1. A classe `AgendaService` mapeia todos os horários ocupados e os formata em uma String.
2. A classe `OpenAIService` gera os próximos 7 dias em tempo real (`dd/MM (EEEE)`).
3. Ambos os contextos são injetados no **System Prompt** da IA durante a chamada.
4. A IA negocia com o cliente. Quando os requisitos são preenchidos (Nome, Data, Hora e Serviço), a IA gera uma tag invisível ao usuário: `[NOVO_AGENDAMENTO|João|04/03|14:00|Corte]`.
5. O `OrchestratorService` intercepta essa string via Regex/Substring, remove do texto, salva no banco e confirma a transação em milissegundos.

---

## 👨‍💻 Sobre o Desenvolvedor

Este sistema foi desenvolvido como uma prova de conceito de minhas habilidades em **Integração de Sistemas, Clean Code e Automação de Processos com Inteligência Artificial**.

Sinta-se à vontade para explorar o código, clonar e entrar em contato para discutirmos soluções de software!
