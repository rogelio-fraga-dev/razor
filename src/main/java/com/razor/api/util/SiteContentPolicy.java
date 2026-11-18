package com.razor.api.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razor.api.dto.SiteContent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * O que o dono pode gravar como conteudo por secao do site (V45).
 *
 * <p><b>Por que tao estrito:</b> isto vai para uma pagina publica servida na mesma origem do
 * painel, e o JSON e livre por natureza. Sem a lista fechada, qualquer chave inventada seria
 * gravada e devolvida — um lugar perfeito para alguem esconder lixo (ou um campo que um template
 * futuro passe a renderizar sem pensar). Entao: so as secoes que os templates conhecem, so os
 * quatro campos de cada uma, so texto puro, e com teto. O que nao cabe e recusado com 400 e uma
 * mensagem que o dono entende — cortar calado faria o texto dele sumir pela metade.
 *
 * <p>Os limites sao os mesmos de {@code CONTENT_LIMITS} em {@code web/src/site/content.ts}: o
 * editor conta os caracteres com a mesma regua ({@code String.length}, unidades UTF-16).
 *
 * <p>Funcoes puras, sem Spring: testaveis sozinhas e chamaveis de qualquer servico.
 */
public final class SiteContentPolicy {

    /** Mesma lista e ordem de {@code SiteSectionKey} no frontend. */
    public static final List<String> SECTION_KEYS = List.of(
            "hero", "stats", "services", "gallery", "story", "team", "plans", "how", "location", "cta");

    public static final int MAX_EYEBROW = 60;
    public static final int MAX_TITLE = 120;
    public static final int MAX_TEXT = 600;
    public static final int MAX_BOOK_LABEL = 40;

    private static final Set<String> TOP_KEYS = Set.of("sections", "bookLabel", "motion");
    private static final Set<String> SECTION_FIELDS = Set.of("visible", "eyebrow", "title", "text");
    private static final Set<String> MOTIONS = Set.of("full", "subtle", "off");

    private static final Map<String, String> SECTION_LABELS = Map.of(
            "hero", "Capa", "stats", "Números da casa", "services", "Serviços", "gallery", "Galeria",
            "story", "Sobre a casa", "team", "Equipe", "plans", "Planos", "how", "Como agendar",
            "location", "Onde estamos", "cta", "Chamada final");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SiteContentPolicy() {
    }

    /**
     * Valida e normaliza o conteudo vindo do editor.
     *
     * @return o conteudo limpo (texto aparado, vazio vira ausente, {@code visible: true} vira
     *         ausente), ou {@code null} se nao sobrar nada — "tudo no padrao".
     * @throws IllegalArgumentException com mensagem em portugues quando algo foge da regra.
     */
    public static SiteContent normalize(Map<String, ?> raw) {
        if (raw == null || raw.isEmpty()) return null;
        for (String k : raw.keySet()) {
            if (!TOP_KEYS.contains(k)) {
                throw new IllegalArgumentException("Conteúdo do site inválido: campo desconhecido \"" + k + "\".");
            }
        }

        Map<String, SiteContent.Section> sections = new LinkedHashMap<>();
        Object rawSections = raw.get("sections");
        if (rawSections != null) {
            if (!(rawSections instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Conteúdo do site inválido: seções em formato errado.");
            }
            for (Object key : map.keySet()) {
                if (!(key instanceof String k) || !SECTION_KEYS.contains(k)) {
                    throw new IllegalArgumentException("Conteúdo do site inválido: seção desconhecida \"" + key + "\".");
                }
            }
            // Ordem fixa (a da pagina), nao a de chegada: o JSON gravado fica estavel.
            for (String key : SECTION_KEYS) {
                Object value = map.get(key);
                if (value == null) continue;
                SiteContent.Section s = section(key, value);
                if (s != null) sections.put(key, s);
            }
        }

        String bookLabel = text(raw.get("bookLabel"), MAX_BOOK_LABEL, "Texto do botão de agendar", false);

        Object rawMotion = raw.get("motion");
        String motion = null;
        if (rawMotion != null) {
            if (!(rawMotion instanceof String m) || !MOTIONS.contains(m)) {
                throw new IllegalArgumentException("Animações: escolha Completas, Suaves ou Desligadas.");
            }
            // "full" e o padrao — nao precisa ser gravado.
            motion = m.equals("full") ? null : m;
        }

        if (sections.isEmpty() && bookLabel == null && motion == null) return null;
        return new SiteContent(sections.isEmpty() ? null : sections, bookLabel, motion);
    }

    private static SiteContent.Section section(String key, Object value) {
        String label = SECTION_LABELS.get(key);
        if (!(value instanceof Map<?, ?> fields)) {
            throw new IllegalArgumentException("Conteúdo do site inválido na seção " + label + ".");
        }
        for (Object f : fields.keySet()) {
            if (!(f instanceof String name) || !SECTION_FIELDS.contains(name)) {
                throw new IllegalArgumentException(
                        "Conteúdo do site inválido na seção " + label + ": campo desconhecido \"" + f + "\".");
            }
        }
        Object rawVisible = fields.get("visible");
        if (rawVisible != null && !(rawVisible instanceof Boolean)) {
            throw new IllegalArgumentException("Conteúdo do site inválido na seção " + label + ": visibilidade.");
        }
        Boolean visible = Boolean.FALSE.equals(rawVisible) ? Boolean.FALSE : null;
        String eyebrow = text(fields.get("eyebrow"), MAX_EYEBROW, label + " — texto acima do título", false);
        String title = text(fields.get("title"), MAX_TITLE, label + " — título", false);
        String text = text(fields.get("text"), MAX_TEXT, label + " — texto", true);
        if (visible == null && eyebrow == null && title == null && text == null) return null;
        return new SiteContent.Section(visible, eyebrow, title, text);
    }

    /**
     * Texto puro: tira caracteres de controle (quebra de linha so onde e paragrafo), apara, vazio
     * vira nulo. Acima do limite, recusa — nunca corta calado.
     */
    static String text(Object raw, int max, String campo, boolean multiline) {
        if (raw == null) return null;
        if (!(raw instanceof String s)) {
            throw new IllegalArgumentException(campo + ": esperado um texto.");
        }
        String clean = clean(s, multiline).strip();
        if (clean.isEmpty()) return null;
        if (clean.length() > max) {
            throw new IllegalArgumentException(campo + ": até " + max + " caracteres (tem " + clean.length() + ").");
        }
        return clean;
    }

    private static String clean(String s, boolean multiline) {
        StringBuilder out = new StringBuilder(s.length());
        String normalized = s.replace("\r\n", "\n").replace('\r', '\n');
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '\n') {
                out.append(multiline ? '\n' : ' ');
            } else if (c == '\t') {
                out.append(' ');
            } else if (!Character.isISOControl(c) && c != ' ' && c != ' '
                    && Character.getType(c) != Character.FORMAT) {
                // FORMAT cobre os invisiveis de direcao (U+202E etc.): servem para disfarcar texto.
                out.append(c);
            }
        }
        return out.toString();
    }

    // ---------- (de)serializacao da coluna content_json ----------

    public static String toJson(SiteContent content) {
        if (content == null) return null;
        try {
            return MAPPER.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o conteúdo do site.", e);
        }
    }

    /**
     * Le a coluna de volta. Passa de novo pelo {@link #normalize}: se um dia a regra apertar, o
     * dado antigo que nao cabe mais some do site em vez de sair cru. JSON corrompido = padrao.
     */
    public static SiteContent fromJson(String json) {
        Map<String, Object> map = toMap(json);
        if (map == null) return null;
        try {
            return normalize(map);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A coluna como mapa cru (o que o editor recebe e devolve). */
    public static Map<String, Object> toMap(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** O conteudo normalizado como mapa — o formato que o editor recebe no GET. */
    public static Map<String, Object> asMap(SiteContent content) {
        if (content == null) return null;
        return MAPPER.convertValue(content, new TypeReference<Map<String, Object>>() {});
    }
}
