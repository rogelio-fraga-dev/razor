package com.razor.api.platform;

import com.razor.api.service.TrialFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Controle das barbearias pelo console (26/11): lista com uso, estender teste, suspender/reativar,
 * trocar o plano, redefinir a senha do dono, anotação — e o relatório organizado para o Claude.
 * Cada ação é registrada na trilha pelo controller.
 */
@Service
@RequiredArgsConstructor
public class PlatformShopsService {

    private static final ZoneId BR = ZoneId.of("America/Sao_Paulo");
    public static final Set<String> STATUSES = Set.of("TRIAL", "ACTIVE", "SUSPENDED");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TrialFeedbackService feedback;
    private final ErrorLogBuffer errors;

    public List<Map<String, Object>> list() {
        return jdbc.queryForList("""
                select b.id, b.name, b.slug, b.city, b.status, b.signup_plan as plan, b.trial_ends_at, b.created_at,
                       b.platform_note as note, b.whatsapp_instance,
                       (select u.name  from tenant_users u where u.barbershop_id = b.id and u.role = 'OWNER' order by u.created_at limit 1) as owner_name,
                       (select u.email from tenant_users u where u.barbershop_id = b.id and u.role = 'OWNER' order by u.created_at limit 1) as owner_email,
                       (select count(*) from customers c where c.barbershop_id = b.id) as customers,
                       (select count(*) from appointments a where a.barbershop_id = b.id and a.created_at > now() - interval '7 days') as appts_7d,
                       (select count(*) from appointments a where a.barbershop_id = b.id and a.source = 'BOT' and a.created_at > now() - interval '7 days') as bot_appts_7d,
                       (select count(*) from comandas c where c.barbershop_id = b.id and c.created_at > now() - interval '7 days') as comandas_7d,
                       (select count(distinct d) from (
                           select cast(c.created_at as date) d from comandas c where c.barbershop_id = b.id and c.created_at > now() - interval '7 days'
                           union select cast(a.created_at as date) from appointments a where a.barbershop_id = b.id and a.source = 'PAINEL' and a.created_at > now() - interval '7 days'
                       ) x) as active_days_7d,
                       (select max(i.timestamp) from interactions i where i.barbershop_id = b.id) as last_message_at,
                       (select count(*) from trial_feedback f where f.barbershop_id = b.id) as feedbacks
                  from barbershops b
                 order by b.created_at desc
                """);
    }

    @Transactional
    public void extendTrial(UUID id, int days) {
        if (days < 1 || days > 60) throw new IllegalArgumentException("Estenda de 1 a 60 dias.");
        jdbc.update("update barbershops set trial_ends_at = greatest(coalesce(trial_ends_at, now()), now()) + (? * interval '1 day'), "
                + "status = case when status = 'SUSPENDED' then status else 'TRIAL' end, updated_at = now() where id = ?", days, id);
    }

    @Transactional
    public void setStatus(UUID id, String status) {
        String s = status == null ? "" : status.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(s)) throw new IllegalArgumentException("Situação inválida.");
        jdbc.update("update barbershops set status = ?, updated_at = now() where id = ?", s, id);
        if ("SUSPENDED".equals(s)) {
            // Derruba as sessões abertas do painel dessa barbearia.
            jdbc.update("update tenant_users set token_version = token_version + 1 where barbershop_id = ?", id);
        }
    }

    @Transactional
    public void setPlan(UUID id, String plan) {
        String p = plan == null || plan.isBlank() ? null : plan.trim().toUpperCase(Locale.ROOT);
        if (p != null && !Set.of("PILOTO", "SOLO", "EQUIPE", "PRO").contains(p)) throw new IllegalArgumentException("Plano inválido.");
        jdbc.update("update barbershops set signup_plan = ?, updated_at = now() where id = ?", p, id);
    }

    @Transactional
    public void setNote(UUID id, String note) {
        String n = note == null ? null : (note.length() > 2000 ? note.substring(0, 2000) : note);
        jdbc.update("update barbershops set platform_note = ?, updated_at = now() where id = ?", n, id);
    }

    /** Nova senha provisória do dono (mostrada uma vez no console) e sessões derrubadas. */
    @Transactional
    public String resetOwnerPassword(UUID id) {
        List<UUID> owners = jdbc.queryForList("select id from tenant_users where barbershop_id = ? and role = 'OWNER' order by created_at limit 1", UUID.class, id);
        if (owners.isEmpty()) throw new IllegalArgumentException("Barbearia sem dono cadastrado.");
        String temp = tempPassword();
        jdbc.update("update tenant_users set password_hash = ?, token_version = token_version + 1, updated_at = now() where id = ?",
                passwordEncoder.encode(temp), owners.get(0));
        return temp;
    }

    static String tempPassword() {
        String alphabet = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder("razor-");
        for (int i = 0; i < 10; i++) sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        return sb.toString();
    }

    public String shopName(UUID id) {
        List<String> n = jdbc.queryForList("select name from barbershops where id = ?", String.class, id);
        return n.isEmpty() ? null : n.get(0);
    }

    // ------------------------------------------------------------------ relatório para o Claude

    /** Relatório em Markdown dos últimos {@code days} dias, com dados de clientes mascarados. */
    public String report(int days) {
        int d = Math.max(1, Math.min(days, 60));
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        StringBuilder md = new StringBuilder();
        md.append("# Relatório do Razor — últimos ").append(d).append(" dias\n\n");
        md.append("Gerado em ").append(LocalDateTime.now(BR).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
          .append(". Telefones e e-mails de clientes mascarados.\n\n");

        md.append("## 1. Barbearias\n\n| Barbearia | Situação | Plano | Dias c/ uso (7d) | Horários (7d) | Pela IA (7d) | Comandas (7d) | Feedbacks |\n|---|---|---|---|---|---|---|---|\n");
        for (Map<String, Object> s : list()) {
            md.append("| ").append(s.get("name")).append(" | ").append(s.get("status")).append(" | ").append(nz(s.get("plan")))
              .append(" | ").append(s.get("active_days_7d")).append(" | ").append(s.get("appts_7d")).append(" | ").append(s.get("bot_appts_7d"))
              .append(" | ").append(s.get("comandas_7d")).append(" | ").append(s.get("feedbacks")).append(" |\n");
        }

        md.append("\n## 2. Erros e avisos do sistema (desde o último reinício, agrupados)\n\n");
        List<ErrorLogBuffer.Group> groups = errors.snapshot();
        if (groups.isEmpty()) md.append("Nenhum.\n");
        for (ErrorLogBuffer.Group g : groups.stream().limit(40).toList()) {
            md.append("- **").append(g.level).append("** `").append(g.logger).append("` ×").append(g.count)
              .append(" (").append(g.first == null ? "?" : g.first.format(f)).append(" → ").append(g.last == null ? "?" : g.last.format(f)).append(")\n")
              .append("  - ").append(g.example == null ? "" : g.example.replace("\n", " ")).append("\n");
        }

        md.append("\n## 3. Eventos de conversa (agendamentos que falharam, conversas sinalizadas)\n\n");
        List<Map<String, Object>> ev = jdbc.queryForList("""
                select b.name as shop, e.kind, count(*) as n, max(e.created_at) as last, (array_agg(e.detail order by e.created_at desc))[1] as example
                  from conversation_events e join barbershops b on b.id = e.barbershop_id
                 where e.created_at > now() - (? * interval '1 day')
                 group by b.name, e.kind order by n desc limit 60
                """, d);
        if (ev.isEmpty()) md.append("Nenhum.\n");
        for (Map<String, Object> e : ev) {
            md.append("- ").append(e.get("shop")).append(" · **").append(e.get("kind")).append("** ×").append(e.get("n"))
              .append(" — ex.: ").append(ErrorLogBuffer.mask(String.valueOf(nz(e.get("example"))))).append("\n");
        }

        md.append("\n## 4. Incidentes de saúde\n\n");
        List<Map<String, Object>> inc = jdbc.queryForList("""
                select coalesce(b.name, 'plataforma') as shop, i.type, i.status, i.occurrences, i.first_seen_at, i.last_seen_at, i.detail
                  from system_incident i left join barbershops b on b.id = i.barbershop_id
                 where i.last_seen_at > now() - (? * interval '1 day') order by i.last_seen_at desc limit 40
                """, d);
        if (inc.isEmpty()) md.append("Nenhum.\n");
        for (Map<String, Object> i : inc) {
            md.append("- ").append(i.get("shop")).append(" · ").append(i.get("type")).append(" (").append(i.get("status")).append(") ×")
              .append(i.get("occurrences")).append(" — ").append(ErrorLogBuffer.mask(String.valueOf(nz(i.get("detail"))))).append("\n");
        }

        md.append("\n## 5. Uso e custo da IA\n\n| Modelo | Tipo | Chamadas | Falhas | Tokens entrada | Tokens saída | Custo estimado |\n|---|---|---|---|---|---|---|\n");
        for (Map<String, Object> u : jdbc.queryForList("""
                select model, kind, sum(calls) calls, sum(failures) failures, sum(input_tokens) tin, sum(output_tokens) tout
                  from ai_usage_daily where day > current_date - ? group by model, kind order by calls desc
                """, d)) {
            md.append("| ").append(u.get("model")).append(" | ").append(u.get("kind")).append(" | ").append(u.get("calls")).append(" | ")
              .append(u.get("failures")).append(" | ").append(u.get("tin")).append(" | ").append(u.get("tout")).append(" | US$ ")
              .append(cost(String.valueOf(u.get("model")), num(u.get("tin")), num(u.get("tout")), num(u.get("calls")))).append(" |\n");
        }

        md.append("\n## 6. Feedback do teste grátis\n\n");
        List<Map<String, Object>> fb = feedback.all();
        if (fb.isEmpty()) md.append("Nenhuma resposta ainda.\n");
        for (Map<String, Object> r : fb) {
            md.append("### ").append(r.get("barbershop")).append(" · ").append(r.get("stage")).append(" · nota ").append(r.get("score")).append("/10\n");
            line(md, "O que está achando", r.get("overall"));
            line(md, "O que mais ajudou", r.get("liked"));
            line(md, "O que falta / atrapalhou", r.get("missing"));
            line(md, "Pagaria?", r.get("would_pay"));
            line(md, "Quanto por mês", r.get("price_suggestion"));
            md.append("\n");
        }
        List<Map<String, Object>> late = feedback.overdue();
        if (!late.isEmpty()) {
            md.append("**Sem responder:** ");
            md.append(String.join(", ", late.stream().map(x -> x.get("barbershop") + " (" + x.get("stage") + ")").toList())).append("\n");
        }

        md.append("\n## 7. Ações feitas no console\n\n");
        List<Map<String, Object>> acts = jdbc.queryForList("""
                select l.created_at, l.action, coalesce(b.name, '') as shop, coalesce(l.target, '') as target
                  from platform_access_log l left join barbershops b on b.id = l.barbershop_id
                 where l.created_at > now() - (? * interval '1 day') order by l.created_at desc limit 80
                """, d);
        if (acts.isEmpty()) md.append("Nenhuma.\n");
        for (Map<String, Object> a : acts) {
            Object at = a.get("created_at");
            md.append("- ").append(at instanceof java.sql.Timestamp ts ? ts.toLocalDateTime().format(f) : String.valueOf(at))
              .append(" · ").append(a.get("action")).append(" · ").append(a.get("shop")).append(" ").append(ErrorLogBuffer.mask(String.valueOf(a.get("target")))).append("\n");
        }
        return md.toString();
    }

    private static void line(StringBuilder md, String label, Object v) {
        if (v != null && !String.valueOf(v).isBlank()) md.append("- **").append(label).append(":** ").append(String.valueOf(v).replace("\n", " ")).append("\n");
    }

    private static Object nz(Object o) {
        return o == null ? "—" : o;
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    /** Preços públicos por milhão de tokens (26/11/2026); transcrição estimada em 15 s por áudio. */
    static String cost(String model, long tin, long tout, long calls) {
        double c;
        if (model.startsWith("gpt-4o-mini")) c = tin / 1e6 * 0.15 + tout / 1e6 * 0.60;
        else if (model.startsWith("gpt-4o")) c = tin / 1e6 * 2.50 + tout / 1e6 * 10.0;
        else if (model.contains("whisper") || model.contains("transcribe")) c = calls * 0.25 * 0.006;
        else c = tin / 1e6 * 0.15 + tout / 1e6 * 0.60;
        return BigDecimal.valueOf(c).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
