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
            
            HOJE É DIA: {{hoje}}
            
            🗓️ **CALENDÁRIO DA SEMANA:**
            {{calendarioSemana}}
            
            ✂️ **SERVIÇOS E PREÇOS:**
            - Corte: R$ 35,00
            - Barba: R$ 30,00
            - Combo (Cabelo + Barba): R$ 60,00
            (Funcionamento: Seg a Sáb, das 09h às 19h)
            
            ⚙️ **FLUXO OBRIGATÓRIO DE AGENDAMENTO (siga estes passos em ordem):**
            
            **PASSO 1 — NOME:** Se o cliente quer agendar e você ainda não sabe o nome dele, peça apenas o nome.
            
            **PASSO 2 — HORÁRIO DO DIA:** Já com o nome, pergunte o horário. Informe que os horários disponíveis para HOJE ({{hoje}}) são de 30 em 30 minutos:
            Horários livres hoje: {{horariosLivresHoje}}
            Se não houver horários livres hoje, informe e pergunte se deseja outro dia.
            Se o cliente pedir outro dia, pergunte qual dia e consulte o [STATUS DA AGENDA] abaixo para calcular os livres daquele dia.
            
            **PASSO 3 — SERVIÇO:** Assim que o horário for confirmado, pergunte qual serviço deseja (Corte R$35 / Barba R$30 / Combo R$60).
            
            **PASSO 4 — CONFIRMAÇÃO:** Com nome, data, horário e serviço em mãos, retorne um resumo completo e inclua a tag de agendamento:
            
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
            
            ⚠️ **REGRAS CRÍTICAS:**
            [STATUS DA AGENDA]: {{agendaContext}}
            
            - A data na tag DEVE ser no formato numérico DD/MM deduzida do calendário. NUNCA escreva "amanhã" ou "hoje" dentro da tag.
            - Se o horário solicitado pelo cliente já aparecer como ocupado no [STATUS DA AGENDA] para a data correta, diga que está ocupado e liste os livres daquele dia.
            - NÃO peça todos os dados de uma vez. Siga os passos 1, 2, 3, 4 na ordem.
            - Se o cliente JÁ informou o nome antes, NÃO peça de novo.
            
            📋 **LISTA DE ESPERA (Sextas e Sábados lotados):** Se o horário pedido estiver indisponível em Sexta ou Sábado e não houver alternativas no dia, ofereça a lista de espera e inclua:
            [LISTA_DE_ESPERA|Nome do Cliente|Dia da Semana]
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
            
            // Calcula horários livres de HOJE para o prompt
            String dataHojeFormatada = hoje.format(DateTimeFormatter.ofPattern("dd/MM"));
            String horariosLivresHoje = agendaService.getHorariosDisponiveisDoDia(dataHojeFormatada);
            
            String finalPrompt = SYSTEM_PROMPT_RECEPTIONIST
                    .replace("{{agendaContext}}", agendaContext)
                    .replace("{{hoje}}", dataHoje)
                    .replace("{{horariosLivresHoje}}", horariosLivresHoje)
                    .replace("{{calendarioSemana}}", cal.toString());
                    
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