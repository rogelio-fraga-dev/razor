package com.razor.api.util;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversao de datas/horas de agendamento de texto para tipos fortes.
 *
 * <p>Resolve os tokens que a IA e o admin usam ({@code "hoje"}, {@code "amanha"},
 * {@code "DD/MM"}, {@code "10h"}, {@code "10:00"}) para {@link LocalDate}/{@link LocalTime}
 * concretos. Para {@code "DD/MM"} sem ano, infere a <b>proxima ocorrencia futura</b> —
 * corrigindo o bug de virada de ano em que "28/12" visto em janeiro caia no ano errado.
 *
 * <p>Fuso fixo em America/Sao_Paulo (operacao 100% Brasil).
 */
public final class AppointmentDates {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern DATE_PATTERN = Pattern.compile("^(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?$");
    private static final Pattern TIME_HHMM = Pattern.compile("^(\\d{1,2}):(\\d{2})$");
    private static final Pattern TIME_HH = Pattern.compile("^(\\d{1,2})$");

    private AppointmentDates() {
    }

    /** Data de hoje no fuso de operacao. */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /**
     * Resolve um token de data para {@link LocalDate} concreto.
     *
     * <ul>
     *   <li>{@code "hoje"} -> hoje; {@code "amanha"}/{@code "amanhã"} -> amanha</li>
     *   <li>{@code "DD/MM"} -> proxima ocorrencia futura (this ano, ou +1 ano se ja passou)</li>
     *   <li>{@code "DD/MM/AAAA"} ou {@code "DD/MM/AA"} -> data exata</li>
     * </ul>
     *
     * @throws IllegalArgumentException se o token nao for reconhecido.
     */
    public static LocalDate parseDate(String token) {
        if (token == null) {
            throw new IllegalArgumentException("data nula");
        }
        String t = stripAccents(token.trim().toLowerCase());
        if (t.equals("hoje")) {
            return today();
        }
        if (t.equals("amanha")) {
            return today().plusDays(1);
        }
        Matcher m = DATE_PATTERN.matcher(t);
        if (m.matches()) {
            int day = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            if (m.group(3) != null) {
                int year = Integer.parseInt(m.group(3));
                if (year < 100) {
                    year += 2000;
                }
                return LocalDate.of(year, month, day);
            }
            LocalDate reference = today();
            LocalDate candidate = LocalDate.of(reference.getYear(), month, day);
            if (candidate.isBefore(reference)) {
                candidate = candidate.plusYears(1);
            }
            return candidate;
        }
        throw new IllegalArgumentException("data nao reconhecida: " + token);
    }

    /**
     * Resolve um token de hora para {@link LocalTime}.
     * Aceita {@code "10:00"}, {@code "10h"}, {@code "10h30"}, {@code "10"}.
     *
     * @throws IllegalArgumentException se o token nao for reconhecido.
     */
    public static LocalTime parseTime(String token) {
        if (token == null) {
            throw new IllegalArgumentException("hora nula");
        }
        String t = token.trim().toLowerCase().replace(" ", "").replace("h", ":");
        if (t.endsWith(":")) {
            t = t + "00";
        }
        Matcher hhmm = TIME_HHMM.matcher(t);
        if (hhmm.matches()) {
            return LocalTime.of(Integer.parseInt(hhmm.group(1)), Integer.parseInt(hhmm.group(2)));
        }
        Matcher hh = TIME_HH.matcher(t);
        if (hh.matches()) {
            return LocalTime.of(Integer.parseInt(hh.group(1)), 0);
        }
        throw new IllegalArgumentException("hora nao reconhecida: " + token);
    }

    /** Formata a data como {@code "dd/MM"} para exibir nas mensagens. */
    public static String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DISPLAY_DATE);
    }

    /** Formata a hora como {@code "HH:mm"} para exibir nas mensagens. */
    public static String formatTime(LocalTime time) {
        return time == null ? "" : time.format(DISPLAY_TIME);
    }

    private static String stripAccents(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}", "");
    }
}
