package com.razor.api.util;

import java.text.Normalizer;

/**
 * Sanitiza texto para logging em console (remove acentos e caracteres nao-ASCII,
 * como emojis), evitando lixo em terminais que nao suportam UTF-8.
 *
 * <p>Extraido de {@code OrchestratorService} para reuso pelos servicos de fluxo
 * (admin/cliente) e para permitir teste isolado.
 */
public final class ConsoleText {

    private ConsoleText() {
    }

    public static String sanitize(String text) {
        if (text == null) return "";
        String temp = Normalizer.normalize(text, Normalizer.Form.NFD);
        temp = temp.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        temp = temp.replaceAll("[^\\p{ASCII}]", "");
        return temp.trim();
    }
}
