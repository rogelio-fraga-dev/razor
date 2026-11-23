package com.razor.api.util;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Link de avaliacao do Google (V46). O bot manda esse link para o cliente satisfeito; se fosse
 * qualquer URL, um link trocado viraria phishing com a cara da barbearia. So passa {@code https}
 * com host do Google: {@code g.page} (link curto do Perfil da Empresa), {@code google.com}/
 * {@code google.com.br} e subdominios ({@code search.google.com/local/writereview},
 * {@code www.google.com/maps/...}) e {@code maps.app.goo.gl}.
 */
public final class GoogleReviewUrlPolicy {

    public static final int MAX_URL = 2048;
    static final String ERRO = "O link de avaliação precisa ser do Google (https://g.page/..., "
            + "https://search.google.com/local/writereview?... ou https://maps.app.goo.gl/...).";

    /** google.com, google.com.br e qualquer subdominio deles. */
    private static final Pattern GOOGLE = Pattern.compile("^([a-z0-9-]+\\.)*google\\.com(\\.br)?$");

    private GoogleReviewUrlPolicy() {
    }

    /** Valida e devolve o link sem espacos nas pontas; nulo/vazio vira {@code null} (sem link). */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String url = raw.trim();
        if (!isValid(url)) throw new IllegalArgumentException(ERRO);
        return url;
    }

    public static boolean isValid(String raw) {
        if (raw == null) return false;
        String url = raw.trim();
        if (url.isEmpty() || url.length() > MAX_URL
                || url.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return host.equals("g.page") || host.equals("maps.app.goo.gl") || GOOGLE.matcher(host).matches();
    }
}
