package com.razor.api.controller;

import com.razor.api.model.UserRole;
import com.razor.api.security.TenantUserPrincipal;
import com.razor.api.service.TrialFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Feedback obrigatório do teste grátis (V57): só o dono responde. */
@RestController
@RequestMapping("/api/admin/trial-feedback")
@RequiredArgsConstructor
public class AdminTrialFeedbackController {

    private final TrialFeedbackService feedback;

    @GetMapping("/pending")
    public ResponseEntity<Object> pending(@AuthenticationPrincipal TenantUserPrincipal p) {
        if (p == null || p.role() != UserRole.OWNER) return ResponseEntity.noContent().build();
        return feedback.pending(p.barbershopId())
                .filter(x -> x.blocking() || x.canSnooze())
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/snooze")
    public ResponseEntity<Object> snooze(@AuthenticationPrincipal TenantUserPrincipal p, @RequestBody Map<String, String> body) {
        if (p == null || p.role() != UserRole.OWNER) return ResponseEntity.status(403).build();
        return feedback.snooze(p.barbershopId(), body.getOrDefault("stage", ""))
                ? ResponseEntity.noContent().build()
                : ResponseEntity.badRequest().body(Map.of("error", "Esta etapa já foi adiada uma vez."));
    }

    @PostMapping
    public ResponseEntity<Object> answer(@AuthenticationPrincipal TenantUserPrincipal p, @RequestBody TrialFeedbackService.Answer a) {
        if (p == null || p.role() != UserRole.OWNER) return ResponseEntity.status(403).build();
        try {
            feedback.answer(p.barbershopId(), p.userId(), a);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
