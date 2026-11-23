package com.razor.api.service;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.Barbershop;
import com.razor.api.model.IncidentType;
import com.razor.api.model.SystemIncident;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.repository.SystemIncidentRepository;
import com.razor.api.tenant.TenantContext;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Incidentes de saude (V50): abrir, somar repeticao, resolver e avisar — sem spam.
 *
 * <p><b>Deduplicacao:</b> um incidente aberto por (barbearia, tipo), garantido tambem pelo indice
 * unico parcial da V50. O mesmo problema visto de novo so soma {@code occurrences}.
 *
 * <p><b>Avisos:</b>
 * <ul>
 *   <li>Painel: sempre — o banner e o cartao "Saude do sistema" leem os incidentes abertos.</li>
 *   <li>WhatsApp do dono/admins: so para os tipos com {@code ownerWhatsApp}, so se o canal
 *       funciona (nenhum incidente aberto que quebre o WhatsApp da casa) e no maximo um aviso a
 *       cada {@code owner-cooldown-hours} por incidente. Quando ele e avisado, tambem recebe o
 *       "resolvido".</li>
 *   <li>Operador: opt-in ({@link OperatorNotifier}), no maximo um aviso a cada
 *       {@code operator-cooldown-minutes} por incidente, e o "resolvido".</li>
 * </ul>
 *
 * <p>Sem {@code @Transactional} de proposito: cada {@code save} e curto e o envio pelo WhatsApp
 * (sincrono, ate 30s) nao pode segurar uma conexao do banco.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentService {

    /** Quanto historico o painel mostra. */
    static final int HISTORY_DAYS = 30;
    static final int HISTORY_LIMIT = 30;

    private final SystemIncidentRepository repository;
    private final BarbershopRepository barbershopRepository;
    private final TenantAdminService tenantAdminService;
    private final WhatsAppService whatsAppService;
    private final OperatorNotifier operatorNotifier;
    private final RazorProperties properties;

    /** Incidente como o painel ve. {@code action}: o que o dono pode fazer (ex.: reconectar). */
    public record IncidentView(UUID id, String type, String status, boolean platform, String message,
                               String detail, LocalDateTime firstSeenAt, LocalDateTime lastSeenAt,
                               LocalDateTime resolvedAt, int occurrences, String action) {
    }

    public record HealthOverview(boolean monitoringEnabled, List<IncidentView> open, List<IncidentView> history) {
    }

    // --- Registro -----------------------------------------------------------------------------

    /**
     * Problema visto agora: abre o incidente (ou soma no aberto) e avisa quem estiver na vez.
     *
     * @param shop barbearia; ignorado (nulo) para tipos de plataforma
     */
    public SystemIncident raise(UUID shop, IncidentType type, String detail) {
        UUID owner = type.platform() ? null : shop;
        LocalDateTime now = LocalDateTime.now(com.razor.api.util.AppointmentDates.ZONE);
        String safeDetail = truncate(detail);
        SystemIncident incident = findOpen(owner, type).map(open -> {
            open.setOccurrences(open.getOccurrences() + 1);
            open.setLastSeenAt(now);
            if (safeDetail != null) open.setDetail(safeDetail);
            return open;
        }).orElseGet(() -> SystemIncident.builder()
                .id(UUID.randomUUID())
                .barbershopId(owner)
                .type(type)
                .status(SystemIncident.Status.OPEN)
                .detail(safeDetail)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .occurrences(1)
                .build());
        boolean isNew = incident.getOccurrences() == 1;
        try {
            incident = repository.save(incident);
        } catch (DataIntegrityViolationException race) {
            // Outro no abriu o mesmo incidente no meio do caminho: vale o dele.
            return findOpen(owner, type).orElseThrow(() -> race);
        }
        if (isNew) {
            log.warn("[MONITOR] Incidente aberto: {} (tenant {}) — {}", type, owner, safeDetail);
        }
        notifyIfDue(incident);
        return incident;
    }

    /**
     * O problema passou: fecha o incidente aberto (se houver) e manda o "resolvido" a quem tinha
     * sido avisado. @return true se havia incidente aberto.
     */
    public boolean resolve(UUID shop, IncidentType type) {
        UUID owner = type.platform() ? null : shop;
        Optional<SystemIncident> open = findOpen(owner, type);
        if (open.isEmpty()) return false;
        SystemIncident incident = open.get();
        incident.setStatus(SystemIncident.Status.RESOLVED);
        incident.setResolvedAt(LocalDateTime.now(com.razor.api.util.AppointmentDates.ZONE));
        repository.save(incident);
        log.info("[MONITOR] Incidente resolvido: {} (tenant {})", type, owner);

        if (incident.getOwnerNotifiedAt() != null && owner != null && canUseWhatsApp(owner)) {
            sendToAdmins(owner, "✅ *Razor — resolvido*\n" + resolvedText(type));
        }
        if (incident.getOperatorNotifiedAt() != null) {
            operatorNotifier.notify("✅ Resolvido: " + operatorText(incident));
        }
        return true;
    }

    public boolean isOpen(UUID shop, IncidentType type) {
        return findOpen(type.platform() ? null : shop, type).isPresent();
    }

    public List<SystemIncident> openIncidents() {
        return repository.findByStatus(SystemIncident.Status.OPEN);
    }

    // --- Leitura do painel --------------------------------------------------------------------

    /** Abertos (para o banner) da barbearia do login, mais os da plataforma. */
    public List<IncidentView> openForCurrentTenant() {
        return repository.findOpenVisibleTo(TenantContext.getCurrent()).stream().map(IncidentService::view).toList();
    }

    public HealthOverview overviewForCurrentTenant() {
        UUID shop = TenantContext.getCurrent();
        List<IncidentView> open = repository.findOpenVisibleTo(shop).stream().map(IncidentService::view).toList();
        List<IncidentView> history = repository.findRecentVisibleTo(shop, LocalDateTime.now(com.razor.api.util.AppointmentDates.ZONE).minusDays(HISTORY_DAYS),
                        PageRequest.of(0, HISTORY_LIMIT)).stream()
                .filter(i -> !i.isOpen())
                .map(IncidentService::view)
                .toList();
        return new HealthOverview(properties.getMonitoring().isEnabled(), open, history);
    }

    static IncidentView view(SystemIncident i) {
        IncidentType t = i.getType();
        String action = switch (t) {
            case WHATSAPP_DISCONNECTED, SEND_FAILURE -> "RECONNECT_WHATSAPP";
            case AI_FAILURE -> "CHECK_AI";
            default -> null;
        };
        return new IncidentView(i.getId(), t.name(), i.getStatus().name(), i.getBarbershopId() == null,
                t.ownerMessage(), i.getDetail(), i.getFirstSeenAt(), i.getLastSeenAt(), i.getResolvedAt(),
                i.getOccurrences(), action);
    }

    // --- Avisos -------------------------------------------------------------------------------

    private void notifyIfDue(SystemIncident incident) {
        LocalDateTime now = LocalDateTime.now(com.razor.api.util.AppointmentDates.ZONE);
        RazorProperties.Monitoring cfg = properties.getMonitoring();
        boolean changed = false;

        UUID shop = incident.getBarbershopId();
        if (shop != null && incident.getType().ownerWhatsApp()
                && due(incident.getOwnerNotifiedAt(), now.minusHours(cfg.getOwnerCooldownHours()))
                && canUseWhatsApp(shop)) {
            String text = "⚠️ *Razor — atenção*\n" + incident.getType().ownerMessage()
                    + "\n_(Detalhes no painel, em Configurações → Status. Próximo aviso sobre isso em no mínimo "
                    + cfg.getOwnerCooldownHours() + "h.)_";
            if (sendToAdmins(shop, text)) {
                incident.setOwnerNotifiedAt(now);
                changed = true;
            }
        }

        if (operatorNotifier.enabled()
                && due(incident.getOperatorNotifiedAt(), now.minusMinutes(cfg.getOperatorCooldownMinutes()))) {
            if (operatorNotifier.notify("⚠️ " + operatorText(incident))) {
                incident.setOperatorNotifiedAt(now);
                changed = true;
            }
        }
        if (changed) repository.save(incident);
    }

    private static boolean due(LocalDateTime lastNotified, LocalDateTime cutoff) {
        return lastNotified == null || lastNotified.isBefore(cutoff);
    }

    /** O WhatsApp da casa funciona: nenhum incidente aberto que quebre o canal. */
    private boolean canUseWhatsApp(UUID shop) {
        return repository.findOpenVisibleTo(shop).stream().noneMatch(i -> i.getType().breaksWhatsApp());
    }

    /** Manda para os admins da barbearia (no contexto dela). @return true se algum recebeu. */
    private boolean sendToAdmins(UUID shop, String text) {
        UUID previous = TenantContext.isSet() ? TenantContext.getCurrent() : null;
        try {
            TenantContext.set(shop);
            boolean any = false;
            for (String phone : new HashSet<>(tenantAdminService.adminPhones())) {
                try {
                    whatsAppService.sendTextMessageSync(phone, text);
                    any = true;
                } catch (Exception e) {
                    log.warn("[MONITOR] Aviso ao admin nao saiu (tenant {}): {}", shop, e.getClass().getSimpleName());
                }
            }
            return any;
        } finally {
            if (previous != null) TenantContext.set(previous);
            else TenantContext.clear();
        }
    }

    private String operatorText(SystemIncident i) {
        String where = i.getBarbershopId() == null ? "plataforma"
                : barbershopRepository.findById(i.getBarbershopId()).map(Barbershop::getSlug).orElse("?")
                        + " (" + i.getBarbershopId() + ")";
        return "Razor [" + i.getType() + "] em " + where + ": " + i.getType().ownerMessage()
                + (i.getDetail() != null ? " — " + i.getDetail() : "")
                + " (ocorrências: " + i.getOccurrences() + ", desde " + i.getFirstSeenAt().withNano(0) + ")";
    }

    private static String resolvedText(IncidentType type) {
        return switch (type) {
            case QUEUE_STUCK -> "Os lembretes atrasados voltaram a sair normalmente.";
            default -> "O problema avisado antes já foi resolvido.";
        };
    }

    private Optional<SystemIncident> findOpen(UUID shop, IncidentType type) {
        return shop == null
                ? repository.findFirstByBarbershopIdIsNullAndTypeAndStatus(type, SystemIncident.Status.OPEN)
                : repository.findFirstByBarbershopIdAndTypeAndStatus(shop, type, SystemIncident.Status.OPEN);
    }

    private static String truncate(String s) {
        if (s == null || s.isBlank()) return null;
        return s.length() <= 300 ? s : s.substring(0, 300);
    }
}
