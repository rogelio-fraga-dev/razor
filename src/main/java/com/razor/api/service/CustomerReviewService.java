package com.razor.api.service;

import com.razor.api.dto.ReviewSummaryResponse;
import com.razor.api.model.Comanda;
import com.razor.api.model.CustomerReview;
import com.razor.api.model.Professional;
import com.razor.api.repository.CustomerReviewRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.state.StateKeys;
import com.razor.api.state.StateStore;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import com.razor.api.util.GoogleReviewUrlPolicy;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Avaliacao pos-atendimento pelo bot (V46).
 *
 * <ol>
 *   <li>{@link #scheduleAfterVisit}: a comanda fechou (o atendimento aconteceu) → programa o pedido
 *       para o atraso configurado (padrao 2h), <b>dentro do expediente</b>. No maximo um pedido por
 *       cliente a cada 30 dias, nunca para admin nem para quem pediu para parar.</li>
 *   <li>{@link #sendOne}: o motor ({@code ReviewRequestScheduler}) manda "de 1 a 5, que nota voce
 *       da?" e arma a chave {@link StateKeys#review} da conversa — e ela que pega o "5" antes do
 *       menu numerico e da IA.</li>
 *   <li>{@link #handleReply}: 4-5 → agradece e manda o link do Google; 1-3 → agradece, pergunta o
 *       que houve e avisa os admins. Nota baixa NUNCA vai para o Google.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerReviewService {

    /** No maximo um pedido de avaliacao por cliente neste intervalo. */
    static final Duration FREQUENCY = Duration.ofDays(30);
    /** Quanto tempo a pergunta espera a nota antes de expirar. */
    static final Duration ANSWER_WINDOW = Duration.ofHours(48);
    /** Quanto tempo, depois da nota baixa, a proxima fala do cliente vale como comentario. */
    static final Duration COMMENT_WINDOW = Duration.ofHours(12);
    /** Pedido que ficou para tras (servidor parado) nao sai mais. */
    static final Duration STALE = Duration.ofDays(2);
    /** Adiamento quando o cliente esta falando com humano ou tem a confirmacao da vespera aberta. */
    static final Duration POSTPONE = Duration.ofHours(1);

    /** Linhas que contam para a regra de frequencia (CANCELLED nunca chegou ao cliente). */
    private static final Set<CustomerReview.Status> COUNTS_FOR_FREQUENCY = EnumSet.of(
            CustomerReview.Status.SCHEDULED, CustomerReview.Status.SENT, CustomerReview.Status.RATED,
            CustomerReview.Status.EXPIRED, CustomerReview.Status.OPTED_OUT);

    private final CustomerReviewRepository repository;
    private final BarbershopSettingsService settingsService;
    private final TenantAdminService tenantAdminService;
    private final CustomerService customerService;
    private final ProfessionalRepository professionalRepository;
    private final StateStore stateStore;
    private final WhatsAppMessenger messenger;

    /** Estado da conversa: qual pedido esta aberto e em que etapa. */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class PendingReview {
        private UUID reviewId;
        private Stage stage;
    }

    public enum Stage { RATING, COMMENT }

    // ------------------------------------------------------------------ programar

    /**
     * Programa o pedido de avaliacao do atendimento. Nunca lanca: avaliacao nao derruba o caixa.
     *
     * @return o pedido programado; vazio quando alguma regra impede
     */
    public Optional<CustomerReview> scheduleAfterVisit(Comanda comanda, String phone) {
        try {
            return doSchedule(comanda, phone, LocalDateTime.now(AppointmentDates.ZONE));
        } catch (Exception e) {
            log.error("[AVALIACAO] Falha ao programar o pedido de avaliacao de {}", phone, e);
            return Optional.empty();
        }
    }

    Optional<CustomerReview> doSchedule(Comanda comanda, String phone, LocalDateTime now) {
        if (comanda == null || phone == null || phone.isBlank() || "N/A".equals(phone)) return Optional.empty();
        UUID shop = TenantContext.getCurrent();
        var cfg = settingsService.getReviewSettings();
        if (!cfg.enabled()) return Optional.empty();
        if (tenantAdminService.isAdmin(phone)) return Optional.empty();
        if (comanda.getId() != null && repository.existsByComandaId(comanda.getId())) return Optional.empty();
        if (repository.existsByBarbershopIdAndCustomerPhoneAndStatus(shop, phone, CustomerReview.Status.OPTED_OUT)) {
            log.info("[AVALIACAO] {} pediu para nao receber avaliacao; nada programado", phone);
            return Optional.empty();
        }
        if (repository.existsByBarbershopIdAndCustomerPhoneAndStatusInAndCreatedAtAfter(
                shop, phone, COUNTS_FOR_FREQUENCY, now.minus(FREQUENCY))) {
            log.info("[AVALIACAO] {} ja recebeu pedido de avaliacao nos ultimos 30 dias", phone);
            return Optional.empty();
        }
        var bot = settingsService.resolve();
        LocalDateTime when = nextSendTime(now.plusMinutes(cfg.delayMinutes()), bot.workingWeekdays(),
                parseTime(bot.openingTime(), LocalTime.of(9, 0)), parseTime(bot.closingTime(), LocalTime.of(19, 0)));
        CustomerReview saved = repository.save(CustomerReview.builder()
                .barbershopId(shop)
                .customerPhone(phone)
                .customerName(comanda.getCustomerName())
                .comandaId(comanda.getId())
                .appointmentId(comanda.getAppointmentId())
                .professionalId(comanda.getProfessionalId())
                .status(CustomerReview.Status.SCHEDULED)
                .scheduledFor(when)
                .build());
        log.info("[AVALIACAO] Pedido de avaliacao de {} programado para {}", phone, when);
        return Optional.of(saved);
    }

    /**
     * Primeiro momento a partir de {@code candidate} dentro do expediente: dia aberto, entre a
     * abertura e o fechamento. Fora disso, vai para a abertura do proximo dia aberto (ate 7 dias).
     * Sem dias abertos configurados, devolve o proprio candidato.
     */
    static LocalDateTime nextSendTime(LocalDateTime candidate, Set<DayOfWeek> openDays, LocalTime open, LocalTime close) {
        if (openDays == null || openDays.isEmpty() || open == null || close == null || !open.isBefore(close)) {
            return candidate;
        }
        for (int i = 0; i <= 7; i++) {
            LocalDate day = candidate.toLocalDate().plusDays(i);
            if (!openDays.contains(day.getDayOfWeek())) continue;
            LocalTime start = i == 0 ? candidate.toLocalTime() : open;
            if (start.isBefore(open)) start = open;
            if (start.isBefore(close)) return LocalDateTime.of(day, start);
        }
        return candidate;
    }

    static boolean withinBusinessHours(LocalDateTime t, Set<DayOfWeek> openDays, LocalTime open, LocalTime close) {
        return nextSendTime(t, openDays, open, close).equals(t);
    }

    // ------------------------------------------------------------------ enviar

    /**
     * Envia um pedido programado e vencido (chamado pelo motor, com o tenant no contexto). Regras
     * reconferidas na hora: avaliacao desligada, opt-out ou pedido velho demais cancelam; cliente em
     * atendimento humano ou com a confirmacao da vespera aberta ("1"/"2" seriam ambiguos) adiam.
     *
     * @return true quando a pergunta saiu
     */
    public boolean sendOne(CustomerReview review, LocalDateTime now) {
        String phone = review.getCustomerPhone();
        var cfg = settingsService.getReviewSettings();
        if (!cfg.enabled()
                || repository.existsByBarbershopIdAndCustomerPhoneAndStatus(review.getBarbershopId(), phone,
                        CustomerReview.Status.OPTED_OUT)
                || review.getScheduledFor().isBefore(now.minus(STALE))) {
            review.setStatus(CustomerReview.Status.CANCELLED);
            repository.save(review);
            return false;
        }
        var bot = settingsService.resolve();
        LocalTime open = parseTime(bot.openingTime(), LocalTime.of(9, 0));
        LocalTime close = parseTime(bot.closingTime(), LocalTime.of(19, 0));
        if (!withinBusinessHours(now, bot.workingWeekdays(), open, close)) {
            // Motor atrasou (ex.: servidor parado) e o expediente acabou: fica para a proxima abertura.
            review.setScheduledFor(nextSendTime(now, bot.workingWeekdays(), open, close));
            repository.save(review);
            return false;
        }
        if (customerService.isCustomerPaused(phone)
                || stateStore.get(StateKeys.confirmation(phone), Object.class).isPresent()) {
            review.setScheduledFor(nextSendTime(now.plus(POSTPONE), bot.workingWeekdays(), open, close));
            repository.save(review);
            return false;
        }

        messenger.sendSync(phone, buildRequestMessage(review, bot), "SISTEMA");
        review.setStatus(CustomerReview.Status.SENT);
        review.setSentAt(now);
        repository.save(review);
        stateStore.put(StateKeys.review(phone), new PendingReview(review.getId(), Stage.RATING), ANSWER_WINDOW);
        log.info("[AVALIACAO] Pedido de avaliacao enviado para {}", phone);
        return true;
    }

    /** Pergunta sem resposta dentro do prazo vira EXPIRED (a chave da conversa expira sozinha). */
    public int expireUnanswered(LocalDateTime now) {
        List<CustomerReview> vencidos = repository.findByStatusAndSentAtBefore(
                CustomerReview.Status.SENT, now.minus(ANSWER_WINDOW));
        for (CustomerReview r : vencidos) {
            r.setStatus(CustomerReview.Status.EXPIRED);
            repository.save(r);
        }
        return vencidos.size();
    }

    String buildRequestMessage(CustomerReview review, BarbershopSettingsService.BotSettings bot) {
        String nome = firstName(review.getCustomerName());
        String barbeiro = professionalName(review.getProfessionalId());
        String intro = bot.reviewText() != null && !bot.reviewText().isBlank()
                ? bot.reviewText().trim()
                : "Oi" + (nome != null ? ", " + nome : "") + "! 💈 Obrigado por vir na *" + bot.displayName() + "*.";
        return intro + "\n\nDe *1 a 5*, que nota você dá pro seu atendimento"
                + (barbeiro != null ? " com o *" + barbeiro + "*" : "") + "? É só responder com o número. ⭐"
                + "\n\n_Se não quiser receber esse tipo de mensagem, responda *parar*._";
    }

    // ------------------------------------------------------------------ resposta

    /**
     * Trata a resposta do cliente se houver pedido de avaliacao aberto. Devolve o texto a
     * responder; vazio = nao era resposta da avaliacao, siga o fluxo normal (a chave continua).
     *
     * @param confirmationPending a pergunta "1 confirma / 2 desmarca" da vespera tambem esta aberta:
     *                            um "1" ou "2" puro fica com ela, nao com a nota
     */
    public Optional<String> handleReply(String phone, String message, boolean confirmationPending) {
        var pending = stateStore.get(StateKeys.review(phone), PendingReview.class);
        if (pending.isEmpty() || message == null) return Optional.empty();
        String t = normalize(message);

        if (pending.get().getStage() == Stage.COMMENT) {
            return handleComment(phone, message, t, pending.get());
        }

        if (isOptOut(t)) {
            stateStore.remove(StateKeys.review(phone));
            findReview(pending.get().getReviewId()).ifPresent(r -> {
                r.setStatus(CustomerReview.Status.OPTED_OUT);
                repository.save(r);
            });
            log.info("[AVALIACAO] {} pediu para nao receber mais pedidos de avaliacao", phone);
            return Optional.of("Combinado! Não vou mais te pedir avaliação. 👍\n\n"
                    + "Se precisar marcar um horário, é só chamar aqui. 💈");
        }

        Integer nota = parseRating(message);
        if (nota == null) return Optional.empty();
        if (confirmationPending && nota <= 2 && t.matches("[12]")) return Optional.empty();

        Optional<CustomerReview> found = findReview(pending.get().getReviewId());
        if (found.isEmpty()) {
            stateStore.remove(StateKeys.review(phone));
            return Optional.empty();
        }
        CustomerReview review = found.get();
        review.setRating(nota);
        review.setStatus(CustomerReview.Status.RATED);
        review.setRatedAt(LocalDateTime.now(AppointmentDates.ZONE));
        repository.save(review);
        String nome = firstName(review.getCustomerName());
        log.info("[AVALIACAO] {} deu nota {}", phone, nota);

        if (nota >= 4) {
            stateStore.remove(StateKeys.review(phone));
            String link = settingsService.getReviewSettings().googleReviewUrl();
            StringBuilder sb = new StringBuilder("Valeu demais" + (nome != null ? ", " + nome : "") + "! 🙌 ");
            if (link != null && GoogleReviewUrlPolicy.isValid(link)) {
                sb.append("Se puder deixar essa nota também no Google, ajuda muito a barbearia a crescer — leva menos de 1 minuto:\n⭐ ")
                  .append(link.trim());
            } else {
                sb.append("Fico feliz que curtiu. Até a próxima! 💈");
            }
            return Optional.of(sb.toString());
        }

        // Nota baixa: so o dono fica sabendo. Nada de link do Google.
        stateStore.put(StateKeys.review(phone), new PendingReview(review.getId(), Stage.COMMENT), COMMENT_WINDOW);
        String barbeiro = professionalName(review.getProfessionalId());
        notifyAdmins("⚠️ *Avaliação baixa*\n👤 " + displayName(review) + " (" + phone + ") deu nota *" + nota + "*"
                + (barbeiro != null ? " para o atendimento com " + barbeiro : "")
                + ".\nPedi pra contar o que aconteceu — se responder, te repasso aqui.");
        return Optional.of("Obrigado pela sinceridade" + (nome != null ? ", " + nome : "") + ". 🙏 "
                + "O que não foi legal? Me conta que eu passo direto pro responsável.");
    }

    private Optional<String> handleComment(String phone, String original, String t, PendingReview pending) {
        stateStore.remove(StateKeys.review(phone));
        // "menu" sempre abre o menu: o cliente desistiu de comentar.
        if (t.equals("menu")) return Optional.empty();
        Optional<CustomerReview> found = findReview(pending.getReviewId());
        if (Set.of("nada", "nao", "n", "deixa", "deixa pra la", "tudo bem", "esquece", "ok").contains(t)) {
            return Optional.of("Tudo bem! Obrigado mesmo assim. Se mudar de ideia, é só escrever aqui. 💈");
        }
        String comentario = original.trim();
        if (comentario.length() > 2000) comentario = comentario.substring(0, 2000);
        String finalComentario = comentario;
        found.ifPresent(r -> {
            r.setComment(finalComentario);
            repository.save(r);
            notifyAdmins("💬 *Comentário da avaliação* (nota " + r.getRating() + ")\n👤 " + displayName(r)
                    + " (" + phone + "): " + finalComentario);
        });
        return Optional.of("Obrigado por contar. 🙏 Já passei pro responsável, que vai olhar isso com carinho.");
    }

    private static final Set<String> OPT_OUT = Set.of("parar", "pare", "stop", "sair", "nao quero", "nao quero mais",
            "nao quero avaliar", "nao me mande mais", "nao mande mais", "parar avaliacao", "cancelar avaliacao");

    static boolean isOptOut(String normalized) {
        return OPT_OUT.contains(normalized);
    }

    private static final Pattern RATING = Pattern.compile(
            "^(?:(?:minha )?nota|dou(?: nota)?|da|e|eu dou)? ?(\\d{1,2}|um|uma|dois|duas|tres|quatro|cinco|dez)"
                    + "(?: (?:de )?(?:5|10|cinco|dez))?(?: estrelas?| pontos?)?$");

    /**
     * Le a nota de 1 a 5 de uma resposta curta: "5", "nota 4", "5 estrelas", "4/5", "⭐⭐⭐⭐⭐",
     * "cinco" (audio transcrito vira "Nota cinco."), "10" (= 5). Frase longa ou outro numero nao e
     * nota — segue o fluxo normal.
     */
    public static Integer parseRating(String msg) {
        if (msg == null) return null;
        String raw = msg.trim();
        if (raw.isEmpty()) return null;
        String semEstrelas = raw.replace("⭐", "").replace("🌟", "").replaceAll("[\\s\\uFE0F]", "");
        if (semEstrelas.isEmpty()) {
            int estrelas = (raw.length() - raw.replace("⭐", "").length()) / "⭐".length()
                    + (raw.length() - raw.replace("🌟", "").length()) / "🌟".length();
            return estrelas >= 1 && estrelas <= 5 ? estrelas : null;
        }
        Matcher m = RATING.matcher(normalize(raw));
        if (!m.matches()) return null;
        String n = m.group(1);
        int v = switch (n) {
            case "um", "uma" -> 1;
            case "dois", "duas" -> 2;
            case "tres" -> 3;
            case "quatro" -> 4;
            case "cinco" -> 5;
            case "dez" -> 10;
            default -> Integer.parseInt(n);
        };
        if (v == 10) return 5;
        return v >= 1 && v <= 5 ? v : null;
    }

    static String normalize(String s) {
        return Normalizer.normalize(s.toLowerCase().trim(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // ------------------------------------------------------------------ painel

    /** Media, distribuicao e as notas mais recentes dos ultimos {@code days} dias. */
    public ReviewSummaryResponse summary(int days) {
        UUID shop = TenantContext.getCurrent();
        LocalDateTime since = LocalDateTime.now(AppointmentDates.ZONE).minusDays(days);
        Double media = null;
        long total = 0;
        List<Object[]> stats = repository.ratingStats(shop, since);
        if (stats != null && !stats.isEmpty() && stats.get(0) != null) {
            Object[] row = stats.get(0);
            if (row.length > 0 && row[0] instanceof Number n) media = Math.round(n.doubleValue() * 10) / 10.0;
            if (row.length > 1 && row[1] instanceof Number n) total = n.longValue();
        }
        Map<Integer, Long> dist = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) dist.put(i, 0L);
        List<Object[]> rows = repository.ratingDistribution(shop, since);
        if (rows != null) {
            for (Object[] r : rows) {
                if (r[0] instanceof Number nota && r[1] instanceof Number qtd) dist.put(nota.intValue(), qtd.longValue());
            }
        }
        long enviados = repository.countByBarbershopIdAndSentAtAfter(shop, since);
        Double taxa = enviados > 0 ? Math.min(1.0, (double) total / enviados) : null;

        Map<UUID, String> nomes = new java.util.HashMap<>();
        for (Professional p : professionalRepository.findByBarbershopId(shop)) nomes.put(p.getId(), p.displayName());
        List<ReviewSummaryResponse.Item> recentes = repository
                .findByBarbershopIdAndRatingIsNotNullOrderByRatedAtDesc(shop, PageRequest.of(0, 8)).stream()
                .map(r -> new ReviewSummaryResponse.Item(r.getId(), displayName(r), r.getRating(), r.getComment(),
                        r.getRatedAt(), r.getProfessionalId() == null ? null : nomes.get(r.getProfessionalId())))
                .toList();
        var cfg = settingsService.getReviewSettings();
        return new ReviewSummaryResponse(days, media, total, dist, enviados, taxa, recentes,
                cfg.enabled(), cfg.googleReviewUrl() != null);
    }

    // ------------------------------------------------------------------ apoio

    private Optional<CustomerReview> findReview(UUID id) {
        if (id == null) return Optional.empty();
        return repository.findByIdAndBarbershopId(id, TenantContext.getCurrent());
    }

    private String professionalName(UUID id) {
        if (id == null) return null;
        try {
            return professionalRepository.findById(id)
                    .filter(p -> TenantContext.getCurrent().equals(p.getBarbershopId()))
                    .map(Professional::displayName)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private void notifyAdmins(String msg) {
        for (String adm : new java.util.LinkedHashSet<>(tenantAdminService.adminPhones())) {
            messenger.send(adm, msg, "ADMIN");
        }
    }

    private static LocalTime parseTime(String hhmm, LocalTime fallback) {
        try {
            return hhmm == null || hhmm.isBlank() ? fallback : LocalTime.parse(hhmm.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String firstName(String n) {
        return n == null || n.isBlank() ? null : n.trim().split("\\s+")[0];
    }

    private static String displayName(CustomerReview r) {
        return r.getCustomerName() == null || r.getCustomerName().isBlank() ? "Cliente" : r.getCustomerName();
    }
}
