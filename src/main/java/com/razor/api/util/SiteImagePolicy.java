package com.razor.api.util;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regra das fotos que o dono sobe para o site (V45). Funcoes puras, testaveis sem Spring.
 *
 * <p><b>O tipo vem dos BYTES, nunca do que o navegador declarou.</b> A foto e servida na mesma
 * origem do painel: um HTML ou SVG renomeado para {@code .jpg}, servido com o tipo que o cliente
 * escolheu, viraria script rodando com a sessao de quem abrisse o link. Entao so passa o que
 * comeca com a assinatura de JPEG, PNG ou WebP, e o content-type gravado e o detectado. SVG nao
 * entra de jeito nenhum (e texto, carrega script).
 *
 * <p>Formato de entrada: data URI ({@code data:...;base64,...}) ou base64 puro. O painel ja manda
 * a imagem redimensionada (lado maior ate 1600px, ~0,82 de qualidade), entao 2MB sobra.
 */
public final class SiteImagePolicy {

    /** Teto da imagem decodificada. */
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    /** Teto de fotos guardadas por barbearia (galeria de 12 + logo + capa + folga para trocas). */
    public static final int MAX_IMAGES_PER_SHOP = 40;

    /** Base64 infla 4/3; acima disto nem tenta decodificar (nao aloca lixo gigante). */
    private static final int MAX_CHARS = 128 + (MAX_BYTES / 3 + 1) * 4;

    private static final Pattern DATA_URI = Pattern.compile("^data:[^,]{0,100};base64,(.*)$", Pattern.DOTALL);

    private SiteImagePolicy() {
    }

    /** Imagem aceita: bytes e o tipo detectado. */
    public record Decoded(byte[] bytes, String contentType) {
    }

    /**
     * Decodifica e valida a foto.
     *
     * @throws IllegalArgumentException com mensagem para o dono quando vazia, grande demais,
     *         corrompida ou de formato fora de JPEG/PNG/WebP.
     */
    public static Decoded decode(String data) {
        if (data == null || data.isBlank()) {
            throw new IllegalArgumentException("Nenhuma foto recebida.");
        }
        if (data.length() > MAX_CHARS) {
            throw new IllegalArgumentException(tooBig());
        }
        String b64 = data.trim();
        Matcher m = DATA_URI.matcher(b64);
        // O tipo declarado no data URI e ignorado de proposito — so o conteudo decide.
        if (m.matches()) b64 = m.group(1);
        else if (b64.startsWith("data:")) throw new IllegalArgumentException("Foto inválida: envie uma imagem JPEG, PNG ou WebP.");

        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Foto inválida: o arquivo chegou corrompido.");
        }
        if (bytes.length == 0) throw new IllegalArgumentException("Nenhuma foto recebida.");
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException(tooBig());

        String type = detect(bytes);
        if (type == null) {
            throw new IllegalArgumentException("Formato não aceito: envie uma foto JPEG, PNG ou WebP.");
        }
        return new Decoded(bytes, type);
    }

    /** O tipo pela assinatura do arquivo, ou {@code null} se nao for JPEG, PNG nem WebP. */
    public static String detect(byte[] b) {
        if (b == null) return null;
        if (b.length >= 3 && u(b[0]) == 0xFF && u(b[1]) == 0xD8 && u(b[2]) == 0xFF) return "image/jpeg";
        if (b.length >= 8 && u(b[0]) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return "image/png";
        if (b.length >= 16 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P'
                && b[12] == 'V' && b[13] == 'P' && b[14] == '8') return "image/webp";
        return null;
    }

    /** Tipos que o endpoint publico serve — qualquer outro valor no banco vira 404, nunca sai. */
    public static boolean isServable(String contentType) {
        return "image/jpeg".equals(contentType) || "image/png".equals(contentType) || "image/webp".equals(contentType);
    }

    private static int u(byte x) {
        return x & 0xFF;
    }

    private static String tooBig() {
        return "Foto muito grande: o limite é " + (MAX_BYTES / (1024 * 1024)) + "MB por imagem.";
    }
}
