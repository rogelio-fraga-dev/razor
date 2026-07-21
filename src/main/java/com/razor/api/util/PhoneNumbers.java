package com.razor.api.util;

/**
 * Normalizacao de numeros de telefone brasileiros.
 *
 * <p>Extraido de {@code OrchestratorService} para permitir teste isolado. Mantem
 * exatamente o comportamento anterior: remove nao-digitos, tira o DDI 55 quando
 * presente e remove o nono digito (9) para comparacao consistente entre formatos.
 */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /**
     * Normaliza um numero brasileiro para o formato DDD + numero (sem o nono digito),
     * facilitando a comparacao entre variacoes (com/sem DDI, com/sem o 9).
     *
     * @param rawNumber numero cru (pode conter simbolos, espacos, DDI); {@code null} vira "".
     * @return apenas digitos, sem DDI 55 e sem o nono digito quando aplicavel.
     */
    public static String normalizeBrazilian(String rawNumber) {
        if (rawNumber == null) {
            return "";
        }
        String num = rawNumber.replaceAll("[^0-9]", "");
        if (num.startsWith("55") && num.length() >= 12) {
            num = num.substring(2);
        }
        if (num.length() == 11 && num.charAt(2) == '9') {
            num = num.substring(0, 2) + num.substring(3);
        }
        return num;
    }
}
