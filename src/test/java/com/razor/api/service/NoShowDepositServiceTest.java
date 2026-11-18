package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Appointment;
import com.razor.api.model.AttendanceStatus;
import com.razor.api.model.DepositStatus;
import com.razor.api.repository.AppointmentRepository;
import com.razor.api.util.AppointmentDates;
import com.razor.api.util.WorkingDays;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Sinal para quem falta muito (V42): limiar, PIX obrigatorio, valor. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NoShowDepositServiceTest {

    private static final String PHONE = "5511977776666";

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private BarbershopSettingsService barbershopSettingsService;
    @Mock private PricingService pricingService;
    @InjectMocks private NoShowDepositService service;

    private Appointment ag;

    @BeforeEach
    void setUp() {
        ag = Appointment.builder().id(1L).customerPhone(PHONE).service("Corte")
                .appointmentDate(LocalDate.now().plusDays(2)).appointmentTime(LocalTime.of(10, 0)).build();
        when(pricingService.priceFor("Corte")).thenReturn(new BigDecimal("40.00"));
        givenPix("chave@pix.com");
        givenThreshold(2, null);
    }

    private void givenPix(String key) {
        when(barbershopSettingsService.resolve()).thenReturn(new BarbershopSettingsService.BotSettings(
                "", "", "", null, "", "Barbearia", key, null, null, "09:00", "19:00", WorkingDays.DEFAULT,
                null, null, null, null, null, null));
    }

    private void givenThreshold(Integer after, BigDecimal amount) {
        when(barbershopSettingsService.getNoShowSettings())
                .thenReturn(new BarbershopSettingsService.NoShowSettings(true, 18, 3, false, after, amount));
    }

    private void givenNoShows(long n) {
        when(appointmentRepository.countByBarbershopIdAndCustomerPhoneAndAttendanceStatusAndAppointmentDateGreaterThanEqual(
                any(), eq(PHONE), eq(AttendanceStatus.NO_SHOW), eq(AppointmentDates.today().minusDays(180))))
                .thenReturn(n);
    }

    @Test
    @DisplayName("abaixo do limiar: nada muda")
    void belowThreshold() {
        givenNoShows(1);
        assertThat(service.requireDepositIfNeeded(ag, "Zé")).isEmpty();
        assertThat(ag.getDepositStatus()).isNull();
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("no limiar (2 faltas em 180 dias): sinal PENDING e PIX do tenant com o preco do servico")
    void atThreshold() {
        givenNoShows(2);
        var msg = service.requireDepositIfNeeded(ag, "Zé");
        assertThat(msg).get().asString().contains("chave@pix.com").contains("R$ 40,00").contains("sinal");
        assertThat(ag.getDepositStatus()).isEqualTo(DepositStatus.PENDING);
        verify(appointmentRepository).save(ag);
    }

    @Test
    @DisplayName("valor fixo configurado vence o preco do servico")
    void fixedAmount() {
        givenThreshold(2, new BigDecimal("15.00"));
        givenNoShows(5);
        assertThat(service.requireDepositIfNeeded(ag, "Zé")).get().asString().contains("R$ 15,00");
    }

    @Test
    @DisplayName("regra desligada (null) nunca exige sinal")
    void disabled() {
        givenThreshold(null, null);
        givenNoShows(10);
        assertThat(service.requireDepositIfNeeded(ag, "Zé")).isEmpty();
    }

    @Test
    @DisplayName("sem PIX configurado a regra nao vale (nunca inventa chave)")
    void noPix() {
        givenPix(null);
        givenNoShows(10);
        assertThat(service.requireDepositIfNeeded(ag, "Zé")).isEmpty();
        assertThat(ag.getDepositStatus()).isNull();
    }
}
