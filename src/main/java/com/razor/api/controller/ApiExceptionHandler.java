package com.razor.api.controller;

import com.razor.api.service.NotFoundException;
import com.razor.api.service.UpstreamServiceException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Mapeia excecoes de dominio para respostas HTTP consistentes na API do painel:
 * {@link NotFoundException} → 404, {@link IllegalArgumentException} (validacao de dominio) → 400 e
 * {@link UpstreamServiceException} (servico externo fora do ar) → 502, sempre com corpo
 * {@code {"error": "..."}}. Erros de bean validation continuam 400 pelo padrao do Spring.
 */
@RestControllerAdvice(basePackages = "com.razor.api.controller")
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<Object> handleUpstream(UpstreamServiceException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }
}
