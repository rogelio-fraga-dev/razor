package com.razor.api.service;

import com.razor.api.dto.AutomationSettingsPayload;
import com.razor.api.model.AutomationSend;
import com.razor.api.model.AutomationSettings;
import com.razor.api.model.ServiceOffering;
import com.razor.api.model.WaitlistEntry;
import com.razor.api.repository.AutomationSendRepository;
import com.razor.api.repository.AutomationSettingsRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.repository.WaitlistRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Leitura e gravacao da aba Automacoes (V47). Sem linha = padroes do sistema. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationSettingsService {

    private final AutomationSettingsRepository repository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final AutomationSendRepository sendRepository;
    private final WaitlistRepository waitlistRepository;

    /** Configuracao do tenant atual (ou os padroes, sem persistir). */
    public AutomationSettings get() {
        return getFor(TenantContext.getCurrent());
    }

    public AutomationSettings getFor(UUID barbershopId) {
        return repository.findById(barbershopId).orElseGet(() -> AutomationSettings.defaults(barbershopId));
    }

    public AutomationSettingsPayload payload() {
        UUID shop = TenantContext.getCurrent();
        AutomationSettings s = getFor(shop);
        List<AutomationSettingsPayload.ServiceReturn> services = serviceOfferingRepository.findByBarbershopId(shop)
                .stream()
                .filter(ServiceOffering::isActive)
                .sorted(Comparator.comparing(ServiceOffering::getName, String.CASE_INSENSITIVE_ORDER))
                .map(o -> new AutomationSettingsPayload.ServiceReturn(o.getId(), o.getName(), o.getReturnReminderDays()))
                .toList();
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("LAPSED", AutomationTemplates.DEFAULT_LAPSED);
        defaults.put("BIRTHDAY", AutomationTemplates.DEFAULT_BIRTHDAY);
        defaults.put("RETURN", AutomationTemplates.DEFAULT_RETURN);

        LocalDateTime since = LocalDateTime.now(AppointmentDates.ZONE).minusDays(30);
        var stats = new AutomationSettingsPayload.Stats(
                sent(shop, AutomationSend.Kind.LAPSED, since),
                sent(shop, AutomationSend.Kind.BIRTHDAY, since),
                sent(shop, AutomationSend.Kind.RETURN, since),
                waitlistRepository.countByBarbershopIdAndStatus(shop, WaitlistEntry.Status.WAITING));

        return new AutomationSettingsPayload(
                s.isWaitlistEnabled(), s.getWaitlistWindowMinutes(),
                s.isLapsedEnabled(), s.getLapsedDays(),
                AutomationTemplates.effective(s.getLapsedTemplate(), AutomationSend.Kind.LAPSED),
                s.isBirthdayEnabled(),
                AutomationTemplates.effective(s.getBirthdayTemplate(), AutomationSend.Kind.BIRTHDAY),
                s.getBirthdayDiscountPercent(), s.getBirthdayCouponValidDays(),
                s.isReturnEnabled(),
                AutomationTemplates.effective(s.getReturnTemplate(), AutomationSend.Kind.RETURN),
                s.getFrequencyCapDays(),
                s.isWeeklyReportEnabled(), s.getWeeklyReportDay(), s.getWeeklyReportHour(),
                services, defaults, stats);
    }

    @Transactional
    public AutomationSettingsPayload update(AutomationSettingsPayload p) {
        UUID shop = TenantContext.getCurrent();
        AutomationSettings s = repository.findById(shop).orElseGet(() -> AutomationSettings.defaults(shop));
        s.setWaitlistEnabled(p.waitlistEnabled());
        s.setWaitlistWindowMinutes(p.waitlistWindowMinutes());
        s.setLapsedEnabled(p.lapsedEnabled());
        s.setLapsedDays(p.lapsedDays());
        s.setLapsedTemplate(customOrNull(p.lapsedTemplate(), AutomationSend.Kind.LAPSED));
        s.setBirthdayEnabled(p.birthdayEnabled());
        s.setBirthdayTemplate(customOrNull(p.birthdayTemplate(), AutomationSend.Kind.BIRTHDAY));
        s.setBirthdayDiscountPercent(p.birthdayDiscountPercent().setScale(2, RoundingMode.HALF_UP));
        s.setBirthdayCouponValidDays(p.birthdayCouponValidDays());
        s.setReturnEnabled(p.returnEnabled());
        s.setReturnTemplate(customOrNull(p.returnTemplate(), AutomationSend.Kind.RETURN));
        s.setFrequencyCapDays(p.frequencyCapDays());
        s.setWeeklyReportEnabled(p.weeklyReportEnabled());
        s.setWeeklyReportDay(p.weeklyReportDay());
        s.setWeeklyReportHour(p.weeklyReportHour());
        repository.save(s);

        if (p.services() != null && !p.services().isEmpty()) {
            Map<UUID, ServiceOffering> doTenant = serviceOfferingRepository.findByBarbershopId(shop).stream()
                    .collect(Collectors.toMap(ServiceOffering::getId, Function.identity()));
            for (var sr : p.services()) {
                ServiceOffering o = doTenant.get(sr.id());
                if (o == null) continue; // servico de outro tenant (ou apagado): ignora
                Integer dias = sr.returnReminderDays();
                if (dias != null && (dias < 7 || dias > 180)) {
                    throw new IllegalArgumentException("Intervalo de retorno deve ficar entre 7 e 180 dias.");
                }
                if (!java.util.Objects.equals(o.getReturnReminderDays(), dias)) {
                    o.setReturnReminderDays(dias);
                    serviceOfferingRepository.save(o);
                }
            }
        }
        log.info("[AUTOMACOES] Configuracoes atualizadas no tenant {}", shop);
        return payload();
    }

    /** Modelo igual ao padrao (ou vazio) nao e gravado: assim melhorias no padrao chegam a todos. */
    private static String customOrNull(String template, AutomationSend.Kind kind) {
        if (template == null || template.isBlank()) return null;
        String t = template.strip();
        return t.equals(AutomationTemplates.defaultFor(kind).strip()) ? null : t;
    }

    private long sent(UUID shop, AutomationSend.Kind kind, LocalDateTime since) {
        return sendRepository.countByBarbershopIdAndKindAndStatusAndSentAtGreaterThanEqual(
                shop, kind, AutomationSend.Status.SENT, since);
    }
}
