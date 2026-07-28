package com.razor.api.util;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dias de funcionamento da barbearia (V32), guardados como lista ISO separada por virgula
 * ({@code "1,2,3,4,5,6"}, 1=segunda … 7=domingo).
 *
 * <p>A numeracao segue {@link DayOfWeek#getValue()} de proposito: a V16 usa 0=domingo (padrao do
 * JavaScript) para os bloqueios recorrentes, e ter duas convencoes conversando sem um ponto unico
 * de traducao seria pedir um bug de fuso de dia. Aqui nao ha conversao — o numero guardado e o
 * numero do Java.
 */
public final class WorkingDays {

    /** Segunda a sabado — o expediente que o sistema sempre anunciou como padrao. */
    public static final Set<DayOfWeek> DEFAULT = EnumSet.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);

    private WorkingDays() {
    }

    /**
     * Le a coluna. Valor ausente, vazio ou ilegivel cai no {@link #DEFAULT} — a alternativa seria
     * devolver "nenhum dia aberto", e uma barbearia que nunca mais oferece horario por causa de
     * um campo mal preenchido e pior do que uma que oferece de segunda a sabado.
     */
    public static Set<DayOfWeek> parse(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT;

        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String part : raw.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) continue;
            try {
                int value = Integer.parseInt(token);
                if (value >= 1 && value <= 7) days.add(DayOfWeek.of(value));
            } catch (NumberFormatException e) {
                // Token invalido e ignorado em vez de derrubar a leitura inteira: um caractere
                // solto nao pode fechar a agenda da barbearia.
            }
        }
        return days.isEmpty() ? DEFAULT : days;
    }

    /** Serializa para a coluna, sempre em ordem crescente para o valor gravado ser estavel. */
    public static String format(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) return format(DEFAULT);
        return days.stream()
                .sorted()
                .map(d -> String.valueOf(d.getValue()))
                .collect(Collectors.joining(","));
    }

    /** A barbearia abre neste dia da semana? */
    public static boolean isOpen(Set<DayOfWeek> days, DayOfWeek day) {
        return days.contains(day);
    }

    /**
     * Converte para numeros ISO (1=segunda … 7=domingo) — o formato da API.
     *
     * <p>A conversao e explicita porque o Jackson serializaria {@code DayOfWeek} como
     * {@code "MONDAY"} e desserializaria numero por <b>ordinal</b> (0=MONDAY), o que faz
     * {@code 1} virar terca. Um dia de diferenca no expediente que so apareceria quando o bot
     * recusasse segunda-feira.
     */
    public static Set<Integer> toIso(Set<DayOfWeek> days) {
        return days.stream().sorted().map(DayOfWeek::getValue)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** Le numeros ISO vindos da API; valores fora de 1..7 sao ignorados. */
    public static Set<DayOfWeek> fromIso(Set<Integer> iso) {
        if (iso == null || iso.isEmpty()) return EnumSet.noneOf(DayOfWeek.class);
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (Integer value : iso) {
            if (value != null && value >= 1 && value <= 7) days.add(DayOfWeek.of(value));
        }
        return days;
    }

    /**
     * Frase para o cliente e para o prompt da IA — ex.: {@code "Seg a Sáb"}, {@code "Ter, Qui e Sáb"}.
     *
     * <p>Substitui o texto livre da V15: com o dado estruturado, a frase e <b>derivada</b> e nao
     * pode mais discordar do que a agenda faz.
     */
    public static String describe(Set<DayOfWeek> days) {
        if (days.isEmpty()) return "fechado";
        if (days.size() == 7) return "todos os dias";

        var ordered = days.stream().sorted().toList();
        // Sequencia sem buracos vira intervalo ("Seg a Sáb"); com buraco, vira lista.
        boolean contiguous = ordered.get(ordered.size() - 1).getValue() - ordered.get(0).getValue()
                == ordered.size() - 1;
        if (contiguous && ordered.size() > 2) {
            return shortName(ordered.get(0)) + " a " + shortName(ordered.get(ordered.size() - 1));
        }

        var names = ordered.stream().map(WorkingDays::shortName).toList();
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " e " + names.get(names.size() - 1);
    }

    /** "Seg", "Ter", … — abreviacao com inicial maiuscula e sem o ponto do locale. */
    private static String shortName(DayOfWeek day) {
        String s = day.getDisplayName(java.time.format.TextStyle.SHORT,
                Locale.forLanguageTag("pt-BR")).replace(".", "");
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
