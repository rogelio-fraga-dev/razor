package com.razor.api.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PhoneNumbersTest {

    @Test
    @DisplayName("null vira string vazia")
    void normalize_null_returnsEmpty() {
        assertThat(PhoneNumbers.normalizeBrazilian(null)).isEmpty();
    }

    @Test
    @DisplayName("string vazia continua vazia")
    void normalize_empty_returnsEmpty() {
        assertThat(PhoneNumbers.normalizeBrazilian("")).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @DisplayName("normaliza para DDD + numero sem o nono digito e sem DDI")
    @CsvSource({
        // com DDI 55, com nono digito
        "5511987654321, 1187654321",
        // sem DDI, com nono digito
        "11987654321,   1187654321",
        // ja normalizado (10 digitos)
        "1187654321,    1187654321",
        // com DDI 55, sem nono digito (12 digitos)
        "551187654321,  1187654321",
        // com formatacao/simbolos
        "(11) 98765-4321, 1187654321"
    })
    void normalize_variousFormats_normalizesConsistently(String input, String expected) {
        assertThat(PhoneNumbers.normalizeBrazilian(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("remove caracteres nao numericos")
    void normalize_stripsNonDigits() {
        assertThat(PhoneNumbers.normalizeBrazilian("+55 (11) 98765-4321"))
                .isEqualTo("1187654321");
    }

    @Test
    @DisplayName("variacoes do mesmo numero convergem para o mesmo valor")
    void normalize_sameNumberDifferentFormats_areEqual() {
        String comDdiComNove = PhoneNumbers.normalizeBrazilian("5511987654321");
        String semDdiComNove = PhoneNumbers.normalizeBrazilian("11987654321");
        String semNove = PhoneNumbers.normalizeBrazilian("1187654321");
        assertThat(comDdiComNove).isEqualTo(semDdiComNove).isEqualTo(semNove);
    }
}
