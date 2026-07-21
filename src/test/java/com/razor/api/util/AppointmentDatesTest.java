package com.razor.api.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AppointmentDatesTest {

    @Test
    @DisplayName("'hoje' resolve para a data de hoje")
    void parseDate_hoje_returnsToday() {
        assertThat(AppointmentDates.parseDate("hoje")).isEqualTo(AppointmentDates.today());
    }

    @Test
    @DisplayName("'amanha'/'amanhã' resolve para amanha (tolerante a acento)")
    void parseDate_amanha_returnsTomorrow() {
        LocalDate tomorrow = AppointmentDates.today().plusDays(1);
        assertThat(AppointmentDates.parseDate("amanha")).isEqualTo(tomorrow);
        assertThat(AppointmentDates.parseDate("amanhã")).isEqualTo(tomorrow);
        assertThat(AppointmentDates.parseDate(" HOJE ")).isEqualTo(AppointmentDates.today());
    }

    @Test
    @DisplayName("DD/MM ja passado neste ano infere o proximo ano (bug de virada de ano)")
    void parseDate_pastDayThisYear_rollsToNextYear() {
        LocalDate today = AppointmentDates.today();
        // Um dia que certamente ja passou: ontem
        LocalDate yesterday = today.minusDays(1);
        String ddmm = String.format("%02d/%02d", yesterday.getDayOfMonth(), yesterday.getMonthValue());

        LocalDate parsed = AppointmentDates.parseDate(ddmm);

        assertThat(parsed).isAfter(today);
        assertThat(parsed.getDayOfMonth()).isEqualTo(yesterday.getDayOfMonth());
        assertThat(parsed.getMonthValue()).isEqualTo(yesterday.getMonthValue());
    }

    @Test
    @DisplayName("DD/MM futuro neste ano mantem o ano corrente")
    void parseDate_futureDayThisYear_keepsCurrentYear() {
        LocalDate today = AppointmentDates.today();
        LocalDate tomorrow = today.plusDays(1);
        String ddmm = String.format("%02d/%02d", tomorrow.getDayOfMonth(), tomorrow.getMonthValue());

        LocalDate parsed = AppointmentDates.parseDate(ddmm);

        // Amanha nunca vira ano seguinte (exceto virada real de ano, coberto acima)
        assertThat(parsed).isEqualTo(tomorrow);
    }

    @Test
    @DisplayName("DD/MM/AAAA usa o ano explicito")
    void parseDate_explicitYear_isRespected() {
        assertThat(AppointmentDates.parseDate("15/03/2030")).isEqualTo(LocalDate.of(2030, 3, 15));
        assertThat(AppointmentDates.parseDate("15/03/30")).isEqualTo(LocalDate.of(2030, 3, 15));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "sexta", "dia 30", "31/13", "quando der"})
    @DisplayName("data invalida lanca IllegalArgumentException (nunca guarda lixo)")
    void parseDate_invalid_throws(String bad) {
        assertThatThrownBy(() -> AppointmentDates.parseDate(bad))
                .isInstanceOf(RuntimeException.class);
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
        "10:00, 10:00",
        "10h,   10:00",
        "10h30, 10:30",
        "9:05,  09:05",
        "14,    14:00",
        "'  8h ', 08:00"
    })
    @DisplayName("varios formatos de hora resolvem para LocalTime")
    void parseTime_variousFormats(String input, String expected) {
        assertThat(AppointmentDates.parseTime(input)).isEqualTo(LocalTime.parse(expected));
    }

    @Test
    @DisplayName("formatacao de exibicao volta para dd/MM e HH:mm")
    void format_roundTripsDisplay() {
        assertThat(AppointmentDates.formatDate(LocalDate.of(2026, 5, 28))).isEqualTo("28/05");
        assertThat(AppointmentDates.formatTime(LocalTime.of(9, 5))).isEqualTo("09:05");
        assertThat(AppointmentDates.formatDate(null)).isEmpty();
    }
}
