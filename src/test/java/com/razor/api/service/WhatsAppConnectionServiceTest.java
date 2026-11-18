package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.config.RazorProperties;
import com.razor.api.model.Barbershop;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Lógica de resolução do nome da instância (o resto fala com a Evolution e é validado ponta a ponta). */
@ExtendWith(MockitoExtension.class)
class WhatsAppConnectionServiceTest {

    @Mock private WebClient webClient;
    @Mock private RazorProperties properties;
    @Mock private BarbershopRepository barbershopRepository;
    @InjectMocks private WhatsAppConnectionService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("usa a instância já definida no tenant, sem salvar")
    void ensureInstanceName_existing() {
        Barbershop shop = Barbershop.builder()
                .id(TenantContext.getCurrent()).slug("figaro").whatsappInstance("figaro-wa").build();
        when(barbershopRepository.findById(TenantContext.getCurrent())).thenReturn(Optional.of(shop));

        assertThat(service.ensureInstanceName()).isEqualTo("figaro-wa");
        verify(barbershopRepository, never()).save(any());
    }

    @Test
    @DisplayName("deriva a instância do slug e persiste quando ausente")
    void ensureInstanceName_derivesFromSlug() {
        Barbershop shop = Barbershop.builder()
                .id(TenantContext.getCurrent()).slug("figaro").whatsappInstance(null).build();
        when(barbershopRepository.findById(TenantContext.getCurrent())).thenReturn(Optional.of(shop));
        when(barbershopRepository.save(any(Barbershop.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.ensureInstanceName()).isEqualTo("figaro");
        assertThat(shop.getWhatsappInstance()).isEqualTo("figaro");
        verify(barbershopRepository).save(shop);
    }

    @Test
    @DisplayName("barbearia inexistente lança NotFound")
    void ensureInstanceName_notFound() {
        when(barbershopRepository.findById(TenantContext.getCurrent())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ensureInstanceName()).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("slug já usado como instância de OUTRO tenant: deriva nome com sufixo do id, nunca reaproveita")
    void ensureInstanceName_slugTakenByOtherTenant_usesSuffix() {
        UUID id = UUID.fromString("12345678-9abc-def0-1234-56789abcdef0");
        Barbershop shop = Barbershop.builder().id(id).slug("figaro").whatsappInstance(null).build();
        when(barbershopRepository.findById(TenantContext.getCurrent())).thenReturn(Optional.of(shop));
        when(barbershopRepository.findByWhatsappInstance("figaro"))
                .thenReturn(Optional.of(Barbershop.builder().id(UUID.randomUUID()).build()));
        when(barbershopRepository.findByWhatsappInstance("figaro-12345678")).thenReturn(Optional.empty());
        when(barbershopRepository.save(any(Barbershop.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.ensureInstanceName()).isEqualTo("figaro-12345678");
        assertThat(shop.getWhatsappInstance()).isEqualTo("figaro-12345678");
    }

    private WhatsAppConnectionService serviceRespondingWith(HttpStatus status, AtomicReference<ClientRequest> seen) {
        WebClient client = WebClient.builder()
                .baseUrl("http://evolution.test")
                .exchangeFunction(req -> {
                    seen.set(req);
                    return Mono.just(ClientResponse.create(status).build());
                })
                .build();
        Barbershop shop = Barbershop.builder()
                .id(TenantContext.getCurrent()).slug("figaro").whatsappInstance("figaro-wa").build();
        when(barbershopRepository.findById(TenantContext.getCurrent())).thenReturn(Optional.of(shop));
        RazorProperties props = new RazorProperties();
        return new WhatsAppConnectionService(client, props, barbershopRepository);
    }

    @Test
    @DisplayName("disconnect: logout OK não lança")
    void disconnect_ok() {
        AtomicReference<ClientRequest> seen = new AtomicReference<>();
        serviceRespondingWith(HttpStatus.OK, seen).disconnect();
        assertThat(seen.get().url().getPath()).isEqualTo("/instance/logout/figaro-wa");
    }

    @Test
    @DisplayName("disconnect: 404 (instância inexistente) conta como já desconectado")
    void disconnect_notFound_isSuccess() {
        serviceRespondingWith(HttpStatus.NOT_FOUND, new AtomicReference<>()).disconnect();
    }

    @Test
    @DisplayName("disconnect: erro da Evolution sobe como Upstream (não finge sucesso)")
    void disconnect_upstreamError_throws() {
        WhatsAppConnectionService svc = serviceRespondingWith(HttpStatus.INTERNAL_SERVER_ERROR, new AtomicReference<>());
        assertThatThrownBy(svc::disconnect).isInstanceOf(UpstreamServiceException.class);
    }
}
