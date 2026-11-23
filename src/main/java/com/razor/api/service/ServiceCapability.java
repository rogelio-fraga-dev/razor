package com.razor.api.service;

import com.razor.api.model.Professional;
import com.razor.api.model.ServiceOffering;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * "Este barbeiro faz este servico?" (V37). Funcoes puras sobre o catalogo do tenant.
 *
 * <p>O problema: {@code Appointment.service} ainda e uma <b>string livre</b> gerada pela IA
 * ("Corte", "Corte + Barba", "barba"), enquanto o barbeiro guarda <b>ids</b> do catalogo. A
 * ponte e por nome normalizado (sem acento, sem caixa), em tres passos, do mais ao menos
 * confiavel:
 * <ol>
 *   <li><b>Nome exato</b> de um servico do catalogo ("Barba" = "barba") — exige aquele servico.</li>
 *   <li><b>Composicao</b>: nomes do catalogo contidos no texto ("Corte de Cabelo + Barba")
 *       — exige <em>todos</em> eles. E o que impede "quem nao faz barba" de receber um combo
 *       descrito por extenso.</li>
 *   <li><b>Abreviacao</b>: o texto contido em nomes do catalogo ("Corte" em "Corte de Cabelo" e
 *       em "Corte + Barba (Combo)") — basta fazer <em>um</em> deles.</li>
 * </ol>
 * Texto que nao casa com nada <b>nao restringe</b>: na duvida, ninguem perde o agendamento —
 * mesmo principio do "lista vazia = atende todos".
 */
public final class ServiceCapability {

    private ServiceCapability() {
    }

    /**
     * Grupos de exigencia: o barbeiro precisa fazer pelo menos um servico de <b>cada</b> grupo.
     * Lista vazia = sem restricao.
     */
    public static List<Set<UUID>> requirements(String serviceName, List<ServiceOffering> catalog) {
        String wanted = normalize(serviceName);
        if (wanted.isEmpty() || catalog == null || catalog.isEmpty()) return List.of();

        List<ServiceOffering> named = catalog.stream()
                .filter(s -> s.getId() != null && !normalize(s.getName()).isEmpty())
                .toList();

        // 1. Nome exato.
        Set<UUID> exact = named.stream()
                .filter(s -> normalize(s.getName()).equals(wanted))
                .map(ServiceOffering::getId)
                .collect(Collectors.toSet());
        if (!exact.isEmpty()) return List.of(exact);

        // 2. Composicao: cada servico citado por inteiro no texto e exigido.
        List<Set<UUID>> parts = new ArrayList<>();
        for (ServiceOffering s : named) {
            if (containsWord(wanted, normalize(s.getName()))) parts.add(Set.of(s.getId()));
        }
        if (!parts.isEmpty()) return parts;

        // 3. Abreviacao: qualquer servico cujo nome contenha o texto serve.
        Set<UUID> abbreviated = named.stream()
                .filter(s -> containsWord(normalize(s.getName()), wanted))
                .map(ServiceOffering::getId)
                .collect(Collectors.toSet());
        if (!abbreviated.isEmpty()) return List.of(abbreviated);

        return List.of();
    }

    /**
     * Servicos do catalogo citados por inteiro numa frase livre do cliente ("quero fazer a barba
     * amanha" → Barba). Quando um citado esta contido em outro tambem citado ("Corte" dentro de
     * "Corte e Barba"), fica so o mais longo. Ordem do catalogo; vazio = nada identificado. Mais
     * conservador que {@link #requirements}: abreviacao NAO conta ("corte" sozinho nao vira
     * "Corte Degrade"), porque aqui o texto e a conversa inteira, nao o nome do servico.
     */
    public static List<ServiceOffering> mentioned(String text, List<ServiceOffering> catalog) {
        String t = normalize(text);
        if (t.isEmpty() || catalog == null || catalog.isEmpty()) return List.of();
        List<ServiceOffering> hits = catalog.stream()
                .filter(s -> s.getId() != null && !normalize(s.getName()).isEmpty())
                .filter(s -> containsWord(t, normalize(s.getName())))
                .toList();
        return hits.stream()
                .filter(s -> hits.stream().noneMatch(o -> o != s
                        && normalize(o.getName()).length() > normalize(s.getName()).length()
                        && containsWord(normalize(o.getName()), normalize(s.getName()))))
                .toList();
    }

    /** O barbeiro atende o servico? Sem servicos configurados = atende todos. */
    public static boolean canPerform(Professional p, List<Set<UUID>> requirements) {
        if (p.servesAllServices() || requirements.isEmpty()) return true;
        Set<UUID> does = p.getServiceIds();
        return requirements.stream().allMatch(group -> group.stream().anyMatch(does::contains));
    }

    /** Filtra quem atende o servico, preservando a ordem. */
    public static List<Professional> qualified(List<Professional> pros, String serviceName,
                                               List<ServiceOffering> catalog) {
        List<Set<UUID>> req = requirements(serviceName, catalog);
        if (req.isEmpty()) return pros;
        return pros.stream().filter(p -> canPerform(p, req)).toList();
    }

    static String normalize(String s) {
        if (s == null) return "";
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /** {@link #containsWord} para o calculo de preco ({@code PricingService.Quoter}). */
    static boolean containsWordPublic(String haystack, String needle) {
        return containsWord(haystack, needle);
    }

    /** {@code needle} aparece em {@code haystack} como palavra(s) inteira(s) — "barba" nao casa "barbara". */
    private static boolean containsWord(String haystack, String needle) {
        if (needle.isEmpty()) return false;
        int from = 0;
        while (true) {
            int i = haystack.indexOf(needle, from);
            if (i < 0) return false;
            int end = i + needle.length();
            boolean startOk = i == 0 || !Character.isLetterOrDigit(haystack.charAt(i - 1));
            boolean endOk = end == haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end));
            if (startOk && endOk) return true;
            from = i + 1;
        }
    }
}
