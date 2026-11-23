package com.razor.api.platform;

import com.razor.api.config.RazorProperties;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Leituras do console da plataforma (V51), de TODAS as barbearias — por isso SQL direto, sem
 * TenantContext. Nada aqui escreve em dado de barbearia; so marcacoes do proprio console
 * (revisao de conversa, receita lancada).
 */
@Service
@RequiredArgsConstructor
public class PlatformConsoleService {

    /** Palavras que costumam indicar cliente irritado (heuristica — o console mostra, um humano julga). */
    static final String ANGRY_PATTERN = "(absurd|p[eé]ssim|horr[ií]vel|rid[ií]cul|palha[cç]ada|vergonha|\\mlixo\\M"
            + "|merda|porra|caralh|desrespeit|cansei|irritad|raiva|que saco|inacredit|nunca mais|reclama[cç][aã]o"
            + "|procon|falta de respeito|enrola[cç][aã]o|ningu[eé]m responde)";
    static final int MAX_DAYS = 90;

    private final JdbcTemplate jdbc;
    private final RazorProperties properties;

    // ------------------------------------------------------------------ custo

    /** Custo estimado em USD de um agregado de uso. */
    double costUsd(String model, String kind, long calls, long in, long out) {
        var p = properties.getPlatform();
        if ("TRANSCRIBE".equals(kind)) return calls * p.getTranscribeCallUsd();
        List<Double> price = priceFor(model);
        if (price == null) return 0;
        return in * price.get(0) / 1_000_000d + out * price.get(1) / 1_000_000d;
    }

    /** Preco do modelo: nome exato, senao o prefixo mais longo ("gpt-4o-mini-2024..." -> "gpt-4o-mini"). */
    List<Double> priceFor(String model) {
        Map<String, List<Double>> prices = properties.getPlatform().getPrices();
        if (model == null) return null;
        if (prices.containsKey(model)) return prices.get(model);
        return prices.entrySet().stream()
                .filter(e -> model.startsWith(e.getKey()))
                .max(Comparator.comparingInt(e -> e.getKey().length()))
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    // ------------------------------------------------------------------ visao geral

    public record ShopRow(UUID id, String name, String slug, String city, String status, boolean active,
                          LocalDateTime trialEndsAt, LocalDateTime createdAt, String plan, boolean whatsappConfigured,
                          long customers, long appointments, long botAppointments, long messages,
                          long aiCalls, long aiFailures, long aiTokens, double aiCostUsd, double aiCostBrl,
                          long openIncidents) {
    }

    public record Overview(int days, int shops, Map<String, Long> byStatus, long messages, long botAppointments,
                           long aiCalls, double aiCostUsd, double aiCostBrl, double usdToBrl, List<ShopRow> rows) {
    }

    public Overview overview(int days) {
        int d = clampDays(days);
        Timestamp since = Timestamp.valueOf(now().minusDays(d));
        Date sinceDay = Date.valueOf(today().minusDays(d));

        Map<UUID, Long> customers = countBy("select barbershop_id, count(*) from customers group by barbershop_id");
        Map<UUID, Long> appts = countBy("select barbershop_id, count(*) from appointments where created_at >= ? group by barbershop_id", since);
        Map<UUID, Long> botAppts = countBy("select barbershop_id, count(*) from appointments where created_at >= ? and source = 'BOT' "
                + "group by barbershop_id", since);
        Map<UUID, Long> msgs = countBy("select barbershop_id, count(*) from interactions where timestamp >= ? group by barbershop_id", since);
        Map<UUID, Long> incidents = countBy("select barbershop_id, count(*) from system_incident where status = 'OPEN' "
                + "and barbershop_id is not null group by barbershop_id");

        Map<UUID, double[]> ai = new HashMap<>(); // calls, failures, tokens, cost
        jdbc.query("select barbershop_id, model, kind, sum(calls), sum(failures), sum(input_tokens), sum(output_tokens) "
                + "from ai_usage_daily where day >= ? group by barbershop_id, model, kind", rs -> {
            UUID shop = rs.getObject(1, UUID.class);
            long calls = rs.getLong(4);
            long in = rs.getLong(6);
            long out = rs.getLong(7);
            double[] a = ai.computeIfAbsent(shop, k -> new double[4]);
            a[0] += calls;
            a[1] += rs.getLong(5);
            a[2] += in + out;
            a[3] += costUsd(rs.getString(2), rs.getString(3), calls, in, out);
        }, sinceDay);

        double fx = properties.getPlatform().getUsdToBrl();
        List<ShopRow> rows = jdbc.query("""
                select id, name, slug, city, status, active, trial_ends_at, created_at, signup_plan, whatsapp_instance
                  from barbershops order by created_at""", (rs, i) -> {
            UUID id = rs.getObject(1, UUID.class);
            double[] a = ai.getOrDefault(id, new double[4]);
            Timestamp trial = rs.getTimestamp(7);
            Timestamp created = rs.getTimestamp(8);
            String wpp = rs.getString(10);
            return new ShopRow(id, rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBoolean(6),
                    trial == null ? null : trial.toLocalDateTime(), created == null ? null : created.toLocalDateTime(),
                    rs.getString(9), wpp != null && !wpp.isBlank(),
                    customers.getOrDefault(id, 0L), appts.getOrDefault(id, 0L), botAppts.getOrDefault(id, 0L),
                    msgs.getOrDefault(id, 0L), (long) a[0], (long) a[1], (long) a[2], round(a[3], 4), round(a[3] * fx, 2),
                    incidents.getOrDefault(id, 0L));
        });

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ShopRow r : rows) byStatus.merge(r.active() ? String.valueOf(r.status()) : "INATIVA", 1L, Long::sum);
        double cost = rows.stream().mapToDouble(ShopRow::aiCostUsd).sum();
        return new Overview(d, rows.size(), byStatus,
                rows.stream().mapToLong(ShopRow::messages).sum(),
                rows.stream().mapToLong(ShopRow::botAppointments).sum(),
                rows.stream().mapToLong(ShopRow::aiCalls).sum(),
                round(cost, 4), round(cost * fx, 2), fx, rows);
    }

    public String shopInstance(UUID shopId) {
        List<String> r = jdbc.query("select whatsapp_instance from barbershops where id = ?", (rs, i) -> rs.getString(1), shopId);
        if (r.isEmpty()) throw new NoSuchElementException();
        return r.get(0);
    }

    // ------------------------------------------------------------------ saude (todas as barbearias)

    public record IncidentRow(UUID id, String type, String status, UUID barbershopId, String barbershopName,
                              String message, String detail, LocalDateTime firstSeenAt, LocalDateTime lastSeenAt,
                              LocalDateTime resolvedAt, int occurrences) {
    }

    /** Abertos primeiro, depois os dos ultimos {@code days} dias. */
    public List<IncidentRow> incidents(int days) {
        Timestamp since = Timestamp.valueOf(now().minusDays(clampDays(days)));
        return jdbc.query("""
                select i.id, i.type, i.status, i.barbershop_id, b.name, i.detail, i.first_seen_at, i.last_seen_at,
                       i.resolved_at, i.occurrences
                  from system_incident i left join barbershops b on b.id = i.barbershop_id
                 where i.status = 'OPEN' or i.last_seen_at >= ?
                 order by (i.status = 'OPEN') desc, i.last_seen_at desc limit 300""", (rs, i) -> {
            String type = rs.getString(2);
            String msg;
            try {
                msg = com.razor.api.model.IncidentType.valueOf(type).ownerMessage();
            } catch (IllegalArgumentException e) {
                msg = type;
            }
            Timestamp resolved = rs.getTimestamp(9);
            return new IncidentRow(rs.getObject(1, UUID.class), type, rs.getString(3), rs.getObject(4, UUID.class),
                    rs.getString(5), msg, rs.getString(6), rs.getTimestamp(7).toLocalDateTime(),
                    rs.getTimestamp(8).toLocalDateTime(), resolved == null ? null : resolved.toLocalDateTime(), rs.getInt(10));
        }, since);
    }

    // ------------------------------------------------------------------ conversas

    public enum ConversationFilter { ALL, AI_ERROR, BOOKING_FAILED, ANGRY, FLAGGED }

    public record ConversationRow(UUID customerId, UUID shopId, String shopName, String customerName, String phoneMasked,
                                  LocalDateTime lastAt, long messages, long aiErrors, long bookingFailures,
                                  boolean angry, boolean flagged, String flagNote) {
    }

    public List<ConversationRow> conversations(UUID shop, ConversationFilter filter, int days) {
        int d = clampDays(days);
        Timestamp since = Timestamp.valueOf(now().minusDays(d));
        List<Object> args = new ArrayList<>(List.of(ANGRY_PATTERN, since));
        String shopFilter = "";
        if (shop != null) {
            shopFilter = " and i.barbershop_id = ?";
            args.add(shop);
        }
        List<ConversationRow> base = jdbc.query("""
                select c.id, c.barbershop_id, b.name, c.name, c.phone_number, max(i.timestamp), count(*),
                       bool_or(i.type = 'USER' and i.content ~* ?),
                       f.id is not null, f.note
                  from interactions i
                  join customers c on c.id = i.customer_id
                  join barbershops b on b.id = c.barbershop_id
                  left join conversation_flags f on f.customer_id = c.id and f.resolved_at is null
                 where i.timestamp >= ?""" + shopFilter + " " + """
                 group by c.id, c.barbershop_id, b.name, c.name, c.phone_number, f.id, f.note
                 order by max(i.timestamp) desc
                 limit 500""", (rs, i) -> new ConversationRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getString(4), mask(rs.getString(5)), rs.getTimestamp(6).toLocalDateTime(),
                rs.getLong(7), 0, 0, rs.getBoolean(8), rs.getBoolean(9), rs.getString(10)), args.toArray());

        // Eventos (IA fora, agendamento falhou) por telefone do cliente.
        Map<String, long[]> events = new HashMap<>();
        jdbc.query("select barbershop_id, customer_phone, kind, count(*) from conversation_events where created_at >= ? "
                + "group by barbershop_id, customer_phone, kind", rs -> {
            long[] e = events.computeIfAbsent(rs.getString(1) + "|" + rs.getString(2), k -> new long[2]);
            e["AI_ERROR".equals(rs.getString(3)) ? 0 : 1] += rs.getLong(4);
        }, since);
        Map<UUID, String> phones = new HashMap<>();
        if (!base.isEmpty()) {
            jdbc.query("select id, phone_number from customers where id = any(?)", rs -> {
                phones.put(rs.getObject(1, UUID.class), rs.getString(2));
            }, (Object) base.stream().map(ConversationRow::customerId).toArray(UUID[]::new));
        }

        List<ConversationRow> out = new ArrayList<>();
        for (ConversationRow r : base) {
            long[] e = events.getOrDefault(r.shopId() + "|" + phones.get(r.customerId()), new long[2]);
            ConversationRow full = new ConversationRow(r.customerId(), r.shopId(), r.shopName(), r.customerName(),
                    r.phoneMasked(), r.lastAt(), r.messages(), e[0], e[1], r.angry(), r.flagged(), r.flagNote());
            boolean keep = switch (filter == null ? ConversationFilter.ALL : filter) {
                case ALL -> true;
                case AI_ERROR -> full.aiErrors() > 0;
                case BOOKING_FAILED -> full.bookingFailures() > 0;
                case ANGRY -> full.angry();
                case FLAGGED -> full.flagged();
            };
            if (keep) out.add(full);
        }
        return out;
    }

    public record Message(String type, String content, LocalDateTime at) {
    }

    public record Event(String kind, String detail, LocalDateTime at) {
    }

    public record ConversationDetail(UUID customerId, UUID shopId, String shopName, String customerName, String phone,
                                     List<Message> messages, List<Event> events, boolean flagged, String flagNote) {
    }

    /** Conversa completa (com o telefone) — quem chama registra o acesso na trilha. */
    public ConversationDetail conversation(UUID customerId, int days) {
        int d = clampDays(days);
        Timestamp since = Timestamp.valueOf(now().minusDays(d));
        var head = jdbc.query("""
                select c.barbershop_id, b.name, c.name, c.phone_number, f.note, f.id is not null
                  from customers c join barbershops b on b.id = c.barbershop_id
                  left join conversation_flags f on f.customer_id = c.id and f.resolved_at is null
                 where c.id = ?""", (rs, i) -> new Object[] {rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getBoolean(6)}, customerId);
        if (head.isEmpty()) throw new NoSuchElementException();
        Object[] h = head.get(0);
        List<Message> msgs = jdbc.query("select type, content, timestamp from interactions where customer_id = ? "
                        + "and timestamp >= ? order by timestamp limit 1000",
                (rs, i) -> new Message(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toLocalDateTime()), customerId, since);
        List<Event> events = jdbc.query("select kind, detail, created_at from conversation_events where barbershop_id = ? "
                        + "and customer_phone = ? and created_at >= ? order by created_at",
                (rs, i) -> new Event(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toLocalDateTime()), h[0], h[3], since);
        return new ConversationDetail(customerId, (UUID) h[0], (String) h[1], (String) h[2], (String) h[3], msgs, events,
                (Boolean) h[5], (String) h[4]);
    }

    public UUID shopOfCustomer(UUID customerId) {
        List<UUID> r = jdbc.query("select barbershop_id from customers where id = ?", (rs, i) -> rs.getObject(1, UUID.class), customerId);
        if (r.isEmpty()) throw new NoSuchElementException();
        return r.get(0);
    }

    public void flag(UUID customerId, String note, UUID platformUser) {
        UUID shop = shopOfCustomer(customerId);
        String n = note == null ? null : note.strip();
        if (n != null && n.length() > 500) n = n.substring(0, 500);
        int updated = jdbc.update("update conversation_flags set note = ? where customer_id = ? and resolved_at is null", n, customerId);
        if (updated == 0) {
            jdbc.update("insert into conversation_flags (id, barbershop_id, customer_id, note, created_by, created_at) "
                    + "values (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), shop, customerId, n, platformUser, Timestamp.valueOf(now()));
        }
    }

    public void resolveFlag(UUID customerId) {
        jdbc.update("update conversation_flags set resolved_at = ? where customer_id = ? and resolved_at is null",
                Timestamp.valueOf(now()), customerId);
    }

    static String mask(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("\\D", "");
        return digits.length() <= 4 ? "••••" : "•••• " + digits.substring(digits.length() - 4);
    }

    // ------------------------------------------------------------------ metricas

    public record DayPoint(LocalDate day, long messagesIn, long messagesOut, long botAppointments, long aiCalls,
                           long aiFailures, long aiErrorEvents, long bookingFailures, Double avgResponseSeconds) {
    }

    public List<DayPoint> metrics(int days, UUID shop) {
        int d = clampDays(days);
        LocalDate from = today().minusDays(d - 1L);
        Timestamp since = Timestamp.valueOf(from.atStartOfDay());
        String sf = shop == null ? "" : " and barbershop_id = ?";
        Object[] args = shop == null ? new Object[] {since} : new Object[] {since, shop};
        Object[] dayArgs = shop == null ? new Object[] {Date.valueOf(from)} : new Object[] {Date.valueOf(from), shop};

        Map<LocalDate, long[]> m = new LinkedHashMap<>();
        for (int i = 0; i < d; i++) m.put(from.plusDays(i), new long[7]);
        Map<LocalDate, Double> resp = new HashMap<>();

        jdbc.query("select cast(timestamp as date), type, count(*) from interactions where timestamp >= ?" + sf
                + " group by 1, 2", rs -> {
            long[] a = m.get(rs.getDate(1).toLocalDate());
            if (a != null) a["USER".equals(rs.getString(2)) ? 0 : 1] += rs.getLong(3);
        }, args);
        jdbc.query("select cast(created_at as date), count(*) from appointments where created_at >= ? and source = 'BOT'" + sf
                + " group by 1", rs -> {
            long[] a = m.get(rs.getDate(1).toLocalDate());
            if (a != null) a[2] += rs.getLong(2);
        }, args);
        jdbc.query("select day, sum(calls), sum(failures) from ai_usage_daily where day >= ?" + sf + " group by day", rs -> {
            long[] a = m.get(rs.getDate(1).toLocalDate());
            if (a != null) {
                a[3] += rs.getLong(2);
                a[4] += rs.getLong(3);
            }
        }, dayArgs);
        jdbc.query("select cast(created_at as date), kind, count(*) from conversation_events where created_at >= ?" + sf
                + " group by 1, 2", rs -> {
            long[] a = m.get(rs.getDate(1).toLocalDate());
            if (a != null) a["AI_ERROR".equals(rs.getString(2)) ? 5 : 6] += rs.getLong(3);
        }, args);
        // Tempo de resposta: da mensagem do cliente ate a proxima fala do bot na mesma conversa
        // (so pares em ate 10 min — resposta de humano horas depois nao e "tempo do bot").
        jdbc.query("""
                select cast(ts as date), avg(extract(epoch from next_ts - ts))
                  from (select type, timestamp ts,
                               lead(type) over w next_type, lead(timestamp) over w next_ts
                          from interactions where timestamp >= ?""" + sf + " " + """
                        window w as (partition by customer_id order by timestamp)) x
                 where type = 'USER' and next_type = 'BOT' and next_ts - ts < interval '10 minutes'
                 group by 1""", rs -> {
            resp.put(rs.getDate(1).toLocalDate(), rs.getDouble(2));
        }, args);

        List<DayPoint> out = new ArrayList<>();
        m.forEach((day, a) -> out.add(new DayPoint(day, a[0], a[1], a[2], a[3], a[4], a[5], a[6],
                resp.containsKey(day) ? round(resp.get(day), 1) : null)));
        return out;
    }

    // ------------------------------------------------------------------ caixa do Razor

    public record RevenueEntry(UUID id, LocalDate month, UUID barbershopId, String barbershopName, String plan,
                               BigDecimal amount, String note) {
    }

    public record MonthTotal(String month, BigDecimal total) {
    }

    public record Revenue(BigDecimal currentMonth, BigDecimal previousMonth, Map<String, BigDecimal> byPlanThisMonth,
                          List<MonthTotal> months, List<RevenueEntry> entries) {
    }

    public Revenue revenue(int months) {
        int n = Math.max(1, Math.min(months, 36));
        YearMonth now = YearMonth.from(today());
        LocalDate from = now.minusMonths(n - 1L).atDay(1);
        List<RevenueEntry> entries = jdbc.query("""
                select r.id, r.month, r.barbershop_id, b.name, r.plan, r.amount, r.note
                  from platform_revenue r left join barbershops b on b.id = r.barbershop_id
                 where r.month >= ? order by r.month desc, r.created_at desc""",
                (rs, i) -> new RevenueEntry(rs.getObject(1, UUID.class), rs.getDate(2).toLocalDate(),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getBigDecimal(6), rs.getString(7)),
                Date.valueOf(from));
        Map<String, BigDecimal> byMonth = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) byMonth.put(now.minusMonths(n - 1L - i).toString(), BigDecimal.ZERO);
        Map<String, BigDecimal> byPlan = new LinkedHashMap<>();
        for (RevenueEntry e : entries) {
            String key = YearMonth.from(e.month()).toString();
            byMonth.merge(key, e.amount(), BigDecimal::add);
            if (key.equals(now.toString())) byPlan.merge(e.plan() == null || e.plan().isBlank() ? "—" : e.plan(), e.amount(), BigDecimal::add);
        }
        List<MonthTotal> series = byMonth.entrySet().stream().map(e -> new MonthTotal(e.getKey(), e.getValue())).toList();
        return new Revenue(byMonth.getOrDefault(now.toString(), BigDecimal.ZERO),
                byMonth.getOrDefault(now.minusMonths(1).toString(), BigDecimal.ZERO), byPlan, series, entries);
    }

    public UUID addRevenue(YearMonth month, UUID barbershopId, String plan, BigDecimal amount, String note) {
        if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("Valor inválido.");
        if (barbershopId != null) {
            Long exists = jdbc.queryForObject("select count(*) from barbershops where id = ?", Long.class, barbershopId);
            if (exists == null || exists == 0) throw new IllegalArgumentException("Barbearia não encontrada.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into platform_revenue (id, month, barbershop_id, plan, amount, note, created_at) values (?, ?, ?, ?, ?, ?, ?)",
                id, Date.valueOf(month.atDay(1)), barbershopId, trim(plan, 40), amount.setScale(2, RoundingMode.HALF_UP),
                trim(note, 200), Timestamp.valueOf(now()));
        return id;
    }

    public void deleteRevenue(UUID id) {
        if (jdbc.update("delete from platform_revenue where id = ?", id) == 0) throw new NoSuchElementException();
    }

    // ------------------------------------------------------------------ util

    private Map<UUID, Long> countBy(String sql, Object... args) {
        Map<UUID, Long> out = new HashMap<>();
        jdbc.query(sql, rs -> {
            out.put(rs.getObject(1, UUID.class), rs.getLong(2));
        }, args);
        return out;
    }

    static int clampDays(int days) {
        return Math.max(1, Math.min(days, MAX_DAYS));
    }

    private static String trim(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static double round(double v, int places) {
        return BigDecimal.valueOf(v).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(AppointmentDates.ZONE);
    }

    private static LocalDate today() {
        return LocalDate.now(AppointmentDates.ZONE);
    }
}
