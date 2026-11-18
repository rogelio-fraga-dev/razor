package com.razor.api.controller;

import com.razor.api.service.ConflictException;
import com.razor.api.service.BookingConflictException;
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
 * {@link ConflictException} → 409, {@link UpstreamServiceException} (servico externo fora do ar) → 502, sempre com corpo
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

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(BookingConflictException.class)
    public ResponseEntity<Object> handleBookingConflict(BookingConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    /** Limite de horarios por cliente (V36) → 409: o pedido e valido, o estado da agenda nao deixa. */
    @ExceptionHandler(com.razor.api.service.AgendaBookingService.BookingLimitExceededException.class)
    public ResponseEntity<Object> handleBookingLimit(
            com.razor.api.service.AgendaBookingService.BookingLimitExceededException e) {
        var d = e.detail();
        String msg = "Você já tem " + d.upcoming().size() + " horário(s) marcado(s) e o limite desta barbearia é "
                + d.limit() + ". Desmarque um deles para agendar outro.";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", msg, "limit", d.limit()));
    }

    /**
     * O indice unico de horario (V43) recusou a gravacao no commit: alguem ocupou o mesmo
     * barbeiro/horario. Mesmo 409 "horario ocupado" do caminho normal. Outras violacoes de
     * integridade seguem o tratamento padrao (500) — esconde-las como 409 mascararia bug.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleIntegrity(org.springframework.dao.DataIntegrityViolationException e) {
        if (com.razor.api.service.BookingLock.isSlotConflict(e)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Esse horário acabou de ser ocupado. Escolha outro."));
        }
        throw e;
    }

    /** Fila de disparos cheia: o pedido e valido, so nao cabe agora. */
    @ExceptionHandler(org.springframework.core.task.TaskRejectedException.class)
    public ResponseEntity<Object> handleRejected(org.springframework.core.task.TaskRejectedException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Já há disparos demais na fila. Tente de novo em alguns minutos."));
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<Object> handleUpstream(UpstreamServiceException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }
}
