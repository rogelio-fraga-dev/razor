package com.razor.api.util;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Quais URLs o dono pode colocar no site white-label.
 *
 * <p><b>Por que existe:</b> o site e servido na MESMA origem do painel ({@code /b/:slug}). Um
 * {@code javascript:alert(document.cookie)} salvo como "link do Instagram" viraria um
 * {@code href} executavel com o token de quem estiver logado no navegador. Por isso so passa
 * {@code https://} com host — nada de {@code javascript:}, {@code data:}, {@code http:} (conteudo
 * misto quebra a pagina em https) nem caminho relativo. O frontend tem a mesma barreira
 * ({@code safeHttpUrl}) para dado antigo, gravado antes desta regra.
 *
 * <p>Instagram e mapa ainda precisam apontar para o servico certo: o botao "Como chegar" que leva
 * a um site qualquer e phishing com a cara da barbearia.
 */
public final class SiteUrlPolicy {

    public static final int MAX_URL = 2048;
    public static final int MAX_GALLERY = 12;

    private static final Pattern GOOGLE_HOST = Pattern.compile("^(www\\.)?google\\.[a-z]{2,3}(\\.[a-z]{2})?$");
    private static final Pattern GOOGLE_MAPS_HOST = Pattern.compile("^maps\\.google\\.[a-z]{2,3}(\\.[a-z]{2})?$");

    private SiteUrlPolicy() {
    }

    /** URL https com host, sem espaco, ate {@link #MAX_URL}. Nulo/vazio passa (campo opcional). */
    public static void requireHttps(String url, String campo) {
        if (url == null || url.isBlank()) return;
        parseHttps(url, campo);
    }

    public static void requireInstagram(String url) {
        if (url == null || url.isBlank()) return;
        String host = parseHttps(url, "Instagram").getHost().toLowerCase(Locale.ROOT);
        if (!(host.equals("instagram.com") || host.endsWith(".instagram.com"))) {
            throw new IllegalArgumentException("O link do Instagram precisa ser do instagram.com.");
        }
    }

    /** Google Maps: google.*&#47;maps, maps.google.*, maps.app.goo.gl ou goo.gl/maps. */
    public static void requireMaps(String url) {
        if (url == null || url.isBlank()) return;
        URI uri = parseHttps(url, "mapa");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        boolean ok = host.equals("maps.app.goo.gl")
                || GOOGLE_MAPS_HOST.matcher(host).matches()
                || (GOOGLE_HOST.matcher(host).matches() && path.startsWith("/maps"))
                || (host.equals("goo.gl") && path.startsWith("/maps"));
        if (!ok) {
            throw new IllegalArgumentException("O link do mapa precisa ser do Google Maps.");
        }
    }

    /** Uma URL https por linha, no maximo {@link #MAX_GALLERY} fotos. */
    public static void requireGallery(String lines) {
        if (lines == null || lines.isBlank()) return;
        List<String> urls = lines.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (urls.size() > MAX_GALLERY) {
            throw new IllegalArgumentException("A galeria aceita até " + MAX_GALLERY + " fotos.");
        }
        for (String u : urls) parseHttps(u, "galeria");
    }

    private static URI parseHttps(String raw, String campo) {
        String url = raw.trim();
        String erro = "Link inválido em " + campo + ": use um endereço completo começando com https://";
        if (url.length() > MAX_URL || url.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            throw new IllegalArgumentException(erro);
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            throw new IllegalArgumentException(erro);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()
                || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException(erro);
        }
        return uri;
    }
}
