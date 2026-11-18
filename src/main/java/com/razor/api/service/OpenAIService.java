package com.razor.api.service;

import com.razor.api.config.RazorProperties;
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
    private final RazorProperties properties;
    private final AgendaService agendaService;
    private final BarbershopSettingsService barbershopSettingsService;
    private final com.razor.api.repository.ServiceOfferingRepository serviceOfferingRepository;
    private final PlanPricingService planPricingService;
    private final PackageService packageService;
    private WebClient openAiWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DEFAULT_PERSONA = "Seja simpática, objetiva e eficiente.";

    private static final String SYSTEM_PROMPT_RECEPTIONIST = """
            Você é a recepcionista virtual da "{{nomeBarbearia}}". {{persona}}
            Responda SEMPRE em português brasileiro. Use formatação *negrito* (um asterisco) e _itálico_ (underscore), pois estamos no WhatsApp.

            HOJE É DIA: {{hoje}}

            🗓️ *CALENDÁRIO DA SEMANA:*
            {{calendarioSemana}}

            ✂️ *SERVIÇOS E PREÇOS:*
            {{tabelaServicos}}
            (Funcionamento: {{funcionamento}})

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
            
            *PASSO 3 — SERVIÇO:* Assim que o horário for confirmado, pergunte qual serviço deseja (use APENAS os serviços e preços da tabela acima — nunca invente valores). Informe brevemente que se ele quiser desistir do atendimento, basta dizer "cancelar".
            
            *PASSO 4 — PEDIR CONFIRMAÇÃO:* Com nome, data, horário e serviço em mãos, retorne um resumo completo e PERGUNTE de forma clara se o cliente confirma o agendamento.
            NÃO emita a tag de agendamento neste passo.
            Exemplo: "Perfeito! Você quer agendar para Dia 26/05 às 14:00 para Corte? Responda SIM para eu confirmar."
            
            *PASSO 5 — AGENDAR E FINALIZAR:* APENAS quando o cliente responder "Sim", "Pode", "Confirmo", ou concordar explicitamente com o Passo 4, você emite a tag de agendamento e pergunta a forma de pagamento.
            Exemplo:
            "Agendamento Confirmado! ✅
            
            Como deseja pagar?
            1️⃣ PIX 💳
            2️⃣ Presencialmente 💵"
            [NOVO_AGENDAMENTO|João|26/05|14:00|Corte]
            
            🚫 *CANCELAMENTO DE HORÁRIO VS ABORTO DE CONVERSA:*
            - SE O CLIENTE JÁ TEM UM AGENDAMENTO NA BASE e pedir para "cancelar o meu horário", "desmarcar o meu horário", inclua a tag [CANCELAR_AGENDAMENTO] na sua resposta.
            - SE O CLIENTE ESTÁ NO MEIO DO FLUXO (Passo 1 ao 4) apenas conversando e disser "cancelar", "deixa pra lá", "não quero mais", ele está abortando a conversa atual. NESSE CASO, NÃO USE NENHUMA TAG. Apenas responda "Sem problemas! O atendimento foi cancelado. Se precisar de algo, chame novamente."
            
            🚫 *BLOQUEIO ABSOLUTO DE DUPLICATAS (ERRO CRÍTICO):*
            NUNCA emita a tag [NOVO_AGENDAMENTO|...] mais de uma vez para a mesma intenção.
            Se o cliente enviar mensagens de encerramento como "obrigado", "valeu", "ok", APENAS responda cordialmente. É ESTRITAMENTE PROIBIDO emitir tags repetidas.
            
            ⚠️ *REGRAS CRÍTICAS:*
            [STATUS DA AGENDA]: {{agendaContext}}
            
            - A data na tag DEVE ser no formato numérico DD/MM. NUNCA escreva "amanhã" ou "hoje" na tag.
            - Se o horário estiver ocupado no [STATUS DA AGENDA], diga que está ocupado e liste no MÁXIMO 3 horários livres.
            - NÃO peça todos os dados de uma vez. Siga os passos 1, 2, 3, 4 e 5 um de cada vez.
            - Se o cliente JÁ informou o nome antes, NÃO peça de novo.
            - Se o cliente fizer perguntas soltas (endereço, preços), responda diretamente sem usar tags.
            
            📋 *LISTA DE ESPERA (Sextas e Sábados):* Se não houver horário livre e o cliente topar, ofereça a lista de espera e inclua:
            [LISTA_DE_ESPERA|Nome do Cliente|Dia da Semana]
            """;

    
    private static final String SYSTEM_PROMPT_AGENDA_READER = """
            Você é um assistente especializado em ler imagens de agenda. Retorne APENAS um JSON válido:
            {"items": [{"date": "26/02", "name": "Adilson", "time": "14:30", "service": "Corte"}]}
            """;

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /** Tabela de servicos do catalogo do tenant (nome, preco e duracao) para o prompt da IA. */
    // package-private: coberto por OpenAIServiceTest (garante que a IA nunca recite preco fixo).
    /**
     * A tabela de precos que vai para o prompt da IA, <b>ja com os beneficios do plano</b> do
     * cliente aplicados.
     *
     * <p>Sem o cliente, esta tabela levava o preco cheio do catalogo — e a IA e quem responde
     * "quanto custa?" na conversa. Resultado: quem paga R$ 119,90 por mes de plano ouvia o valor
     * integral do bot, e o beneficio so aparecia no menu numerado, que quase ninguem abre. O
     * motor de beneficios existia desde a V27 e nao estava ligado onde mais importa.
     *
     * <p>{@code customer} nulo (cliente novo, ainda sem cadastro) cai no preco cheio, que e o
     * correto: ele nao tem plano.
     */
    private String brl(java.math.BigDecimal v) {
        return String.format(new java.util.Locale("pt", "BR"), "%.2f", v);
    }

    String buildServiceTable(com.razor.api.model.Customer customer) {
        var catalog = serviceOfferingRepository.findByBarbershopIdAndActiveTrue(
                com.razor.api.tenant.TenantContext.getCurrent());
        if (catalog == null || catalog.isEmpty()) {
            return "- (catálogo não cadastrado — peça ao cliente que aguarde e avise o barbeiro)";
        }

        // Preco por servico ja resolvido pelo plano (desconto, cota do mes, dia da semana).
        var precos = new java.util.HashMap<String, PlanPricingService.PricedItem>();
        for (var item : planPricingService.priceCatalogFor(customer, LocalDate.now(ZoneId.of("America/Sao_Paulo")))) {
            precos.put(item.name(), item);
        }

        StringBuilder sb = new StringBuilder();
        for (var s : catalog) {
            sb.append("- ").append(s.getName());
            var comPlano = precos.get(s.getName());
            if (comPlano != null && (comPlano.isFree() || comPlano.discounted())) {
                // O preco cheio vai junto para a IA poder dizer "de X por Y" — e o que faz o
                // cliente PERCEBER o valor do plano que ele paga.
                sb.append(comPlano.isFree()
                        ? ": GRÁTIS pelo plano do cliente (valor normal R$ "
                                + brl(comPlano.originalPrice()) + ")"
                        : ": R$ " + brl(comPlano.finalPrice())
                                + " com o plano do cliente (valor normal R$ " + brl(comPlano.originalPrice()) + ")");
            } else if (s.getPrice() != null) {
                sb.append(": R$ ").append(String.format(new java.util.Locale("pt", "BR"), "%.2f", s.getPrice()));
            }
            if (s.getDurationMinutes() != null) {
                sb.append(" (").append(s.getDurationMinutes()).append(" min)");
            }
            sb.append("\n");
        }
        sb.append(buildPackageLines(customer));
        return sb.toString().trim();
    }

    /**
     * Pacotes pre-pagos (V34) do cliente, para a IA nao cobrar de novo um corte que ele ja
     * pagou. Sai uma linha por pacote usavel, com o saldo e a validade.
     */
    String buildPackageLines(com.razor.api.model.Customer customer) {
        if (packageService == null || customer == null) return "";
        var pacotes = packageService.usableFor(customer);
        if (pacotes == null || pacotes.isEmpty()) return "";
        var fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        StringBuilder sb = new StringBuilder();
        for (var cp : pacotes) {
            String cobre = cp.getServiceName() != null ? cp.getServiceName() : "qualquer serviço";
            sb.append("- PACOTE PRÉ-PAGO do cliente \"").append(cp.getPackageName()).append("\" (")
                    .append(cobre).append("): ").append(cp.getRemainingUses())
                    .append(cp.getRemainingUses() == 1 ? " uso restante" : " usos restantes")
                    .append(", válido até ").append(cp.getExpiresAt().format(fmt))
                    .append(". Já está pago — o atendimento coberto não é cobrado de novo.\n");
        }
        return sb.toString();
    }

    /** Ex.: "Seg a Sáb, das 09:00 às 19:00" — vindo das configuracoes do tenant. */
    String buildOpeningHours(BarbershopSettingsService.BotSettings cfg) {
        // Frase derivada dos dias estruturados (V32): antes vinha de um texto livre a parte, que
        // podia anunciar "Seg a Sáb" enquanto a agenda aceitava domingo.
        return cfg.workingDaysText() + ", das " + cfg.openingTime() + " às " + cfg.closingTime();
    }

    /**
     * Aviso de limite de horarios no prompt (V36), com o limite <b>configurado</b> da barbearia.
     * Antes o "2 por semana" estava cravado aqui e contava o historico inteiro do cliente. O
     * aviso so orienta a conversa: quem de fato barra e o {@code AgendaBookingService}.
     *
     * @param futureAppointments horarios futuros que o cliente ja tem
     * @param limit              limite configurado; nulo, zero ou negativo = sem limite
     */
    static String bookingLimitHint(int futureAppointments, Integer limit) {
        if (futureAppointments <= 0) return "";
        if (limit == null || limit <= 0) {
            return "\n\n⚠️ AVISO: O cliente já possui " + futureAppointments + " horário(s) futuro(s) marcado(s). "
                    + "Se for marcar outro, mencione isso sutilmente ('Vi que você já tem horário com a gente, vamos marcar mais esse!').";
        }
        if (futureAppointments >= limit) {
            return "\n\n⚠️ LIMITE ATINGIDO: Este cliente já possui " + futureAppointments + " horário(s) futuro(s) marcado(s) "
                    + "e o limite da barbearia é " + limit + " por cliente. É PROIBIDO oferecer horários ou marcar mais. "
                    + "Explique com gentileza, diga quais horários ele já tem e ofereça remarcar: para trocar, ele envia "
                    + "'cancelar meu horário', escolhe qual liberar e depois você marca o novo.";
        }
        return "\n\n⚠️ AVISO DE MULTIPLOS AGENDAMENTOS: O cliente já possui " + futureAppointments + " horário(s) futuro(s). "
                + "Avise-o sutilmente ('Vi que você já tem um horário marcado conosco, mas vamos marcar esse outro!'). "
                + "O limite da barbearia é " + limit + " horários futuros por cliente, então ele ainda pode agendar este.";
    }

    /**
     * @throws AiUnavailableException se a IA falhar (rede, cota, timeout) — nunca devolve um
     *         texto de erro disfarcado de resposta.
     */
    public String processCustomerMessage(String userMessage, List<com.razor.api.model.Interaction> recentHistory,String agendaContext, int activeAppointments, String knownCustomerName, com.razor.api.model.Customer customer) {
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
            
            // Identidade, catalogo e funcionamento vem do TENANT ATUAL — sem isso toda barbearia
            // teria um bot dizendo o nome e os precos de outra (bug multi-tenant).
            var cfg = barbershopSettingsService.resolve();

            String finalPrompt = SYSTEM_PROMPT_RECEPTIONIST
                    .replace("{{agendaContext}}", agendaContext)
                    .replace("{{hoje}}", dataHoje)
                    .replace("{{horariosLivresHoje}}", horariosLivresHoje)
                    .replace("{{calendarioSemana}}", cal.toString())
                    .replace("{{knownCustomerName}}", knownCustomerName)
                    .replace("{{nomeBarbearia}}", cfg.displayName())
                    .replace("{{persona}}", blankTo(cfg.botPersona(), DEFAULT_PERSONA))
                    .replace("{{tabelaServicos}}", buildServiceTable(customer))
                    .replace("{{funcionamento}}", buildOpeningHours(cfg));
                    
            finalPrompt += bookingLimitHint(activeAppointments,
                    barbershopSettingsService.getMaxFutureBookingsPerCustomer());
                    
            messages.add(SystemMessage.from(finalPrompt));
            
            messages.addAll(buildHistoryMessages(recentHistory, userMessage));

            return chatModel.generate(messages).content().text();
        } catch (Exception e) {
            // Nada de responder "deu um erro" como se fosse a IA: quem chama manda a mensagem de
            // contingencia (sem grava-la como fala do bot) e avisa o dono.
            log.error("[IA] Falha ao gerar resposta do chat", e);
            throw new AiUnavailableException("IA indisponivel: " + e.getMessage(), e);
        }
    }

    /**
     * Converte o historico do banco em mensagens do chat, em ordem cronologica e com o papel
     * vindo do TIPO da interacao (USER/BOT).
     *
     * <p>Antes o papel era decidido pela paridade do indice: com a janela de 10 mensagens o
     * primeiro item quase nunca era do cliente, entao os papeis saiam invertidos e a mensagem
     * atual do cliente ia para o modelo como fala do proprio assistente.
     *
     * <p>A ultima mensagem do cliente no historico e a atual (ja gravada antes de chamar a IA);
     * ela e substituida por {@code userMessage}, que pode ser a versao "traduzida" do menu
     * (ex.: "1" vira "Quero agendar um horário."). Se nao houver, a mensagem atual e anexada.
     */
    // package-private: coberto por OpenAIServiceTest.
    static List<ChatMessage> buildHistoryMessages(List<com.razor.api.model.Interaction> recentHistory,
                                                  String userMessage) {
        List<com.razor.api.model.Interaction> chronological =
                recentHistory == null ? new ArrayList<>() : new ArrayList<>(recentHistory);
        // O repositorio devolve DESC (mais recente primeiro). Inverte e depois ordena por
        // timestamp (ordenacao estavel: empates mantem a ordem invertida).
        java.util.Collections.reverse(chronological);
        chronological.sort(java.util.Comparator.comparing(com.razor.api.model.Interaction::getTimestamp,
                java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())));

        List<ChatMessage> out = new ArrayList<>();
        for (com.razor.api.model.Interaction i : chronological) {
            if (i == null || i.getContent() == null || i.getContent().isBlank() || i.getType() == null) continue;
            out.add(i.getType() == com.razor.api.model.Interaction.InteractionType.USER
                    ? UserMessage.from(i.getContent())
                    : AiMessage.from(i.getContent()));
        }

        boolean hasCurrent = userMessage != null && !userMessage.isBlank();
        if (!out.isEmpty() && out.get(out.size() - 1) instanceof UserMessage) {
            if (hasCurrent) out.set(out.size() - 1, UserMessage.from(userMessage));
        } else if (hasCurrent) {
            out.add(UserMessage.from(userMessage));
        }
        return out;
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
    
    /**
     * Transcreve um audio com o Whisper.
     * Retorna null quando a transcricao falha (chave invalida, rede, resposta vazia),
     * para o chamador nao tratar uma mensagem de erro como se fosse fala do usuario.
     */
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
            String whisperModel = properties.getOpenai().getWhisperModel();
            builder.part("model", (whisperModel == null || whisperModel.isBlank()) ? "whisper-1" : whisperModel);

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
                    String text = root.get("text").asText().trim();
                    return text.isEmpty() ? null : text;
                }
            }
            log.error("Erro Áudio Whisper: resposta sem campo 'text'");
            return null;
        } catch (Exception e) {
            log.error("Erro Áudio Whisper: {}", e.getMessage());
            return null;
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