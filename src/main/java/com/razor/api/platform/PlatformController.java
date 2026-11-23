package com.razor.api.platform;

import com.razor.api.dto.WhatsAppConnectResponse;
import com.razor.api.dto.WhatsAppStatusResponse;
import com.razor.api.security.ClientIp;
import com.razor.api.service.WhatsAppConnectionService;
import com.razor.api.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Console da plataforma (V51). So com a sessao do console ({@code ROLE_PLATFORM_ADMIN}, ver
 * SecurityConfig). Le dados de todas as barbearias; o que e sensivel (conversa com telefone,
 * mudanca da chave da IA, reconectar WhatsApp de uma casa) entra na trilha.
 */
@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class PlatformController {

    private final PlatformConsoleService console;
    private final PlatformAiSettingsService aiSettings;
    private final PlatformAuditLog audit;
    private final WhatsAppConnectionService whatsApp;

    // ------------------------------------------------------------------ visao geral e saude

    @GetMapping("/overview")
    public PlatformConsoleService.Overview overview(@RequestParam(defaultValue = "30") int days) {
        return console.overview(days);
    }

    @GetMapping("/incidents")
    public List<PlatformConsoleService.IncidentRow> incidents(@RequestParam(defaultValue = "30") int days) {
        return console.incidents(days);
    }

    @GetMapping("/shops/{id}/whatsapp")
    public WhatsAppStatusResponse whatsappStatus(@PathVariable UUID id) {
        console.shopInstance(id); // 404 se nao existe
        return inTenant(id, whatsApp::status);
    }

    /** Gera o QR/codigo de pareamento da barbearia (o dono le no celular dele). */
    @PostMapping("/shops/{id}/whatsapp/connect")
    public WhatsAppConnectResponse whatsappConnect(@PathVariable UUID id,
                                                   @AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p,
                                                   HttpServletRequest http) {
        requireMfa(p);
        console.shopInstance(id);
        audit.record(p.userId(), "WHATSAPP_CONNECT", id, null, ClientIp.of(http));
        return inTenant(id, whatsApp::connect);
    }

    // ------------------------------------------------------------------ IA

    @GetMapping("/ai")
    public PlatformAiSettingsService.View ai() {
        return aiSettings.view();
    }

    public record AiUpdate(@Size(max = 300) String apiKey, boolean clearKey, @Size(max = 60) String chatModel,
                           @Size(max = 60) String escalationModel, @Size(max = 60) String transcribeModel) {
    }

    @PutMapping("/ai")
    public PlatformAiSettingsService.View updateAi(@Valid @RequestBody AiUpdate req,
                                                   @AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p,
                                                   HttpServletRequest http) {
        requireMfa(p);
        var view = aiSettings.update(new PlatformAiSettingsService.Update(req.apiKey(), req.clearKey(), req.chatModel(),
                req.escalationModel(), req.transcribeModel()), p.userId());
        String what = (req.apiKey() != null && !req.apiKey().isBlank()) ? "nova chave ..." + view.keyLast4()
                : req.clearKey() ? "chave do ambiente" : "modelos";
        audit.record(p.userId(), "AI_SETTINGS", null, what, ClientIp.of(http));
        return view;
    }

    public record AiTest(@Size(max = 300) String apiKey) {
    }

    @PostMapping("/ai/test")
    public PlatformAiSettingsService.TestResult testAi(@Valid @RequestBody(required = false) AiTest req,
                                                       @AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p) {
        requireMfa(p);
        return aiSettings.test(req == null ? null : req.apiKey());
    }

    // ------------------------------------------------------------------ conversas

    @GetMapping("/conversations")
    public List<PlatformConsoleService.ConversationRow> conversations(
            @RequestParam(required = false) UUID shop,
            @RequestParam(defaultValue = "ALL") PlatformConsoleService.ConversationFilter filter,
            @RequestParam(defaultValue = "7") int days) {
        return console.conversations(shop, filter, days);
    }

    /** Conversa completa, com o telefone: cada leitura fica na trilha (LGPD). */
    @GetMapping("/conversations/{customerId}")
    public PlatformConsoleService.ConversationDetail conversation(@PathVariable UUID customerId,
                                                                  @RequestParam(defaultValue = "30") int days,
                                                                  @AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p,
                                                                  HttpServletRequest http) {
        requireMfa(p);
        var detail = console.conversation(customerId, days);
        audit.record(p.userId(), "READ_CONVERSATION", detail.shopId(), "cliente " + customerId, ClientIp.of(http));
        return detail;
    }

    public record FlagRequest(@Size(max = 500) String note) {
    }

    @PostMapping("/conversations/{customerId}/flag")
    public ResponseEntity<Void> flag(@PathVariable UUID customerId, @Valid @RequestBody(required = false) FlagRequest req,
                                     @AuthenticationPrincipal PlatformSecurity.PlatformPrincipal p) {
        console.flag(customerId, req == null ? null : req.note(), p.userId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/conversations/{customerId}/flag")
    public ResponseEntity<Void> unflag(@PathVariable UUID customerId) {
        console.resolveFlag(customerId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ metricas, caixa, trilha

    @GetMapping("/metrics")
    public List<PlatformConsoleService.DayPoint> metrics(@RequestParam(defaultValue = "30") int days,
                                                        @RequestParam(required = false) UUID shop) {
        return console.metrics(days, shop);
    }

    @GetMapping("/revenue")
    public PlatformConsoleService.Revenue revenue(@RequestParam(defaultValue = "12") int months) {
        return console.revenue(months);
    }

    public record RevenueRequest(@NotNull @Pattern(regexp = "\\d{4}-\\d{2}") String month, UUID barbershopId,
                                 @Size(max = 40) String plan, @NotNull @DecimalMin("0.00") BigDecimal amount,
                                 @Size(max = 200) String note) {
    }

    @PostMapping("/revenue")
    public Map<String, UUID> addRevenue(@Valid @RequestBody RevenueRequest req) {
        YearMonth m;
        try {
            m = YearMonth.parse(req.month());
        } catch (Exception e) {
            throw new IllegalArgumentException("Mês inválido (use AAAA-MM).");
        }
        return Map.of("id", console.addRevenue(m, req.barbershopId(), req.plan(), req.amount(), req.note()));
    }

    @DeleteMapping("/revenue/{id}")
    public ResponseEntity<Void> deleteRevenue(@PathVariable UUID id) {
        console.deleteRevenue(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit")
    public List<PlatformAuditLog.Entry> audit(@RequestParam(defaultValue = "100") int limit) {
        return audit.recent(Math.max(1, Math.min(limit, 500)));
    }

    /** Telefone de cliente, chave da IA e pareamento de WhatsApp so com o 2FA ativo (V56). */
    private static void requireMfa(PlatformSecurity.PlatformPrincipal p) {
        if (p == null || !p.mfaEnabled()) throw new PlatformExceptionHandler.MfaRequiredException();
    }

    private static <T> T inTenant(UUID shop, Supplier<T> action) {
        TenantContext.set(shop);
        try {
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }
}
