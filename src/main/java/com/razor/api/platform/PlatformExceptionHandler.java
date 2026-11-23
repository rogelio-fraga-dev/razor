package com.razor.api.platform;

import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erros do console no mesmo formato do painel: {@code {"error": "..."}}. */
@RestControllerAdvice(basePackages = "com.razor.api.platform")
public class PlatformExceptionHandler {

    @ExceptionHandler(SecretCipher.ConsoleDisabledException.class)
    public ResponseEntity<Object> disabled(SecretCipher.ConsoleDisabledException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
    }

    /** Acao sensivel sem 2FA ativo (V56). */
    public static class MfaRequiredException extends RuntimeException {
        public MfaRequiredException() {
            super("Ative a verificação em duas etapas (aba Segurança) para usar esta função.");
        }
    }

    @ExceptionHandler(MfaRequiredException.class)
    public ResponseEntity<Object> mfaRequired(MfaRequiredException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage(), "code", "MFA_REQUIRED"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Object> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Não encontrado."));
    }
}
