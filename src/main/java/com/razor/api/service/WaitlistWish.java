package com.razor.api.service;

import com.razor.api.model.WaitlistEntry;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta o desejo de quem entra na lista de espera (V47) a partir do texto livre que a IA manda
 * ("sexta de manhã", "25/11", "amanhã à tarde", "qualquer dia"). Funcao pura: sem Spring, sem banco.
 *
 * @param date    data exata, quando o texto tem uma (dd/mm, hoje, amanha)
 * @param weekday dia da semana, quando o texto cita um e nao ha data exata
 * @param period  periodo (manha/tarde/noite), quando citado
 */
public record WaitlistWish(LocalDate date, DayOfWeek weekday, WaitlistEntry.Period period) {

    private static final Pattern DATE = Pattern.compile("(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?");

    public static WaitlistWish parse(String dayText, String periodText, LocalDate today) {
        String t = normalize(dayText);
        LocalDate date = null;
        Matcher m = DATE.matcher(t);
        if (m.find()) {
            try {
                date = com.razor.api.util.AppointmentDates.parseDate(m.group());
            } catch (Exception ignored) {
                // data invalida (31/02): fica sem data
            }
        } else if (t.contains("amanha")) {
            date = today.plusDays(1);
        } else if (t.matches(".*\\bhoje\\b.*")) {
            date = today;
        }
        DayOfWeek weekday = date == null ? weekdayOf(t).orElse(null) : null;
        WaitlistEntry.Period period = periodOf(normalize(periodText)).or(() -> periodOf(t)).orElse(null);
        return new WaitlistWish(date, weekday, period);
    }

    /** Dia da semana citado no texto ("sexta", "sáb", "segunda-feira"). */
    public static Optional<DayOfWeek> weekdayOf(String text) {
        String t = normalize(text);
        if (t.isEmpty()) return Optional.empty();
        if (t.contains("segunda") || t.matches(".*\\bseg\\b.*")) return Optional.of(DayOfWeek.MONDAY);
        if (t.contains("terca") || t.matches(".*\\bter\\b.*")) return Optional.of(DayOfWeek.TUESDAY);
        if (t.contains("quarta") || t.matches(".*\\bqua\\b.*")) return Optional.of(DayOfWeek.WEDNESDAY);
        if (t.contains("quinta") || t.matches(".*\\bqui\\b.*")) return Optional.of(DayOfWeek.THURSDAY);
        if (t.contains("sexta") || t.matches(".*\\bsex\\b.*")) return Optional.of(DayOfWeek.FRIDAY);
        if (t.contains("sabado") || t.matches(".*\\bsab\\b.*")) return Optional.of(DayOfWeek.SATURDAY);
        if (t.contains("domingo") || t.matches(".*\\bdom\\b.*")) return Optional.of(DayOfWeek.SUNDAY);
        return Optional.empty();
    }

    /** Periodo citado ("de manhã", "à tarde", "noite"); "qualquer" = nenhum. */
    public static Optional<WaitlistEntry.Period> periodOf(String text) {
        String t = normalize(text);
        // Palavra inteira: "amanha" contem "manha" e nao e periodo.
        if (t.matches(".*\\bmanha\\b.*")) return Optional.of(WaitlistEntry.Period.MANHA);
        if (t.contains("tarde")) return Optional.of(WaitlistEntry.Period.TARDE);
        if (t.contains("noite")) return Optional.of(WaitlistEntry.Period.NOITE);
        return Optional.empty();
    }

    /** Minusculo, sem acento, so letras/digitos/espaco/barra. */
    static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .replaceAll("[^a-z0-9/ ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** "qualquer", "tanto faz", "-" e vazio = sem preferencia. */
    static boolean isAny(String s) {
        String t = normalize(s);
        return t.isEmpty() || t.contains("qualquer") || t.contains("tanto faz") || t.equals("nenhum")
                || t.equals("indiferente");
    }
}
