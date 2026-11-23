package com.razor.api.platform;

import com.razor.api.config.RazorProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Cifra os segredos que o console guarda no banco (chave da OpenAI, segredo do 2FA) com AES-256-GCM
 * e a chave {@code RAZOR_SECRETS_KEY}. Formato: base64(iv[12] || ciphertext+tag). Sem a chave
 * configurada (ou invalida) o console fica desligado — nunca se grava segredo em claro.
 */
@Slf4j
@Component
public class SecretCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public SecretCipher(RazorProperties properties) {
        this(properties.getPlatform().getSecretsKey());
    }

    SecretCipher(String base64Key) {
        SecretKeySpec k = null;
        if (base64Key != null && !base64Key.isBlank()) {
            try {
                byte[] raw = Base64.getDecoder().decode(base64Key.trim());
                if (raw.length == 32) {
                    k = new SecretKeySpec(raw, "AES");
                } else {
                    log.error("[PLATAFORMA] RAZOR_SECRETS_KEY precisa ter 32 bytes em base64 (tem {}) — console desligado", raw.length);
                }
            } catch (IllegalArgumentException e) {
                log.error("[PLATAFORMA] RAZOR_SECRETS_KEY nao e base64 valido — console desligado");
            }
        }
        this.key = k;
    }

    /** Ha chave valida (o console pode funcionar). */
    public boolean enabled() {
        return key != null;
    }

    public String encrypt(String plain) {
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar segredo", e);
        }
    }

    public String decrypt(String encoded) {
        requireKey();
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(c.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Chave trocada ou dado adulterado: nao devolve lixo.
            throw new IllegalStateException("Segredo ilegivel (RAZOR_SECRETS_KEY mudou?)", e);
        }
    }

    private void requireKey() {
        if (key == null) throw new ConsoleDisabledException();
    }

    /** Console sem RAZOR_SECRETS_KEY valida. */
    public static class ConsoleDisabledException extends RuntimeException {
        public ConsoleDisabledException() {
            super("Console da plataforma desligado: defina RAZOR_SECRETS_KEY (32 bytes em base64).");
        }
    }
}
