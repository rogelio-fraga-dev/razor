package com.razor.api.controller;

import com.razor.api.service.IncidentService;
import com.razor.api.service.IncidentService.HealthOverview;
import com.razor.api.service.IncidentService.IncidentView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Saude do sistema no painel (V50). Tenant vem do login; a barbearia ve os proprios incidentes e
 * os da plataforma (que valem para todas), nunca os de outra casa.
 *
 * <ul>
 *   <li>{@code GET /api/admin/system-health} — abertos + historico (cartao em Configuracoes →
 *       Status). So o dono, pelo fecho geral de {@code /api/admin/**}.</li>
 *   <li>{@code GET /api/admin/system-health/alerts} — so os abertos, para o banner do painel.
 *       Dono e recepcao (SecurityConfig): quem esta no balcao precisa saber que o WhatsApp caiu.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/system-health")
@RequiredArgsConstructor
public class AdminSystemHealthController {

    private final IncidentService incidentService;

    @GetMapping
    public HealthOverview overview() {
        return incidentService.overviewForCurrentTenant();
    }

    @GetMapping("/alerts")
    public List<IncidentView> alerts() {
        return incidentService.openForCurrentTenant();
    }
}
