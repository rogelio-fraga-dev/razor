package com.barberbot.api.service;

import com.barberbot.api.config.BarberBotProperties;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class OpenAIService {
    
    private final OpenAiChatModel chatModel;
    private final BarberBotProperties properties;
    private final AgendaService agendaService;
    private WebClient openAiWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT_RECEPTIONIST = """
            Você é a recepcionista virtual da "Barbearia Ficticia". Seja simpática, objetiva e eficiente.
            Responda SEMPRE em português brasileiro. Use formatação *negrito* (um asterisco) e _itálico_ (underscore), pois estamos no WhatsApp.
            
            HOJE É DIA: {{hoje}}
            
            🗓️ *CALENDÁRIO DA SEMANA:*
            {{calendarioSemana}}
            
            ✂️ *SERVIÇOS E PREÇOS:*
            - Corte: R$ 35,00
            - Barba: R$ 30,00
            - Combo (Cabelo + Barba): R$ 60,00
            (Funcionamento: Seg a Sáb, das 09h às 19h)
            
            ⚙️ *FLUXO OBRIGATÓRIO DE AGENDAMENTO (siga estes passos em ordem):*
            
            *PASSO 1 — NOME:* 
            Você está falando com o número de telefone do cliente. O nome salvo dele no sistema é: "{{knownCustomerName}}".
            - Se o nome salvo for "Desconhecido", ou apenas um primeiro nome genérico (ex: "Matheus"), peça de forma muito educada o *NOME E SOBRENOME* para o agendamento não ficar duplicado no sistema.
            - Se o nome salvo já for um nome e sobrenome completo ou válido para o negócio, *PULE este passo e o chame pelo nome*. NUNCA pergunte o nome novamente se já tiver.
            
            *PASSO 2 — HORÁRIO:* Já sabendo o nome completo, pergunte o dia e horário que o cliente prefere.
            - *REGRA DE OURO:* NUNCA liste todos os horários disponíveis de uma vez, isso polui a tela.
            - *REGRA OBRIGATÓRIA:* A agenda só abre *de semana em semana*. O cliente só pode agendar para dias da semana atual (Segunda a Sábado, sem domingo).
            - Ao perguntar o horário, informe em *negrito* que a *agenda abre semanalmente* e apenas os dias dessa semana estão disponíveis.
            - Consulte os horários livres de hoje aqui: {{horariosLivresHoje}}. Para outros dias, consulte o [STATUS DA AGENDA].
            - Se o cliente sugerir um horário que está LIVRE, passe para o próximo passo.
            - Se o horário sugerido estiver OCUPADO, sugira *apenas 2 opções próximas* que estejam livres (ex: 30 min antes ou depois).
            - Se ele pedir uma data para a próxima semana, informe que a agenda só abre na própria semana.
            
            *PASSO 3 — SERVIÇO:* Assim que o horário for confirmado, pergunte qual serviço deseja (Corte R$35 / Barba R$30 / Combo R$60). Informe brevemente que se ele quiser desistir, basta dizer "cancelar", ou digitar "menu" para ir ao início.
            
            *PASSO 4 — CONFIRMAÇÃO:* Com nome, data, horário e serviço em mãos, retorne um resumo completo e inclua a tag de agendamento.
            
            Exemplo de resposta no Passo 4:
            "Perfeito! Confirmando seu agendamento: ✅
            👤 Nome: João
            📅 Data: 26/05
            ⏰ Horário: 14:00
            ✂️ Serviço: Corte — R$ 35,00
            
            Como deseja pagar?
            1️⃣ PIX 💳
            2️⃣ Presencialmente 💵"
            [NOVO_AGENDAMENTO|João|26/05|14:00|Corte]
            
            🚫 *CANCELAMENTO VIA CONVERSA:*
            Se o cliente disser que quer cancelar, desmarcar ou que não vai poder ir, inclua na sua resposta:
            [CANCELAR_AGENDAMENTO]
            E confirme que o agendamento foi desmarcado com sucesso.
            
            🚫 *BLOQUEIO ABSOLUTO DE DUPLICATAS (ERRO CRÍTICO):*
            NUNCA emita a tag [NOVO_AGENDAMENTO|...] mais de uma vez para o mesmo serviço.
            Se o cliente enviar mensagens curtas de encerramento ou agradecimento como "obrigado", "valeu", "tmj", "ok", "perfeito", "blz", você DEVE APENAS responder cordialmente. É ESTRITAMENTE PROIBIDO incluir a tag de agendamento novamente nessas respostas de agradecimento.
            
            ⚠️ *REGRAS CRÍTICAS:*
            [STATUS DA AGENDA]: {{agendaContext}}
            
            - A data na tag DEVE ser no formato numérico DD/MM deduzida do calendário. NUNCA escreva "amanhã" ou "hoje" dentro da tag.
            - Se o horário solicitado já aparecer como ocupado no [STATUS DA AGENDA] para a data correta, diga que está ocupado e liste os livres daquele dia.
            - *IMPORTANTE SOBRE HORÁRIOS:* Quando for listar os horários livres, NUNCA envie uma lista gigante. Ofereça no MÁXIMO 3 ou 4 opções espalhadas pelo dia (ex: "Tenho livre às 09:00, 14:00 e 16:30"). Se o cliente pedir outras opções, aí sim você lista mais.
            - NÃO peça todos os dados de uma vez. Siga os passos 1, 2, 3, 4 na ordem.
            - Se o cliente JÁ informou o nome antes (no histórico), NÃO peça de novo.
            - Se o cliente quiser ver seus agendamentos, perguntar sobre endereço, preços, fidelidade ou falar com atendente, responda diretamente sem tags de agendamento.
            
            📋 *LISTA DE ESPERA (Sextas e Sábados lotados):* Se o horário pedido estiver indisponível em Sexta ou Sábado e não houver alternativas no dia, ofereça a lista de espera e inclua:
            [LISTA_DE_ESPERA|Nome do Cliente|Dia da Semana]
            """;

    
    private static final String SYSTEM_PROMPT_AGENDA_READER = """
            Você é um assistente especializado em ler imagens de agenda. Retorne APENAS um JSON válido:
            {"items": [{"date": "26/02", "name": "Adilson", "time": "14:30", "service": "Corte"}]}
            """;

    public String processCustomerMessage(String userMessage, List<String> recentHistory, String agendaContext, int activeAppointments, String knownCustomerName) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
            String dataHoje = hoje.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            
            StringBuilder cal = new StringBuilder();
            DateTimeFormatter fmtDia = DateTimeFormatter.ofPattern("dd/MM (EEEE)", new Locale("pt", "BR"));
            for(int i = 0; i < 7; i++) {
                cal.append("- ").append(hoje.plusDays(i).format(fmtDia)).append("\n");
            }
            
            // Calcula horários livres de HOJE para o prompt
            String dataHojeFormatada = hoje.format(DateTimeFormatter.ofPattern("dd/MM"));
            String horariosLivresHoje = agendaService.getHorariosDisponiveisDoDia(dataHojeFormatada);
            
            String finalPrompt = SYSTEM_PROMPT_RECEPTIONIST
                    .replace("{{agendaContext}}", agendaContext)
                    .replace("{{hoje}}", dataHoje)
                    .replace("{{horariosLivresHoje}}", horariosLivresHoje)
                    .replace("{{calendarioSemana}}", cal.toString())
                    .replace("{{knownCustomerName}}", knownCustomerName);
                    
            if (activeAppointments >= 2) {
                finalPrompt += "\n\n⚠️ LIMITE ATINGIDO: Este cliente já possui " + activeAppointments + " agendamentos ativos na semana. É PROIBIDO oferecer horários ou marcar mais agendamentos. Diga de forma educada que ele atingiu o limite de 2 agendamentos semanais. Caso queira marcar outro, peça para ele primeiro cancelar um dos atuais pelo menu.";
            }
                    
            messages.add(SystemMessage.from(finalPrompt));
            
            // O histórico vem em ordem DESC do banco (mais recente primeiro).
            // Precisamos reverter para ordem cronológica ASC antes de montar as mensagens.
            // O último item do histórico ASC é a mensagem atual do usuário (já salva no banco).
            // Por isso NÃO adicionamos userMessage separadamente para evitar duplicação.
            if (recentHistory != null && !recentHistory.isEmpty()) {
                // Reverte: banco retorna DESC, precisamos ASC
                List<String> chronological = new ArrayList<>(recentHistory);
                java.util.Collections.reverse(chronological);
                
                // O último item já é a mensagem atual do usuário — não adicionar de novo
                // Montamos o histórico alternando USER/BOT na ordem correta
                for (int i = 0; i < chronological.size(); i++) {
                    if (i % 2 == 0) messages.add(UserMessage.from(chronological.get(i)));
                    else messages.add(AiMessage.from(chronological.get(i)));
                }
            } else {
                // Sem histórico — adiciona só a mensagem atual
                messages.add(UserMessage.from(userMessage));
            }
            
            return chatModel.generate(messages).content().text();
        } catch (Exception e) {
            log.error("Erro Chat: {}", e.getMessage());
            return "Opa, deu um pequeno erro no meu sistema. Pode repetir?";
        }
    }
    
    public String extractAgendaFromImage(String base64Image, String mimeType) {
        try {
            String pureBase64 = base64Image.contains(",") ? base64Image.split(",")[1] : base64Image;
            String cleanMime = (mimeType != null && mimeType.contains("image/")) ? mimeType.split(";")[0] : "image/jpeg";
            
            UserMessage userMessage = UserMessage.from(
                TextContent.from("Extraia os agendamentos desta imagem para JSON."),
                ImageContent.from(pureBase64, cleanMime) 
            );
            SystemMessage systemMessage = SystemMessage.from(SYSTEM_PROMPT_AGENDA_READER);
            String response = chatModel.generate(systemMessage, userMessage).content().text();
            return response.replace("```json", "").replace("```", "").trim();
        } catch (Exception e) {
            log.error("Erro Visão Computacional: {}", e.getMessage());
            throw new RuntimeException("Falha ao ler imagem.", e);
        }
    }
    
    public String transcribeAudio(String base64Audio, String mimeType) {
        try {
            String pureBase64 = base64Audio.contains(",") ? base64Audio.split(",")[1] : base64Audio;
            byte[] audioBytes = Base64.getDecoder().decode(pureBase64);
            
            String extension = "ogg"; 
            if (mimeType != null) {
                if (mimeType.contains("mp4")) extension = "mp4";
                else if (mimeType.contains("mpeg") || mimeType.contains("mp3")) extension = "mp3";
            }
            final String filename = "audio." + extension;
            
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(audioBytes) {
                @Override public String getFilename() { return filename; }
            });
            builder.part("model", "whisper-1");

            String jsonResponse = getOpenAiWebClient()
                    .post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(builder.build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (jsonResponse != null) {
                JsonNode root = objectMapper.readTree(jsonResponse);
                if (root.has("text")) {
                    return root.get("text").asText().trim();
                }
            }
            return jsonResponse;
        } catch (Exception e) {
            log.error("Erro Áudio Whisper: {}", e.getMessage());
            return "[Erro na transcrição do áudio]";
        }
    }

    private WebClient getOpenAiWebClient() {
        if (openAiWebClient == null) {
            openAiWebClient = WebClient.builder()
                    .baseUrl("https://api.openai.com/v1")
                    .defaultHeader("Authorization", "Bearer " + properties.getOpenai().getApiKey())
                    .build();
        }
        return openAiWebClient;
    }
}