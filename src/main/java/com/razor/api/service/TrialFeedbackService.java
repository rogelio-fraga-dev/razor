package com.razor.api.service;

import com.razor.api.model.Barbershop;
import com.razor.api.repository.BarbershopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Feedback obrigatório no teste grátis (V57, piloto de 26/11).
 *
 * <p>Etapas: D3 (a partir do 3º dia do teste), D7 (a partir do 7º) e FIM (a partir do penúltimo dia).
 * A etapa pendente é a mais antiga já alcançada e ainda sem resposta. Cada etapa pode ser adiada
 * <b>uma vez</b>, por 24 h; passado o adiamento, o painel fica bloqueado até a resposta.
 * Só vale para barbearia em teste (status TRIAL); conta ativa nunca é cobrada de feedback.
 */
@Service
@RequiredArgsConstructor
public class TrialFeedbackService {

    public static final List<String> STAGES = List.of("D3", "D7", "FIM");
    private static final ZoneId BR = ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate jdbc;
    private final BarbershopRepository barbershopRepository;

    public record Pending(String stage, boolean blocking, boolean canSnooze, LocalDateTime snoozedUntil) {
    }

    public record Answer(String stage, int score, String overall, String liked, String missing,
                         String wouldPay, String priceSuggestion) {
    }

    /** Etapa que já devia ter sido respondida, considerando o momento {@code now}. */
    static Optional<String> dueStage(LocalDateTime trialStart, LocalDateTime trialEnd, LocalDateTime now, List<String> answered) {
        if (trialStart == null) return Optional.empty();
        long days = Duration.between(trialStart, now).toDays();
        boolean fim = trialEnd != null ? !now.isBefore(trialEnd.minusDays(1)) : days >= 13;
        for (String s : STAGES) {
            boolean reached = switch (s) {
                case "D3" -> days >= 3;
                case "D7" -> days >= 7;
                default -> fim;
            };
            if (reached && !answered.contains(s)) return Optional.of(s);
        }
        return Optional.empty();
    }

    public Optional<Pending> pending(UUID shopId) {
        Barbershop shop = barbershopRepository.findById(shopId).orElse(null);
        if (shop == null || !Barbershop.STATUS_TRIAL.equals(shop.getStatus())) return Optional.empty();
        LocalDateTime now = LocalDateTime.now(BR);
        List<String> answered = jdbc.queryForList("select stage from trial_feedback where barbershop_id = ?", String.class, shopId);
        Optional<String> stage = dueStage(shop.getCreatedAt(), shop.getTrialEndsAt(), now, answered);
        if (stage.isEmpty()) return Optional.empty();
        List<LocalDateTime> snooze = jdbc.queryForList(
                "select snoozed_until from trial_feedback_snooze where barbershop_id = ? and stage = ?",
                LocalDateTime.class, shopId, stage.get());
        LocalDateTime until = snooze.isEmpty() ? null : snooze.get(0);
        boolean snoozedNow = until != null && now.isBefore(until);
        if (snoozedNow) return Optional.of(new Pending(stage.get(), false, false, until)); // adiado: não mostra
        return Optional.of(new Pending(stage.get(), until != null, until == null, until));
    }

    /** Adia a etapa por 24 h. Só uma vez por etapa. */
    public boolean snooze(UUID shopId, String stage) {
        if (!STAGES.contains(stage)) return false;
        Integer used = jdbc.queryForObject("select count(*) from trial_feedback_snooze where barbershop_id = ? and stage = ?",
                Integer.class, shopId, stage);
        if (used != null && used > 0) return false;
        jdbc.update("insert into trial_feedback_snooze (barbershop_id, stage, snoozed_until) values (?, ?, ?)",
                shopId, stage, LocalDateTime.now(BR).plusHours(24));
        return true;
    }

    public void answer(UUID shopId, UUID userId, Answer a) {
        if (!STAGES.contains(a.stage())) throw new IllegalArgumentException("Etapa inválida.");
        if (a.score() < 0 || a.score() > 10) throw new IllegalArgumentException("A nota vai de 0 a 10.");
        jdbc.update("""
                insert into trial_feedback (id, barbershop_id, user_id, stage, score, overall, liked, missing, would_pay, price_suggestion, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (barbershop_id, stage) do update set score = excluded.score, overall = excluded.overall,
                  liked = excluded.liked, missing = excluded.missing, would_pay = excluded.would_pay,
                  price_suggestion = excluded.price_suggestion, created_at = excluded.created_at
                """, UUID.randomUUID(), shopId, userId, a.stage(), a.score(), clip(a.overall(), 2000), clip(a.liked(), 2000),
                clip(a.missing(), 2000), a.wouldPay() == null ? null : clip(a.wouldPay().toUpperCase(), 10),
                clip(a.priceSuggestion(), 120), LocalDateTime.now(BR));
    }

    /** Todas as respostas, para o console da plataforma. */
    public List<Map<String, Object>> all() {
        return jdbc.queryForList("""
                select f.stage, f.score, f.overall, f.liked, f.missing, f.would_pay, f.price_suggestion, f.created_at,
                       b.id as barbershop_id, b.name as barbershop
                  from trial_feedback f join barbershops b on b.id = f.barbershop_id
                 order by f.created_at desc
                """);
    }

    /** Barbearias em teste com etapa vencida e sem resposta (para cobrar no console). */
    public List<Map<String, Object>> overdue() {
        List<Barbershop> trial = barbershopRepository.findAll().stream()
                .filter(b -> Barbershop.STATUS_TRIAL.equals(b.getStatus())).toList();
        LocalDateTime now = LocalDateTime.now(BR);
        return trial.stream().map(b -> {
            List<String> answered = jdbc.queryForList("select stage from trial_feedback where barbershop_id = ?", String.class, b.getId());
            return dueStage(b.getCreatedAt(), b.getTrialEndsAt(), now, answered)
                    .<Map<String, Object>>map(s -> Map.of("barbershopId", b.getId(), "barbershop", b.getName(), "stage", s))
                    .orElse(null);
        }).filter(java.util.Objects::nonNull).toList();
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : (t.length() > max ? t.substring(0, max) : t);
    }
}
