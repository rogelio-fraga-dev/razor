package com.razor.api.platform;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP (RFC 6238: HMAC-SHA1, 6 digitos, passo de 30 s) — o que Google Authenticator, Authy,
 * 1Password e afins usam. Implementacao propria e pequena para nao puxar dependencia.
 */
public final class Totp {

    static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    /** Aceita o passo anterior e o seguinte: relogio do celular um pouco fora. */
    private static final int WINDOW = 1;
    private static final char[] B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private Totp() {
    }

    /** Segredo novo: 20 bytes aleatorios em base32 (sem padding). */
    public static String newSecret() {
        byte[] b = new byte[20];
        new SecureRandom().nextBytes(b);
        return base32(b);
    }

    public static String otpauthUri(String issuer, String account, String secret) {
        String label = enc(issuer + ":" + account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static long step(Instant at) {
        return at.getEpochSecond() / STEP_SECONDS;
    }

    /**
     * Confere o codigo. Devolve o passo que casou (para bloquear reuso), ou -1.
     *
     * @param lastUsedStep ultimo passo ja aceito; codigos desse passo ou anteriores sao recusados
     */
    public static long verify(String secret, String code, Instant now, Long lastUsedStep) {
        if (code == null) return -1;
        String c = code.replaceAll("\\s", "");
        if (!c.matches("\\d{" + DIGITS + "}")) return -1;
        long current = step(now);
        for (long s = current - WINDOW; s <= current + WINDOW; s++) {
            if (lastUsedStep != null && s <= lastUsedStep) continue;
            if (constantTimeEquals(code(secret, s), c)) return s;
        }
        return -1;
    }

    static String code(String secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(unbase32(secret), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP indisponivel", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(B32[(buffer >> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(B32[(buffer << (5 - bits)) & 31]);
        return sb.toString();
    }

    static byte[] unbase32(String s) {
        String t = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(t.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char ch : t.toCharArray()) {
            int v = ch >= 'A' && ch <= 'Z' ? ch - 'A' : ch >= '2' && ch <= '7' ? ch - '2' + 26 : -1;
            if (v < 0) throw new IllegalArgumentException("base32 invalido");
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }
}
