package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.IncidentType;
import com.razor.api.model.SystemIncident;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.repository.SystemIncidentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class IncidentServiceTest {

    private final SystemIncidentRepository repo = mock(SystemIncidentRepository.class);
    private final TenantAdminService admins = mock(TenantAdminService.class);
    private final WhatsAppService whatsApp = mock(WhatsAppService.class);
    private final OperatorNotifier operator = mock(OperatorNotifier.class);
    private final RazorProperties props = new RazorProperties();
    private final UUID shop = UUID.randomUUID();
    private IncidentService service;

    @BeforeEach
    void setUp() {
        service = new IncidentService(repo, mock(BarbershopRepository.class), admins, whatsApp, operator, props);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(repo.findFirstByBarbershopIdAndTypeAndStatus(any(), any(), any())).thenReturn(Optional.empty());
        when(repo.findFirstByBarbershopIdIsNullAndTypeAndStatus(any(), any())).thenReturn(Optional.empty());
        when(repo.findOpenVisibleTo(any())).thenReturn(List.of());
        when(admins.adminPhones()).thenReturn(List.of("5511900000001"));
    }

    private SystemIncident open(IncidentType type, UUID owner) {
        return SystemIncident.builder().id(UUID.randomUUID()).barbershopId(owner).type(type)
                .status(SystemIncident.Status.OPEN).firstSeenAt(LocalDateTime.now()).lastSeenAt(LocalDateTime.now())
                .occurrences(1).build();
    }

    @Test
    @DisplayName("primeira ocorrencia abre o incidente e avisa o dono pelo WhatsApp (tipo que avisa)")
    void raise_opensAndNotifies() {
        SystemIncident i = service.raise(shop, IncidentType.QUEUE_STUCK, "2 tarefa(s) atrasada(s)");
        assertThat(i.getStatus()).isEqualTo(SystemIncident.Status.OPEN);
        assertThat(i.getOwnerNotifiedAt()).isNotNull();
        verify(whatsApp).sendTextMessageSync(eq("5511900000001"), contains("atrasados"));
    }

    @Test
    @DisplayName("repeticao soma ocorrencias e nao avisa de novo dentro do intervalo minimo")
    void raise_dedupAndCooldown() {
        SystemIncident ja = open(IncidentType.QUEUE_STUCK, shop);
        ja.setOwnerNotifiedAt(LocalDateTime.now().minusMinutes(10));
        when(repo.findFirstByBarbershopIdAndTypeAndStatus(shop, IncidentType.QUEUE_STUCK, SystemIncident.Status.OPEN))
                .thenReturn(Optional.of(ja));
        SystemIncident i = service.raise(shop, IncidentType.QUEUE_STUCK, "x");
        assertThat(i.getOccurrences()).isEqualTo(2);
        verify(whatsApp, never()).sendTextMessageSync(anyString(), anyString());
    }

    @Test
    @DisplayName("WhatsApp desconectado nao e avisado pelo proprio WhatsApp; com canal quebrado, nenhum aviso sai por ele")
    void raise_channelBroken_noWhatsApp() {
        service.raise(shop, IncidentType.WHATSAPP_DISCONNECTED, "sessão encerrada");
        verify(whatsApp, never()).sendTextMessageSync(anyString(), anyString());

        when(repo.findOpenVisibleTo(shop)).thenReturn(List.of(open(IncidentType.SEND_FAILURE, shop)));
        service.raise(shop, IncidentType.QUEUE_STUCK, "x");
        verify(whatsApp, never()).sendTextMessageSync(anyString(), anyString());
    }

    @Test
    @DisplayName("incidente de plataforma fica sem barbearia e avisa o operador quando ligado")
    void platform_operator() {
        when(operator.enabled()).thenReturn(true);
        when(operator.notify(anyString())).thenReturn(true);
        SystemIncident i = service.raise(shop, IncidentType.SCHEDULER_STALLED, "sem rodar há 7 min");
        assertThat(i.getBarbershopId()).isNull();
        assertThat(i.getOperatorNotifiedAt()).isNotNull();
        verify(operator).notify(contains("SCHEDULER_STALLED"));
    }

    @Test
    @DisplayName("corrida no indice unico: devolve o incidente aberto pelo outro no")
    void raise_race() {
        SystemIncident outro = open(IncidentType.QUEUE_STUCK, shop);
        when(repo.findFirstByBarbershopIdAndTypeAndStatus(shop, IncidentType.QUEUE_STUCK, SystemIncident.Status.OPEN))
                .thenReturn(Optional.empty(), Optional.of(outro));
        doThrow(new DataIntegrityViolationException("uq")).when(repo).save(any());
        assertThat(service.raise(shop, IncidentType.QUEUE_STUCK, "x")).isSameAs(outro);
    }

    @Test
    @DisplayName("resolver fecha e manda o 'resolvido' so para quem tinha sido avisado")
    void resolve() {
        SystemIncident ja = open(IncidentType.QUEUE_STUCK, shop);
        ja.setOwnerNotifiedAt(LocalDateTime.now().minusHours(1));
        when(repo.findFirstByBarbershopIdAndTypeAndStatus(shop, IncidentType.QUEUE_STUCK, SystemIncident.Status.OPEN))
                .thenReturn(Optional.of(ja));
        assertThat(service.resolve(shop, IncidentType.QUEUE_STUCK)).isTrue();
        assertThat(ja.getStatus()).isEqualTo(SystemIncident.Status.RESOLVED);
        assertThat(ja.getResolvedAt()).isNotNull();
        verify(whatsApp).sendTextMessageSync(eq("5511900000001"), contains("resolvido"));
        verify(operator, never()).notify(anyString());

        assertThat(service.resolve(shop, IncidentType.AI_FAILURE)).isFalse();
    }

    @Test
    @DisplayName("visao do painel: acao sugerida e marca de plataforma")
    void view() {
        var v = IncidentService.view(open(IncidentType.WHATSAPP_DISCONNECTED, shop));
        assertThat(v.action()).isEqualTo("RECONNECT_WHATSAPP");
        assertThat(v.platform()).isFalse();
        assertThat(IncidentService.view(open(IncidentType.EVOLUTION_UNAVAILABLE, null)).platform()).isTrue();
    }

    @Test
    @DisplayName("classificacao do erro da IA nunca devolve o texto cru (pode ter pedaco da chave)")
    void classifyAiError() {
        assertThat(HealthSignals.classifyAiError(new RuntimeException("Incorrect API key provided: sk-abc***")))
                .isEqualTo(HealthSignals.AI_AUTH);
        assertThat(HealthSignals.classifyAiError(new RuntimeException("x", new RuntimeException("insufficient_quota"))))
                .isEqualTo(HealthSignals.AI_QUOTA);
        assertThat(HealthSignals.classifyAiError(new RuntimeException("Request timed out"))).isEqualTo(HealthSignals.AI_TIMEOUT);
        assertThat(HealthSignals.classifyAiError(new RuntimeException("whatever sk-123"))).isEqualTo(HealthSignals.AI_OTHER);
    }
}
