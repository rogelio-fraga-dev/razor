package com.razor.api.service;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * O dia que o cliente pediu, em português do dia a dia: "hoje", "amanhã", "depois de amanhã",
 * "sexta", "27/11", "dia 27". Serve só para o bot mostrar os horários livres daquele dia; quem
 * valida a data de verdade é a gravação do agendamento.
 */
final class RequestedDate {

    private static final Map<String, DayOfWeek> DIAS = Map.of(
            "segunda", DayOfWeek.MONDAY, "terca", DayOfWeek.TUESDAY, "quarta", DayOfWeek.WEDNESDAY,
            "quinta", DayOfWeek.THURSDAY, "sexta", DayOfWeek.FRIDAY, "sabado", DayOfWeek.SATURDAY,
            "domingo", DayOfWeek.SUNDAY);
    private static final Pattern DD_MM = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})\\b");
    private static final Pattern DIA_N = Pattern.compile("\\bdia (\\d{1,2})\\b");
    private static final Pattern SEMANA = Pattern.compile("\\b(segunda|terca|quarta|quinta|sexta|sabado|domingo)\\b");

    private RequestedDate() {}

    static LocalDate find(String text, LocalDate hoje) {
        if (text == null || text.isBlank()) return null;
        String t = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
        if (t.contains("depois de amanha")) return hoje.plusDays(2);
        if (t.matches("(?s).*\\bamanha\\b.*")) return hoje.plusDays(1);
        if (t.matches("(?s).*\\bhoje\\b.*")) return hoje;
        Matcher m = DD_MM.matcher(t);
        if (m.find()) {
            try {
                LocalDate d = LocalDate.of(hoje.getYear(), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
                return d.isBefore(hoje) ? d.plusYears(1) : d;
            } catch (RuntimeException e) {
                return null; // 30/02 e afins
            }
        }
        m = SEMANA.matcher(t);
        if (m.find()) {
            DayOfWeek alvo = DIAS.get(m.group(1));
            int delta = (alvo.getValue() - hoje.getDayOfWeek().getValue() + 7) % 7;
            return hoje.plusDays(delta);
        }
        m = DIA_N.matcher(t);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            // O próximo "dia N" que existe: dia 31 pedido em novembro cai em 31/12.
            for (int k = 0; k < 3; k++) {
                LocalDate mes = hoje.plusMonths(k);
                if (n < 1 || n > mes.lengthOfMonth()) continue;
                LocalDate d = mes.withDayOfMonth(n);
                if (!d.isBefore(hoje)) return d;
            }
            return null;
        }
        return null;
    }
}
