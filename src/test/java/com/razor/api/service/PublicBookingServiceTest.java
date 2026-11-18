package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.dto.PublicBookingDtos;
import com.razor.api.model.Appointment;
import com.razor.api.model.AppointmentSource;
import com.razor.api.model.Barbershop;
import com.razor.api.model.BarbershopSite;
import com.razor.api.model.Professional;
import com.razor.api.model.ServiceOffering;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.repository.BarbershopRepository;
import com.razor.api.repository.BarbershopSiteRepository;
import com.razor.api.repository.ProfessionalRepository;
import com.razor.api.repository.ServiceOfferingRepository;
import com.razor.api.tenant.TenantContext;
import com.razor.api.util.AppointmentDates;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Agendamento online pela página pública")
class PublicBookingServiceTest {

    private static final UUID SHOP = UUID.randomUUID();
    private static final UUID OUTRA = UUID.randomUUID();
    private static final UUID CORTE = UUID.randomUUID();
    private static final UUID ANA = UUID.randomUUID();

    @Mock private BarbershopRepository barbershopRepository;
    @Mock private BarbershopSiteRepository siteRepository;
    @Mock private ServiceOfferingRepository serviceRepository;
    @Mock private ProfessionalRepository professionalRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private BarbershopSettingsService settingsService;
    @Mock private AgendaAvailabilityService availabilityService;
    @Mock private AgendaBookingService bookingService;
    @Mock private CustomerService customerService;
    @Mock private TenantAdminService tenantAdminService;
    @Mock private WhatsAppService whatsAppService;

    @org.mockito.Spy private BookingLock bookingLock = BookingLock.inline();
    @InjectMocks private PublicBookingService service;

    private BarbershopSite site;
    private LocalDate dia;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        when(barbershopRepository.findBySlug("figaro"))
                .thenReturn(Optional.of(Barbershop.builder().id(SHOP).slug("figaro").name("Figaro").active(true).build()));
        site = BarbershopSite.defaultsFor(SHOP);
        site.setPublished(true);
        site.setOnlineBookingEnabled(true);
        when(siteRepository.findById(SHOP)).thenReturn(Optional.of(site));
        var cfg = new BarbershopSettingsService.BotSettings("", "", "", null, "");
        when(settingsService.resolveFor(SHOP)).thenReturn(cfg);
        when(settingsService.resolve()).thenReturn(cfg);
        when(serviceRepository.findById(CORTE)).thenReturn(Optional.of(ServiceOffering.builder()
                .id(CORTE).barbershopId(SHOP).name("Corte").price(new BigDecimal("35")).durationMinutes(30).active(true).build()));
        when(professionalRepository.findById(ANA)).thenReturn(Optional.of(Professional.builder()
                .id(ANA).barbershopId(SHOP).name("Ana").active(true).build()));
        when(tenantAdminService.adminPhones()).thenReturn(List.of("5511911112222"));
        dia = AppointmentDates.today().plusDays(2);
        when(availabilityService.horariosLivres(eq(dia), anyInt(), any(), any())).thenReturn(List.of(LocalTime.of(10, 0)));
        when(bookingService.agendar(any())).thenAnswer(i -> {
            AgendaBookingService.NovoAgendamento n = i.getArgument(0);
            return Optional.of(Appointment.builder().barbershopId(SHOP).customerName(n.name()).customerPhone(n.phone())
                    .appointmentDate(n.date()).appointmentTime(n.time()).service(n.service())
                    .professionalId(n.preferredProfessionalId()).source(n.source()).build());
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private PublicBookingDtos.Request pedido(String phone, String website) {
        return new PublicBookingDtos.Request(CORTE, ANA, dia, "10:00", "João da Silva", phone, website);
    }

    @Test
    @DisplayName("agendamento desligado ou site não publicado responde 404 igual")
    void disabledIs404() {
        site.setOnlineBookingEnabled(false);
        assertThatThrownBy(() -> service.options("figaro")).isInstanceOf(NotFoundException.class);
        site.setOnlineBookingEnabled(true);
        site.setPublished(false);
        assertThatThrownBy(() -> service.options("figaro")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.options("nao-existe")).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("grade expõe só os horários, e com a barbearia do slug no contexto")
    void slotsOnly() {
        PublicBookingDtos.Slots s = service.slots("figaro", CORTE, null, dia);
        assertThat(s.slots()).containsExactly("10:00");
        verify(availabilityService).horariosLivres(dia, 30, null, null);
        assertThat(TenantContext.isSet()).isFalse(); // restaurado ao sair
    }

    @Test
    @DisplayName("data fora da janela não consulta a agenda")
    void slotsOutsideWindow() {
        assertThat(service.slots("figaro", CORTE, null, AppointmentDates.today().minusDays(1)).slots()).isEmpty();
        assertThat(service.slots("figaro", CORTE, null, AppointmentDates.today().plusDays(60)).slots()).isEmpty();
    }

    @Test
    @DisplayName("serviço de outra barbearia é recusado")
    void crossTenantService() {
        when(serviceRepository.findById(CORTE)).thenReturn(Optional.of(ServiceOffering.builder()
                .id(CORTE).barbershopId(OUTRA).name("Corte").active(true).build()));
        assertThatThrownBy(() -> service.slots("figaro", CORTE, null, dia)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("pedido válido grava com origem SITE, telefone normalizado e avisa cliente e dono")
    void bookOk() {
        PublicBookingDtos.Confirmation c = service.book("figaro", pedido("(11) 98888-7777", null));

        ArgumentCaptor<AgendaBookingService.NovoAgendamento> cap = ArgumentCaptor.forClass(AgendaBookingService.NovoAgendamento.class);
        verify(bookingService).agendar(cap.capture());
        assertThat(cap.getValue().source()).isEqualTo(AppointmentSource.SITE);
        assertThat(cap.getValue().phone()).isEqualTo("5511988887777");
        assertThat(cap.getValue().exigirPreferido()).isTrue();
        assertThat(cap.getValue().durationMinutes()).isEqualTo(30);
        verify(customerService).findOrCreateCustomer("5511988887777", "João da Silva");
        verify(whatsAppService).sendTextMessage(eq("5511988887777"), contains("cancelar"));
        verify(whatsAppService).sendTextMessage(eq("5511911112222"), contains("Novo agendamento pelo site"));
        assertThat(c.professionalName()).isEqualTo("Ana");
        assertThat(c.time()).isEqualTo("10:00");
    }

    @Test
    @DisplayName("honeypot preenchido não grava nada")
    void honeypot() {
        assertThatThrownBy(() -> service.book("figaro", pedido("11988887777", "http://spam")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(bookingService, never()).agendar(any());
    }

    @Test
    @DisplayName("telefone inválido é recusado")
    void invalidPhone() {
        for (String tel : List.of("123", "abc", "11 8888-77", "+1 415 555 0100")) {
            assertThatThrownBy(() -> service.book("figaro", pedido(tel, null)))
                    .as(tel).isInstanceOf(IllegalArgumentException.class);
        }
        verify(bookingService, never()).agendar(any());
    }

    @Test
    @DisplayName("horário fora da grade (POST montado à mão) vira conflito")
    void timeNotInGrid() {
        PublicBookingDtos.Request r = new PublicBookingDtos.Request(CORTE, ANA, dia, "03:00", "João", "11988887777", null);
        assertThatThrownBy(() -> service.book("figaro", r)).isInstanceOf(BookingConflictException.class);
        verify(bookingService, never()).agendar(any());
    }

    @Test
    @DisplayName("horário ocupado entre a grade e o clique vira 409")
    void raceLost() {
        org.mockito.Mockito.doReturn(Optional.empty()).when(bookingService).agendar(any());
        assertThatThrownBy(() -> service.book("figaro", pedido("11988887777", null)))
                .isInstanceOf(BookingConflictException.class);
        verify(whatsAppService, never()).sendTextMessage(any(), any());
    }

    @Test
    @DisplayName("teto de horários futuros por telefone")
    void futureCap() {
        Appointment futuro = Appointment.builder().appointmentDate(dia.plusDays(1)).appointmentTime(LocalTime.NOON).build();
        when(bookingService.upcomingFor(SHOP, "5511988887777")).thenReturn(List.of(futuro, futuro, futuro));
        assertThatThrownBy(() -> service.book("figaro", pedido("11988887777", null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(bookingService, never()).agendar(any());
    }

    @Test
    @DisplayName("normalização de telefone e nome")
    void normalization() {
        assertThat(PublicBookingService.normalizarTelefone("+55 (21) 3333-4444")).contains("552133334444");
        assertThat(PublicBookingService.normalizarTelefone("011 98888-7777")).contains("5511988887777");
        assertThat(PublicBookingService.normalizarTelefone("11 88888-7777")).isEmpty(); // celular sem o 9
        assertThat(PublicBookingService.normalizarNome("  Zé   d'Ávila ")).isEqualTo("Zé d'Ávila");
        assertThatThrownBy(() -> PublicBookingService.normalizarNome("<script>")).isInstanceOf(IllegalArgumentException.class);
    }
}
