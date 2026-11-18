package com.razor.api.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regras de validacao do perfil do barbeiro (V37). Funcoes puras, sem estado — testaveis sem
 * Spring e reaproveitaveis por quem mais precisar aceitar uma foto de perfil.
 *
 * <p>Todo campo aqui e <b>publico</b> (vai para o site white-label), entao a regra e de
 * fronteira: o que passa por aqui sera renderizado no navegador de um estranho.
 */
public final class ProfessionalProfileRules {

    /** Teto da foto <b>decodificada</b>. O painel gera ~20-40KB (256px JPEG); 60KB da folga. */
    public static final int MAX_PHOTO_BYTES = 60 * 1024;
    public static final int MAX_NICKNAME = 60;
    public static final int MAX_BIO = 500;
    public static final int MAX_SPECIALTIES = 8;
    public static final int MAX_SPECIALTY_LENGTH = 40;
    public static final int MAX_INSTAGRAM_URL = 255;

    /**
     * So imagem raster, so base64. {@code image/svg+xml} fica de fora de proposito: SVG carrega
     * script, e esta foto sai num site publico.
     */
    private static final Pattern PHOTO_DATA_URI =
            Pattern.compile("^data:image/(jpeg|png|webp);base64,([A-Za-z0-9+/]+={0,2})$");

    /** Teto do texto antes de decodificar — base64 infla 4/3; nao decodifica lixo gigante. */
    private static final int MAX_PHOTO_CHARS = 40 + (MAX_PHOTO_BYTES * 4 / 3) + 4;

    private static final Pattern INSTAGRAM_HANDLE = Pattern.compile("^@?([A-Za-z0-9._]{1,30})$");

    private ProfessionalProfileRules() {
    }

    /**
     * Valida a foto. Vazio/nulo vira {@code null} (sem foto).
     *
     * @throws IllegalArgumentException se nao for um data URI de imagem jpeg/png/webp em base64,
     *         se passar de {@link #MAX_PHOTO_BYTES}, ou se o conteudo nao for do tipo declarado.
     */
    public static String validatePhoto(String photo) {
        if (photo == null || photo.isBlank()) return null;
        String value = photo.trim();
        if (value.length() > MAX_PHOTO_CHARS) {
            throw new IllegalArgumentException("Foto muito grande. Envie uma imagem de até 60KB.");
        }
        Matcher m = PHOTO_DATA_URI.matcher(value);
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "Foto inválida: envie uma imagem JPEG, PNG ou WebP pelo painel.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(m.group(2));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Foto inválida: conteúdo corrompido.");
        }
        if (bytes.length > MAX_PHOTO_BYTES) {
            throw new IllegalArgumentException("Foto muito grande. Envie uma imagem de até 60KB.");
        }
        if (!magicMatches(m.group(1), bytes)) {
            throw new IllegalArgumentException("Foto inválida: o arquivo não é a imagem que diz ser.");
        }
        return value;
    }

    /** Confere a assinatura do arquivo: um "image/jpeg" que nao comeca como JPEG nao entra. */
    private static boolean magicMatches(String type, byte[] b) {
        return switch (type) {
            case "jpeg" -> b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "png" -> b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "webp" -> b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }

    /**
     * Instagram: URL http(s) ou um @perfil (que vira {@code https://instagram.com/perfil}).
     * Qualquer outro esquema ({@code javascript:}, {@code data:}...) e recusado — o link sai
     * clicavel no site publico.
     */
    public static String validateInstagram(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        Matcher handle = INSTAGRAM_HANDLE.matcher(value);
        if (handle.matches() && !value.contains("/")) {
            return "https://instagram.com/" + handle.group(1);
        }
        if (value.length() > MAX_INSTAGRAM_URL) {
            throw new IllegalArgumentException("Link do Instagram muito longo.");
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Link do Instagram inválido.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("O link do Instagram precisa começar com https://");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Link do Instagram inválido.");
        }
        return value;
    }

    /** Texto curto opcional: aparado, vazio vira nulo, acima do limite e recusado. */
    public static String optionalText(String raw, int max, String field) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if (value.length() > max) {
            throw new IllegalArgumentException(field + " deve ter no máximo " + max + " caracteres.");
        }
        return value;
    }

    /**
     * Especialidades normalizadas para o formato de armazenamento (uma por linha): aparadas,
     * sem duplicatas (ignorando caixa), sem quebras de linha internas. Lista vazia vira nulo.
     */
    public static String normalizeSpecialties(List<String> raw) {
        if (raw == null) return null;
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String s : raw) {
            if (s == null) continue;
            String tag = s.replaceAll("[\\r\\n]+", " ").trim();
            if (tag.isEmpty()) continue;
            if (tag.length() > MAX_SPECIALTY_LENGTH) {
                throw new IllegalArgumentException(
                        "Cada especialidade deve ter no máximo " + MAX_SPECIALTY_LENGTH + " caracteres.");
            }
            if (seen.add(tag.toLowerCase(Locale.ROOT))) out.add(tag);
        }
        if (out.size() > MAX_SPECIALTIES) {
            throw new IllegalArgumentException("Informe no máximo " + MAX_SPECIALTIES + " especialidades.");
        }
        return out.isEmpty() ? null : String.join("\n", out);
    }
}
