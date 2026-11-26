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
    /** Sucesso/falha da IA por barbearia para o monitor de saude (V50). Nulo so em teste. */
    private final HealthSignals healthSignals;
    private final PricingService pricingService;
    /** Modelo mais forte (gpt-4o); nulo nos testes = sem escalonamento. */
    private final ChatEscalation chatEscalation;
    /** Chave/modelos trocaveis pelo console (V51); nulo nos testes = os beans do ambiente. */
    private final com.razor.api.platform.AiRuntime aiRuntime;
    /** Uso/custo da IA por barbearia (V51); nulo nos testes. */
    private final com.razor.api.platform.AiUsageRecorder usage;
    /** Barbeiros ativos, para o bot saber quem existe (pente fino de 25/11); nulo nos testes. */
    private final com.razor.api.repository.ProfessionalRepository professionalRepository;
    private WebClient openAiWebClient;
    private String openAiWebClientKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DEFAULT_PERSONA = "Seja simpática, objetiva e eficiente.";

    private static final String SYSTEM_PROMPT_RECEPTIONIST = """
            Você é a recepcionista virtual da "{{nomeBarbearia}}". {{persona}}
            Os títulos e avisos deste texto (em MAIÚSCULAS ou com emoji) são instruções internas: NUNCA os copie na resposta.
            Responda SEMPRE em português brasileiro. Use formatação *negrito* (um asterisco) e _itálico_ (underscore), pois estamos no WhatsApp.

            HOJE É DIA: {{hoje}}

            🗓️ *PRÓXIMOS 7 DIAS (dá para agendar em qualquer dia aberto desta lista):*
            {{calendarioSemana}}

            ✂️ *SERVIÇOS E PREÇOS:*
            {{tabelaServicos}}
            (Funcionamento: {{funcionamento}})

            🏠 *SOBRE A BARBEARIA (as únicas informações que você tem):*
            {{sobreBarbearia}}
            - Responda perguntas sobre a barbearia SÓ com o que está aqui e na tabela de serviços.
            - NUNCA invente nada que não esteja escrito (estacionamento, formas de pagamento, Wi-Fi, bebida,
              promoções, endereço). Se não souber, diga que não tem essa informação e que ele pode digitar
              *8* para falar com o barbeiro.

            ⚙️ *FLUXO OBRIGATÓRIO DE AGENDAMENTO (siga estes passos em ordem):*
            
            *PASSO 1 — NOME:* 
            Você está falando com o número de telefone do cliente. O nome salvo dele no sistema é: "{{knownCustomerName}}".
            - Se o nome salvo for "Desconhecido", ou apenas um primeiro nome genérico (ex: "Matheus"), peça de forma muito educada o *NOME E SOBRENOME* para o agendamento não ficar duplicado no sistema.
            - Se o nome salvo já for um nome e sobrenome completo ou válido para o negócio, *PULE este passo e o chame pelo nome*. NUNCA pergunte o nome novamente se já tiver.
            
            *PASSO 2 — SERVIÇO (sempre ANTES do horário):*
            {{etapaServico}}
            {{orcamentoServico}}
            - Os horários livres dependem do serviço: só contam os barbeiros que fazem o serviço e a duração dele. Por isso o serviço vem primeiro.
            - "Corte", "cortar o cabelo", "na máquina", "degradê" = o corte simples do catálogo. NÃO pergunte se é visagista ou outro tipo: só ofereça uma variação se o cliente pedir.
            - Se o serviço ainda não foi definido, pergunte qual é, citando *brevemente* os nomes do catálogo acima numa linha só (preço só se o cliente perguntar). Use APENAS os serviços e preços da tabela — nunca invente valores. Combos valem: "corte e barba" são os dois serviços juntos.
            - Se o cliente escolher de forma indireta ("o primeiro", "os dois", "esse mesmo"), repita o nome exato do catálogo na resposta e inclua a tag [SERVICO|Nome exato do catálogo] (combo: [SERVICO|Corte + Barba]). Nessa mesma resposta NÃO cite horários livres: apenas pergunte o dia e o horário que ele prefere (os horários certos chegam na próxima mensagem).
            - Se o cliente já disse o dia/horário antes do serviço, guarde essa preferência: pergunte só o serviço e, quando ele responder, confira a disponibilidade daquele horário sem perguntar de novo.
            
            *PASSO 3 — DIA E HORÁRIO:* Com o serviço definido, pergunte (ou confira) o dia e horário que o cliente prefere.
            - *REGRA DE OURO:* NUNCA liste todos os horários disponíveis de uma vez, isso polui a tela.
            - Dá para agendar nos dias abertos dos *próximos 7 dias* (lista acima), dentro do funcionamento. "Amanhã" e "depois de amanhã" estão sempre dentro.
            - Só fale do prazo se o cliente pedir um dia fora da lista; aí diga com gentileza até que dia dá para marcar. Não repita esse aviso nas outras mensagens.
            - Horários livres de *HOJE* (vale SÓ para hoje, não para outros dias): {{horariosLivresHoje}}
            - Para outros dias, use os *HORÁRIOS LIVRES NO DIA PEDIDO* (quando vierem abaixo) ou o [STATUS DA AGENDA].
            - Você já tem os horários: NUNCA diga "um momento", "vou verificar" ou "aguarde". Responda direto.
            - Se o cliente sugerir um horário que está LIVRE, passe para o próximo passo.
            - Se o horário sugerido estiver OCUPADO, sugira *apenas 2 opções próximas* que estejam livres (ex: 30 min antes ou depois).
            
            *PASSO 4 — PEDIR CONFIRMAÇÃO:* Com nome, serviço, data e horário em mãos, retorne um resumo completo e PERGUNTE de forma clara se o cliente confirma o agendamento.
            NÃO emita a tag de agendamento neste passo.
            Exemplo: "Perfeito! Você quer agendar Corte no dia 26/05 às 14:00? Responda SIM para eu confirmar."
            
            *PASSO 5 — AGENDAR E FINALIZAR:* APENAS quando o cliente responder "Sim", "Pode", "Confirmo", ou concordar explicitamente com o Passo 4, você emite a tag de agendamento e pergunta a forma de pagamento.
            Se o cliente pediu um barbeiro pelo nome ("com o Bruno"), coloque esse nome como 6º campo da tag; se não pediu ninguém, use só 5 campos.
            Exemplo:
            "Agendamento Confirmado! ✅
            
            Como deseja pagar?
            1️⃣ PIX 💳
            2️⃣ Presencialmente 💵"
            [NOVO_AGENDAMENTO|João|26/05|14:00|Corte]
            (com barbeiro pedido: [NOVO_AGENDAMENTO|João|26/05|14:00|Corte|Bruno])
            
            ⚡ *CLIENTE QUE JÁ DISSE TUDO:* Se o cliente já deu vários dados numa mensagem só (ex.: "corte amanhã 15h com o Bruno"), NÃO pergunte de novo nada do que ele já informou — pule direto para o passo que falta (normalmente o Passo 4, a confirmação).
            
            🚫 *CANCELAMENTO DE HORÁRIO VS ABORTO DE CONVERSA:*
            - SE O CLIENTE JÁ TEM UM AGENDAMENTO NA BASE e pedir para "cancelar o meu horário", "desmarcar o meu horário", inclua a tag [CANCELAR_AGENDAMENTO] na sua resposta.
            - SE O CLIENTE ESTÁ NO MEIO DO FLUXO (Passo 1 ao 4) apenas conversando e disser "cancelar", "deixa pra lá", "não quero mais", ele está abortando a conversa atual. NESSE CASO, NÃO USE NENHUMA TAG. Apenas responda "Sem problemas! O atendimento foi cancelado. Se precisar de algo, chame novamente."
            - Use essa frase SÓ quando o cliente pedir com palavras para cancelar ou desistir. Emoji, "ok", 👍, um número solto, arquivo ou mensagem que você não entendeu NÃO é desistência: siga a conversa ou pergunte como pode ajudar.
            
            🚫 *BLOQUEIO ABSOLUTO DE DUPLICATAS (ERRO CRÍTICO):*
            NUNCA emita a tag [NOVO_AGENDAMENTO|...] mais de uma vez para a mesma intenção.
            Se o cliente enviar mensagens de encerramento como "obrigado", "valeu", "ok", APENAS responda cordialmente. É ESTRITAMENTE PROIBIDO emitir tags repetidas.
            
            ⚠️ *REGRAS CRÍTICAS:*
            [STATUS DA AGENDA]: {{agendaContext}}
            
            - A data na tag DEVE ser no formato numérico DD/MM. NUNCA escreva "amanhã" ou "hoje" na tag.
            - Se o horário estiver ocupado no [STATUS DA AGENDA], diga que está ocupado e liste no MÁXIMO 3 horários livres.
            - Pergunte um dado por vez (não peça tudo de uma vez), mas aproveite tudo que o cliente já informou. A ordem é: nome, serviço, dia/horário, confirmação.
            - Se o cliente JÁ informou o nome antes, NÃO peça de novo.
            - Se o cliente fizer perguntas soltas (endereço, preços), responda diretamente sem usar tags.
            
            📋 *LISTA DE ESPERA (Sextas e Sábados):* Se não houver horário livre e o cliente topar, ofereça a lista de espera e inclua:
            [LISTA_DE_ESPERA|Nome do Cliente|Dia da Semana ou data DD/MM|Período (manhã, tarde, noite ou qualquer)|Serviço|Barbeiro (ou qualquer)]
            Pergunte o período e o barbeiro só se o cliente ainda não disse; sem preferência, use "qualquer".
            Quando abrir uma vaga que combine, o sistema avisa o cliente sozinho e ele responde SIM para marcar.
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

    /** Vai no lugar dos horarios enquanto o servico nao foi definido (a IA pergunta o servico antes). */
    static final String SERVICO_PENDENTE = "(serviço ainda não definido — descubra o serviço antes de falar em "
            + "horários; os horários livres aparecem aqui assim que ele estiver definido)";

    private boolean isCatalogEmpty() {
        var catalog = serviceOfferingRepository.findByBarbershopIdAndActiveTrue(
                com.razor.api.tenant.TenantContext.getCurrent());
        return catalog == null || catalog.isEmpty();
    }

    /**
     * O que a IA sabe do servico nesta conversa (Passo 2 do prompt).
     *
     * @param servicoPedido servico ja definido (aptos vazio = ninguem configurado para ele, grade
     *                      geral); {@code null} = ainda nao definido
     * @param catalogoVazio barbearia sem catalogo: nao ha o que perguntar, segue o fluxo antigo
     */
    static String serviceStageBlock(ProfessionalAssignmentService.ServicoPedido servicoPedido, boolean catalogoVazio) {
        if (servicoPedido != null) {
            boolean filtrado = servicoPedido.aptos() != null && !servicoPedido.aptos().isEmpty();
            return "- ✅ *SERVIÇO JÁ DEFINIDO NA CONVERSA: " + servicoPedido.nome() + ".* NÃO pergunte o serviço de novo."
                    + (filtrado ? " Os horários livres abaixo já valem só para os barbeiros que fazem esse serviço e para a duração dele." : "");
        }
        if (catalogoVazio) {
            return "- O catálogo ainda não foi cadastrado: pergunte o serviço que o cliente quer e siga para o horário.";
        }
        return "- (interno) O serviço ainda não foi definido: antes de oferecer ou confirmar qualquer horário, descubra o serviço. "
                + "Você ainda NÃO tem os horários livres — não invente nem confirme disponibilidade.";
    }

    /**
     * Orcamento do servico definido para o Passo 4 (V46): cada servico com o preco, o total e a
     * duracao somada — pelo calculo unico ({@link PricingService#quoter}), o mesmo do painel. Com
     * um combo do catalogo que cobre o pedido e sai mais barato, manda a IA oferecer o combo dizendo
     * quanto economiza. Vazio quando o servico nao esta definido ou nao casa com o catalogo.
     */
    String quoteBlock(ProfessionalAssignmentService.ServicoPedido servicoPedido) {
        if (servicoPedido == null || pricingService == null) return "";
        PricingService.Quoter quoter = pricingService.quoter();
        if (quoter == null) return "";
        PricingService.Quote quote = quoter.quote(servicoPedido.nome());
        if (quote == null || !quote.fromCatalog()) return "";
        StringBuilder sb = new StringBuilder("- 🧾 *ORÇAMENTO DO SERVIÇO DEFINIDO* (valores de tabela; se a tabela acima mostra preço do plano do cliente, vale o do plano):\n");
        for (PricingService.QuoteLine l : quote.lines()) {
            sb.append("  • ").append(l.name()).append(": R$ ").append(brl(l.price()))
              .append(" (").append(formatDuration(l.durationMinutes())).append(")\n");
        }
        sb.append("  *Total: R$ ").append(brl(quote.total())).append("* · duração estimada: ")
          .append(formatDuration(quote.durationMinutes())).append("\n");
        if (quote.multiple()) {
            sb.append("- Na confirmação (Passo 4), liste cada serviço com o preço, depois o *TOTAL* e a *duração estimada*.\n");
        }
        quoter.comboFor(servicoPedido.nome()).ifPresent(c -> sb
                .append("- 💡 *COMBO DISPONÍVEL:* ")
                .append(String.join(" + ", c.covered().stream().map(PricingService.QuoteLine::name).toList()))
                .append(" saem por R$ ").append(brl(c.combo().price())).append(" no *").append(c.combo().name())
                .append("* — economia de R$ ").append(brl(c.savings())).append(". Antes de pedir a confirmação, ofereça o combo ")
                .append("dizendo quanto ele economiza (uma vez só). Se o cliente aceitar, o serviço passa a ser o *")
                .append(c.combo().name()).append("* (mais os outros serviços pedidos, se houver): use esse nome na tag [SERVICO|...] e na tag de agendamento. ")
                .append("Se recusar, siga com os serviços separados.\n"));
        return sb.toString().trim();
    }

    /** 30 → "30 min"; 90 → "1h30"; 120 → "2h". */
    static String formatDuration(int minutes) {
        if (minutes < 60) return minutes + " min";
        int h = minutes / 60;
        int m = minutes % 60;
        return m == 0 ? h + "h" : h + "h" + String.format("%02d", m);
    }

    /** Ex.: "Seg a Sáb, das 09:00 às 19:00" — vindo das configuracoes do tenant. */
    /** Os próximos 7 dias, marcando os fechados — a IA não oferece dia em que a barbearia não abre. */
    static String calendarBlock(LocalDate hoje, BarbershopSettingsService.BotSettings cfg) {
        DateTimeFormatter fmtDia = DateTimeFormatter.ofPattern("dd/MM (EEEE)", new Locale("pt", "BR"));
        StringBuilder cal = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            LocalDate d = hoje.plusDays(i);
            cal.append("- ").append(d.format(fmtDia));
            if (i == 0) cal.append(" — hoje");
            if (i == 1) cal.append(" — amanhã");
            if (!cfg.isOpenOn(d.getDayOfWeek())) cal.append(" — FECHADO");
            cal.append("\n");
        }
        return cal.toString();
    }

    /**
     * O que a IA pode dizer sobre a barbearia além do catálogo (25/11): sem isto ela inventava
     * ("não aceitamos cartão", "temos estacionamento") ou respondia o endereço de forma vaga.
     */
    static String aboutBlock(BarbershopSettingsService.BotSettings cfg) {
        StringBuilder sb = new StringBuilder();
        String addr = oneLine(cfg.addressText()).replaceAll("[*_📍]", "").trim();
        sb.append("- Endereço: ").append(addr.isBlank() ? "não informado" : addr);
        if (cfg.addressMapsUrl() != null && !cfg.addressMapsUrl().isBlank()) sb.append(" (mapa: ").append(cfg.addressMapsUrl()).append(")");
        sb.append("\n- Pagamento: ");
        String pay = oneLine(cfg.paymentInfo());
        if (!pay.isBlank()) sb.append(pay);
        else sb.append(cfg.hasPix() ? "PIX ou presencial no caixa (as outras formas não foram informadas)"
                : "presencial no caixa (as formas não foram informadas)");
        if (cfg.instagramUrl() != null && !cfg.instagramUrl().isBlank())
            sb.append("\n- Instagram: ").append(cfg.instagramUrl().trim());
        String cancel = oneLine(cfg.cancellationPolicy());
        if (!cancel.isBlank()) sb.append("\n- Cancelamento: ").append(cancel);
        return sb.toString();
    }

    /** "- Barbeiros: Rafael, Bruno" + a regra para quem pede alguém que não existe. */
    String barbersLine() {
        if (professionalRepository == null) return "";
        java.util.UUID shop = com.razor.api.tenant.TenantContext.getCurrent();
        if (shop == null) return "";
        List<String> nomes = professionalRepository.findByBarbershopIdAndActiveTrue(shop).stream()
                .map(com.razor.api.model.Professional::getName).filter(n -> n != null && !n.isBlank()).toList();
        if (nomes.isEmpty()) return "";
        return "\n- Barbeiros: " + String.join(", ", nomes)
                + ". Se o cliente pedir alguém que não está nesta lista, diga que esse profissional não atende aqui e cite os que atendem.";
    }

    /**
     * Horários livres do dia que o cliente pediu ("amanhã", "sexta", "27/11"), já calculados pelo
     * sistema para o serviço da conversa. Antes a IA deduzia da lista de ocupados e errava: chegou a
     * dizer que a sexta estava cheia quando havia vaga (pente fino de 25/11).
     */
    String requestedDayBlock(String userMessage, List<com.razor.api.model.Interaction> history, LocalDate hoje,
                             BarbershopSettingsService.BotSettings cfg,
                             ProfessionalAssignmentService.ServicoPedido servico, boolean semServico) {
        if (semServico || agendaService == null) return "";
        LocalDate dia = RequestedDate.find(userMessage, hoje);
        if (dia == null && history != null) {
            // A data pode ter vindo antes ("amanhã às 10" e só depois o serviço).
            for (com.razor.api.model.Interaction i : history) {
                if (i.getType() != com.razor.api.model.Interaction.InteractionType.USER) continue;
                dia = RequestedDate.find(i.getContent(), hoje);
                if (dia != null) break;
            }
        }
        if (dia == null || dia.isBefore(hoje) || !dia.isBefore(hoje.plusDays(7))) return "";
        String rotulo = dia.format(DateTimeFormatter.ofPattern("dd/MM (EEEE)", new Locale("pt", "BR")));
        if (!cfg.isOpenOn(dia.getDayOfWeek())) {
            return "\n\n🕒 *DIA PEDIDO: " + rotulo + "* — a barbearia está FECHADA nesse dia.";
        }
        String ddMM = dia.format(DateTimeFormatter.ofPattern("dd/MM"));
        com.razor.api.model.Professional barbeiro = barberMentioned(userMessage, history);
        if (barbeiro != null) {
            // Cliente pediu alguém: os horários são os DELE (antes saíam os da barbearia toda e o
            // horário acabava com outro barbeiro).
            String dele = agendaService.getHorariosDisponiveisDoProfissional(barbeiro.getId(), ddMM);
            return "\n\n🕒 *HORÁRIOS LIVRES DE " + barbeiro.getName().toUpperCase() + " NO DIA PEDIDO — " + rotulo + ":* " + dele
                    + "\nO cliente pediu esse barbeiro: ofereça SÓ estes horários (no máximo 3). Se nenhum servir, diga que ele está sem horário e ofereça outro dia ou outro barbeiro.";
        }
        String livres = agendaService.getHorariosDisponiveisDoDia(ddMM, servico);
        return "\n\n🕒 *HORÁRIOS LIVRES NO DIA PEDIDO — " + rotulo
                + (servico != null ? " — " + servico.nome() : "") + ":* " + livres
                + "\nUse ESTA lista para dizer se o horário pedido está livre e para sugerir os mais próximos (no máximo 3).";
    }

    /** O barbeiro citado pelo primeiro nome na mensagem atual ou nas anteriores do cliente. */
    com.razor.api.model.Professional barberMentioned(String userMessage, List<com.razor.api.model.Interaction> history) {
        if (professionalRepository == null) return null;
        java.util.UUID shop = com.razor.api.tenant.TenantContext.getCurrent();
        if (shop == null) return null;
        List<com.razor.api.model.Professional> pros = professionalRepository.findByBarbershopIdAndActiveTrue(shop);
        List<String> falas = new ArrayList<>();
        falas.add(userMessage);
        if (history != null) history.stream()
                .filter(i -> i.getType() == com.razor.api.model.Interaction.InteractionType.USER)
                .map(com.razor.api.model.Interaction::getContent).forEach(falas::add);
        for (String fala : falas) {
            if (fala == null) continue;
            String t = " " + java.text.Normalizer.normalize(fala, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase() + " ";
            for (var p : pros) {
                if (p.getName() == null || p.getName().isBlank()) continue;
                String first = java.text.Normalizer.normalize(p.getName().trim().split("\\s+")[0], java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "").toLowerCase();
                if (first.length() >= 3 && t.matches("(?s).*[^a-z]" + java.util.regex.Pattern.quote(first) + "[^a-z].*")) return p;
            }
        }
        return null;
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("\\s*\\n\\s*", ", ").trim();
    }

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
        return processCustomerMessage(userMessage, recentHistory, agendaContext, activeAppointments, knownCustomerName, customer, null);
    }

    /**
     * @param servicoPedido servico que o cliente ja citou na conversa e os barbeiros que o fazem
     *                      (ver {@link ProfessionalAssignmentService#servicoCitado}); {@code null} =
     *                      desconhecido, e os horarios livres de hoje olham a barbearia toda.
     */
    public String processCustomerMessage(String userMessage, List<com.razor.api.model.Interaction> recentHistory, String agendaContext,
                                         int activeAppointments, String knownCustomerName, com.razor.api.model.Customer customer,
                                         ProfessionalAssignmentService.ServicoPedido servicoPedido) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
            String dataHoje = hoje.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            
            var cfg = barbershopSettingsService.resolve();
            String cal = calendarBlock(hoje, cfg);
            
            // Calcula horários livres de HOJE para o prompt
            String dataHojeFormatada = hoje.format(DateTimeFormatter.ofPattern("dd/MM"));
            // Servico ja conhecido: so horarios em que um barbeiro QUE FAZ o servico esta livre
            // (a mesma regra da gravacao) — o bot nao oferece horario que depois e recusado.
            // Servico desconhecido com catalogo cadastrado: a IA NAO recebe horario nenhum — o
            // serviço vem antes, senao ela ofereceria um horario em que so esta livre quem nao faz o
            // servico (e a gravacao recusaria depois). Sem catalogo nao ha como perguntar: grade geral.
            boolean catalogoVazio = isCatalogEmpty();
            boolean perguntarServico = servicoPedido == null && !catalogoVazio;
            String horariosLivresHoje;
            if (perguntarServico) {
                horariosLivresHoje = SERVICO_PENDENTE;
                agendaContext = SERVICO_PENDENTE;
            } else {
                horariosLivresHoje = agendaService.getHorariosDisponiveisDoDia(dataHojeFormatada, servicoPedido);
                if (servicoPedido != null && servicoPedido.aptos() != null && !servicoPedido.aptos().isEmpty()) {
                    horariosLivresHoje += " (já considerando só os barbeiros que fazem " + servicoPedido.nome()
                            + "; se o cliente trocar de serviço, confirme o horário de novo)";
                }
            }

            // Identidade, catalogo e funcionamento vem do TENANT ATUAL (cfg, acima) — sem isso toda
            // barbearia teria um bot dizendo o nome e os precos de outra (bug multi-tenant).

            String finalPrompt = SYSTEM_PROMPT_RECEPTIONIST
                    .replace("{{etapaServico}}", serviceStageBlock(servicoPedido, catalogoVazio))
                    .replace("{{orcamentoServico}}", quoteBlock(servicoPedido))
                    .replace("{{agendaContext}}", agendaContext == null ? "" : agendaContext)
                    .replace("{{hoje}}", dataHoje)
                    .replace("{{horariosLivresHoje}}", horariosLivresHoje)
                    .replace("{{calendarioSemana}}", cal)
                    .replace("{{sobreBarbearia}}", aboutBlock(cfg) + barbersLine())
                    .replace("{{knownCustomerName}}", knownCustomerName)
                    .replace("{{nomeBarbearia}}", cfg.displayName())
                    .replace("{{persona}}", blankTo(cfg.botPersona(), DEFAULT_PERSONA))
                    .replace("{{tabelaServicos}}", buildServiceTable(customer))
                    .replace("{{funcionamento}}", buildOpeningHours(cfg));
                    
            finalPrompt += requestedDayBlock(userMessage, recentHistory, hoje, cfg, servicoPedido, perguntarServico);
            finalPrompt += bookingLimitHint(activeAppointments,
                    barbershopSettingsService.getMaxFutureBookingsPerCustomer());
                    
            messages.add(SystemMessage.from(finalPrompt));
            
            messages.addAll(buildHistoryMessages(recentHistory, userMessage));

            String text = generateWithEscalation(messages);
            if (healthSignals != null) healthSignals.aiSuccess();
            return text;
        } catch (Exception e) {
            // Nada de responder "deu um erro" como se fosse a IA: quem chama manda a mensagem de
            // contingencia (sem grava-la como fala do bot) e avisa o dono.
            if (healthSignals != null) healthSignals.aiFailure(e);
            log.error("[IA] Falha ao gerar resposta do chat", e);
            throw new AiUnavailableException("IA indisponivel: " + e.getMessage(), e);
        }
    }

    /**
     * Chat no modelo principal (gpt-4o-mini). So escala para o modelo forte (gpt-4o) quando o
     * principal falha, devolve vazio ou uma tag de agendamento quebrada — os casos em que o cliente
     * receberia "nao entendi" por limitacao do modelo, e nao da conversa.
     */
    String generateWithEscalation(List<ChatMessage> messages) {
        long t0 = System.nanoTime();
        String text = null;
        Exception falha = null;
        try {
            text = generateCounted(mainModel(), mainModelName(), com.razor.api.platform.AiUsageRecorder.Kind.CHAT,
                    m -> m.generate(messages));
        } catch (Exception e) {
            falha = e;
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        String motivo = falha != null ? "falha (" + falha.getMessage() + ")"
                : needsEscalation(text) ? "resposta vazia ou tag quebrada" : null;
        if (motivo == null) {
            log.info("[TEMPO] IA (modelo principal) respondeu em {} ms", ms);
            return text;
        }
        OpenAiChatModel forteModel = strongModel();
        if (forteModel == null) {
            if (falha != null) throw new IllegalStateException(falha.getMessage(), falha);
            return text;
        }
        log.warn("[IA] Modelo principal: {} em {} ms — escalando para o modelo forte", motivo, ms);
        long t1 = System.nanoTime();
        String forte = generateCounted(forteModel, strongModelName(), com.razor.api.platform.AiUsageRecorder.Kind.CHAT,
                m -> m.generate(messages));
        log.info("[TEMPO] IA (modelo forte) respondeu em {} ms", (System.nanoTime() - t1) / 1_000_000);
        return forte;
    }

    private OpenAiChatModel mainModel() {
        return aiRuntime != null ? aiRuntime.chat() : chatModel;
    }

    private OpenAiChatModel strongModel() {
        if (aiRuntime != null) return aiRuntime.escalation();
        return chatEscalation != null ? chatEscalation.model() : null;
    }

    private String mainModelName() {
        if (aiRuntime != null) return aiRuntime.current().chatModelName();
        return properties.getOpenai() != null ? properties.getOpenai().getModel() : null;
    }

    private String strongModelName() {
        if (aiRuntime != null) return aiRuntime.current().escalationModelName();
        return properties.getOpenai() != null ? properties.getOpenai().getEscalationModel() : null;
    }

    /** Gera e registra o uso (tokens) — ou a falha — da chamada. */
    private String generateCounted(OpenAiChatModel model, String modelName, com.razor.api.platform.AiUsageRecorder.Kind kind,
                                   java.util.function.Function<OpenAiChatModel, dev.langchain4j.model.output.Response<dev.langchain4j.data.message.AiMessage>> call) {
        try {
            var r = call.apply(model);
            if (usage != null) {
                var t = r.tokenUsage();
                usage.success(kind, modelName, t == null ? null : t.inputTokenCount(), t == null ? null : t.outputTokenCount());
            }
            return r.content().text();
        } catch (RuntimeException e) {
            if (usage != null) usage.failure(kind, modelName);
            throw e;
        }
    }

    /**
     * O modelo escreveu "Agendamento Confirmado / Como deseja pagar?" mas esqueceu a tag: nada foi
     * gravado e o cliente acharia que tem horario (visto em 25/11 com o gpt-4o-mini).
     */
    public static boolean claimsBookingWithoutTag(String text) {
        if (text == null || text.contains("[NOVO_AGENDAMENTO|")) return false;
        String t = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return t.contains("agendamento confirmado") || t.contains("como deseja pagar");
    }

    /** Resposta que nao serve: vazia, que diz ter agendado sem a tag, ou com tag que o parser nao le. */
    static boolean needsEscalation(String text) {
        if (text == null || text.isBlank()) return true;
        if (claimsBookingWithoutTag(text)) return true;
        if (!text.contains("[NOVO_AGENDAMENTO|")) return false;
        var intent = new com.razor.api.ai.AiTagParser().parse(text);
        return !(intent instanceof com.razor.api.ai.AiIntent.NovoAgendamento);
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
            // Ler foto de agenda e o caso dificil: vai direto no modelo forte, quando existe.
            OpenAiChatModel forte = strongModel();
            OpenAiChatModel visao = forte != null ? forte : mainModel();
            String response = generateCounted(visao, forte != null ? strongModelName() : mainModelName(),
                    com.razor.api.platform.AiUsageRecorder.Kind.VISION, m -> m.generate(systemMessage, userMessage));
            return response.replace("```json", "").replace("```", "").trim();
        } catch (Exception e) {
            log.error("Erro Visão Computacional: {}", e.getMessage());
            throw new RuntimeException("Falha ao ler imagem.", e);
        }
    }
    
    /**
     * Transcreve um audio. Modelo principal configuravel (padrao {@code gpt-4o-mini-transcribe},
     * mais rapido que o whisper); se ele falhar, tenta uma vez o de reserva ({@code whisper-1}).
     * Idioma fixo em portugues (pula a deteccao). Retorna null quando nada deu certo, para o
     * chamador nao tratar uma mensagem de erro como se fosse fala do usuario.
     */
    public String transcribeAudio(String base64Audio, String mimeType) {
        byte[] audioBytes;
        try {
            String pureBase64 = base64Audio.contains(",") ? base64Audio.split(",")[1] : base64Audio;
            audioBytes = Base64.getDecoder().decode(pureBase64);
        } catch (Exception e) {
            log.error("Erro Áudio: base64 inválido ({})", e.getMessage());
            return null;
        }
        String extension = "ogg";
        if (mimeType != null) {
            if (mimeType.contains("mp4")) extension = "mp4";
            else if (mimeType.contains("mpeg") || mimeType.contains("mp3")) extension = "mp3";
        }
        String principal = aiRuntime != null ? aiRuntime.current().transcribeModelName()
                : blankTo(properties.getOpenai().getWhisperModel(), "gpt-4o-mini-transcribe");
        String reserva = blankTo(properties.getOpenai().getWhisperFallbackModel(), "whisper-1");

        long t0 = System.nanoTime();
        String text = transcribeWith(principal, audioBytes, "audio." + extension);
        if (text == null && !reserva.equals(principal)) {
            log.warn("[AUDIO] Transcricao com {} falhou — tentando {}", principal, reserva);
            text = transcribeWith(reserva, audioBytes, "audio." + extension);
        }
        log.info("[TEMPO] Transcricao: {} ms ({} KB)", (System.nanoTime() - t0) / 1_000_000, audioBytes.length / 1024);
        return text;
    }

    /** Uma chamada a {@code /audio/transcriptions}; null em qualquer falha. */
    String transcribeWith(String model, byte[] audioBytes, String filename) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(audioBytes) {
                @Override public String getFilename() { return filename; }
            });
            builder.part("model", model);
            builder.part("language", "pt");

            String jsonResponse = getOpenAiWebClient()
                    .post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(builder.build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(java.time.Duration.ofSeconds(30));

            if (jsonResponse != null) {
                JsonNode root = objectMapper.readTree(jsonResponse);
                if (root.has("text")) {
                    String text = root.get("text").asText().trim();
                    if (usage != null) usage.success(com.razor.api.platform.AiUsageRecorder.Kind.TRANSCRIBE, model, null, null);
                    return text.isEmpty() ? null : text;
                }
            }
            log.error("Erro Áudio ({}): resposta sem campo 'text'", model);
            if (usage != null) usage.failure(com.razor.api.platform.AiUsageRecorder.Kind.TRANSCRIBE, model);
            return null;
        } catch (Exception e) {
            log.error("Erro Áudio ({}): {}", model, e.getMessage());
            if (usage != null) usage.failure(com.razor.api.platform.AiUsageRecorder.Kind.TRANSCRIBE, model);
            return null;
        }
    }

    private synchronized WebClient getOpenAiWebClient() {
        // A chave pode ter sido trocada no console (V51): recria o cliente quando muda.
        String key = aiRuntime != null ? aiRuntime.current().apiKey() : properties.getOpenai().getApiKey();
        if (openAiWebClient == null || !java.util.Objects.equals(key, openAiWebClientKey)) {
            openAiWebClient = WebClient.builder()
                    .baseUrl("https://api.openai.com/v1")
                    .defaultHeader("Authorization", "Bearer " + key)
                    .build();
            openAiWebClientKey = key;
        }
        return openAiWebClient;
    }
}