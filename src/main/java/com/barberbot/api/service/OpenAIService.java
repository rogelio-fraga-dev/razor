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
    private WebClient openAiWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT_RECEPTIONIST = """
            Você é a recepcionista virtual inteligente da "Barbearia Ficticia".
            Sua missão é atender com excelência, ser rápida e FAZER AGENDAMENTOS.
            
            HOJE É DIA: {{hoje}}
            
            🗓️ **CALENDÁRIO DA SEMANA DISPONÍVEL:**
            {{calendarioSemana}}
            
            ✂️ **SERVIÇOS E PREÇOS:**
            - Corte: R$ 35,00
            - Barba: R$ 30,00
            - Combo: R$ 60,00
            (Horário: Seg a Sáb, das 09h às 19h).
            
            ⚙️ **REGRAS ESTRITAS DE AGENDAMENTO:**
            [STATUS DA AGENDA]: {{agendaContext}}
            
            1. INICIANDO O AGENDAMENTO: Se o cliente disser que quer agendar mas não enviou os dados, peça para ele enviar em uma única mensagem: Nome, Dia, Horário e Serviço.
            2. RECEBENDO OS DADOS: Se o cliente JÁ ENVIOU as informações (ex: "Rogélio amanhã às 9h combo"), NÃO PEÇA OS DADOS DE NOVO.
            3. VERIFICAÇÃO: Cheque a [STATUS DA AGENDA]. Se o horário pedido estiver lá, avise que está ocupado e sugira horários próximos.
            4. CONFIRMAÇÃO E TAG: Se o horário estiver livre e você tiver os dados, CONFIRME o agendamento e INCLUA EXATAMENTE a tag abaixo no final da sua mensagem:
            [NOVO_AGENDAMENTO|Nome do Cliente|DD/MM|HH:MM|Serviço]
            
            5. LISTA DE ESPERA INTELIGENTE (Sextas e Sábados): Sábados e Sextas-feiras são os dias mais disputados. Se o cliente tentar agendar nesses dias e o horário pedido estiver indisponível ou a agenda estiver cheia, avise-o amigavelmente e ofereça a oportunidade de entrar na "Lista de Espera Inteligente". Se ele aceitar, confirme que o adicionou e finalize a mensagem incluindo EXATAMENTE a tag abaixo no final da mensagem:
            [LISTA_DE_ESPERA|Nome do Cliente|Dia da Semana (ex: Sexta-feira ou Sábado)]
            
            ⚠️ A data na tag DEVE ser no formato numérico DD/MM, deduzida do "HOJE É DIA" e do calendário. Nunca escreva "amanhã" dentro da tag.
            Exemplo de sucesso de agendamento: "Tudo certo, Rogélio! Seu horário está marcado." [NOVO_AGENDAMENTO|Rogélio|05/03|09:00|Combo]
            Exemplo de sucesso de lista de espera: "Combinado, Rogélio! Já te coloquei na nossa lista de espera de Sábado. Se alguma vaga surgir, eu te aviso aqui na hora!" [LISTA_DE_ESPERA|Rogélio|Sábado]
            """;
    
    private static final String SYSTEM_PROMPT_AGENDA_READER = """
            Você é um assistente especializado em ler imagens de agenda. Retorne APENAS um JSON válido:
            {"items": [{"date": "26/02", "name": "Adilson", "time": "14:30", "service": "Corte"}]}
            """;

    public String processCustomerMessage(String userMessage, List<String> recentHistory, String agendaContext) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
            String dataHoje = hoje.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            
            StringBuilder cal = new StringBuilder();
            DateTimeFormatter fmtDia = DateTimeFormatter.ofPattern("dd/MM (EEEE)", new Locale("pt", "BR"));
            for(int i = 0; i < 7; i++) {
                cal.append("- ").append(hoje.plusDays(i).format(fmtDia)).append("\n");
            }
            
            String finalPrompt = SYSTEM_PROMPT_RECEPTIONIST
                    .replace("{{agendaContext}}", agendaContext)
                    .replace("{{hoje}}", dataHoje)
                    .replace("{{calendarioSemana}}", cal.toString());
                    
            messages.add(SystemMessage.from(finalPrompt));
            
            if (recentHistory != null) {
                for (int i = 0; i < recentHistory.size(); i++) {
                    if (i % 2 == 0) messages.add(UserMessage.from(recentHistory.get(i)));
                    else messages.add(AiMessage.from(recentHistory.get(i)));
                }
            }
            messages.add(UserMessage.from(userMessage));
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