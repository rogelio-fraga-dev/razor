package com.razor.api.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Dias de funcionamento (V32)")
class WorkingDaysTest {

    @Test
    @DisplayName("lê a lista ISO gravada na coluna")
    void parse_isoList() {
        assertThat(WorkingDays.parse("1,2,3,4,5,6")).isEqualTo(WorkingDays.DEFAULT);
        assertThat(WorkingDays.parse("2,7"))
                .containsExactlyInAnyOrder(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY);
    }

    @Test
    @DisplayName("valor ausente ou ilegível cai no padrão, nunca em 'fechado todo dia'")
    void parse_invalid_fallsBackToDefault() {
        // Uma barbearia que para de oferecer horário por causa de um campo mal preenchido é pior
        // do que uma que oferece de segunda a sábado.
        assertThat(WorkingDays.parse(null)).isEqualTo(WorkingDays.DEFAULT);
        assertThat(WorkingDays.parse("")).isEqualTo(WorkingDays.DEFAULT);
        assertThat(WorkingDays.parse("   ")).isEqualTo(WorkingDays.DEFAULT);
        assertThat(WorkingDays.parse("Seg a Sáb")).isEqualTo(WorkingDays.DEFAULT);
        assertThat(WorkingDays.parse("9,0,-3")).isEqualTo(WorkingDays.DEFAULT);
    }

    @Test
    @DisplayName("token inválido no meio não derruba os válidos")
    void parse_ignoresBadTokensOnly() {
        assertThat(WorkingDays.parse("1,x,3"))
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
    }

    @Test
    @DisplayName("grava sempre em ordem crescente — o valor no banco precisa ser estável")
    void format_isSorted() {
        assertThat(WorkingDays.format(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.MONDAY)))
                .isEqualTo("1,6");
        assertThat(WorkingDays.format(WorkingDays.DEFAULT)).isEqualTo("1,2,3,4,5,6");
    }

    @Test
    @DisplayName("ida e volta preserva os dias")
    void roundTrip() {
        var days = EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY);
        assertThat(WorkingDays.parse(WorkingDays.format(days))).isEqualTo(days);
    }

    @Test
    @DisplayName("a frase é derivada dos dias — sequência vira intervalo, buraco vira lista")
    void describe_rangeOrList() {
        assertThat(WorkingDays.describe(WorkingDays.DEFAULT)).isEqualTo("Seg a Sáb");
        assertThat(WorkingDays.describe(EnumSet.allOf(DayOfWeek.class))).isEqualTo("todos os dias");
        assertThat(WorkingDays.describe(EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)))
                .isEqualTo("Ter, Qui e Sáb");
        assertThat(WorkingDays.describe(EnumSet.of(DayOfWeek.MONDAY))).isEqualTo("Seg");
        assertThat(WorkingDays.describe(EnumSet.noneOf(DayOfWeek.class))).isEqualTo("fechado");
    }

    @Test
    @DisplayName("ISO é 1=segunda…7=domingo, e NÃO o ordinal do enum")
    void isoIsNotOrdinal() {
        // O Jackson desserializa número para enum por ordinal (0=MONDAY). Sem conversão
        // explícita, um payload com [1..6] (Seg a Sáb em ISO) viraria Ter a Dom em silêncio.
        assertThat(WorkingDays.toIso(WorkingDays.DEFAULT)).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(WorkingDays.fromIso(java.util.Set.of(1))).containsExactly(DayOfWeek.MONDAY);
        assertThat(WorkingDays.fromIso(java.util.Set.of(7))).containsExactly(DayOfWeek.SUNDAY);
        assertThat(WorkingDays.fromIso(WorkingDays.toIso(WorkingDays.DEFAULT)))
                .isEqualTo(WorkingDays.DEFAULT);
    }

    @Test
    @DisplayName("ISO fora da faixa é ignorado em vez de estourar")
    void fromIso_ignoresOutOfRange() {
        assertThat(WorkingDays.fromIso(java.util.Set.of(0, 8, 3)))
                .containsExactly(DayOfWeek.WEDNESDAY);
        assertThat(WorkingDays.fromIso(java.util.Set.of())).isEmpty();
        assertThat(WorkingDays.fromIso(null)).isEmpty();
    }

    @Test
    @DisplayName("domingo não é dia aberto no padrão — é o bug que a V32 fecha")
    void default_excludesSunday() {
        assertThat(WorkingDays.isOpen(WorkingDays.DEFAULT, DayOfWeek.SUNDAY)).isFalse();
        assertThat(WorkingDays.isOpen(WorkingDays.DEFAULT, DayOfWeek.MONDAY)).isTrue();
    }
}
