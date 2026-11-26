package com.razor.api.platform;

import com.razor.api.security.ClientIp;
import com.razor.api.service.TrialFeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Console com controle total (26/11): barbearias e ações, feedback do teste grátis e o relatório
 * organizado. Toda ação que muda algo exige o 2FA ativo e entra na trilha.
 */
@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class PlatformShopsController {

    private final PlatformShopsService shops;
    private final TrialFeedbackService feedback;
    private final PlatformAuditLog audit;

    public record Days(int days) {}
    public record Value(String value) {}

    @GetMapping("/shops-admin")
    public List<Map<String, Object>> list() {
        return shops.list();
    }

    @PostMapping("/shops/{id}/extend-trial")
    public ResponseEntity<Object> extend(@PathVariable UUID id, @RequestBody Days body,
                                         @AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p, HttpServletRequest http) {
        return act(p, http, "SHOP_EXTEND_TRIAL", id, "+" + body.days() + " dias", () -> shops.extendTrial(id, body.days()));
    }

    @PostMapping("/shops/{id}/status")
    public ResponseEntity<Object> status(@PathVariable UUID id, @RequestBody Value body,
                                         @AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p, HttpServletRequest http) {
        return act(p, http, "SHOP_STATUS", id, body.value(), () -> shops.setStatus(id, body.value()));
    }

    @PostMapping("/shops/{id}/plan")
    public ResponseEntity<Object> plan(@PathVariable UUID id, @RequestBody Value body,
                                       @AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p, HttpServletRequest http) {
        return act(p, http, "SHOP_PLAN", id, String.valueOf(body.value()), () -> shops.setPlan(id, body.value()));
    }

    @PostMapping("/shops/{id}/note")
    public ResponseEntity<Object> note(@PathVariable UUID id, @RequestBody Value body,
                                       @AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p, HttpServletRequest http) {
        return act(p, http, "SHOP_NOTE", id, null, () -> shops.setNote(id, body.value()));
    }

    @PostMapping("/shops/{id}/reset-owner-password")
    public ResponseEntity<Object> resetPassword(@PathVariable UUID id,
                                                @AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p, HttpServletRequest http) {
        requireMfa(p);
        try {
            String temp = shops.resetOwnerPassword(id);
            audit.record(p.userId(), "SHOP_RESET_OWNER_PASSWORD", id, null, ClientIp.of(http));
            return ResponseEntity.ok(Map.of("password", temp));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/feedback")
    public Map<String, Object> feedback() {
        return Map.of("answers", feedback.all(), "overdue", feedback.overdue());
    }

    /** Relatório em Markdown para baixar e mandar ao Claude. */
    @GetMapping(value = "/report", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> report(@RequestParam(defaultValue = "7") int days,
                                         @AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p, HttpServletRequest http) {
        requireMfa(p);
        audit.record(p.userId(), "REPORT_EXPORT", null, days + " dias", ClientIp.of(http));
        String name = "razor-relatorio-" + LocalDate.now() + ".md";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .body(shops.report(days));
    }

    private ResponseEntity<Object> act(PlatformSecurity.PlatformPrincipal p, HttpServletRequest http, String action,
                                       UUID id, String target, Runnable r) {
        requireMfa(p);
        try {
            r.run();
            audit.record(p.userId(), action, id, target, ClientIp.of(http));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private static void requireMfa(PlatformSecurity.PlatformPrincipal p) {
        if (p == null || !p.mfaEnabled()) throw new PlatformExceptionHandler.MfaRequiredException();
    }
}
