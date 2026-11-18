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

    /**
     * Formato CANONICO de gravacao de um WhatsApp brasileiro: so digitos, com DDI 55
     * ({@code 55 + DDD + numero}, 12 ou 13 digitos) — o mesmo que chega do webhook da Evolution.
     *
     * <p>Nao confundir com {@link #normalizeBrazilian}: aquele tira o DDI e o nono digito e serve
     * so para COMPARAR; gravar nesse formato quebraria o envio (a Evolution precisa do DDI). O
     * nono digito e preservado como digitado: ha contas de WhatsApp registradas sem ele (12
     * digitos) e o envio para elas so funciona assim. Por isso as buscas por telefone usam
     * {@link #variants} — casam as duas formas.
     *
     * @return o numero canonico, ou vazio se nao parece um telefone brasileiro (DDD + 8/9 digitos)
     */
    public static java.util.Optional<String> canonicalBrazilian(String raw) {
        if (raw == null) return java.util.Optional.empty();
        String d = raw.replaceAll("[^0-9]", "");
        if (d.startsWith("0")) d = d.substring(1); // 0 de operadora/discagem
        if (d.length() == 10 || d.length() == 11) d = "55" + d;
        if (!d.startsWith("55") || (d.length() != 12 && d.length() != 13)) return java.util.Optional.empty();
        if (d.charAt(2) == '0') return java.util.Optional.empty(); // DDD nao comeca com 0
        if (d.length() == 13 && d.charAt(4) != '9') return java.util.Optional.empty(); // celular: 9xxxx-xxxx
        return java.util.Optional.of(d);
    }

    /**
     * As grafias com que o MESMO telefone pode estar gravado: com e sem DDI, com e sem o nono
     * digito, mais os digitos exatamente como vieram. Base das buscas por telefone (limite de
     * agendamentos, "seus horarios", pacotes): um cadastro de 12 digitos e um agendamento de 13
     * sao a mesma pessoa.
     */
    public static java.util.Set<String> variants(String raw) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (raw == null) return out;
        String digits = raw.replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) out.add(digits);
        String base = normalizeBrazilian(raw); // DDD + 8 digitos quando celular
        if (base.length() == 10) {
            String com9 = base.substring(0, 2) + "9" + base.substring(2);
            out.add("55" + base);
            out.add("55" + com9);
            out.add(base);
            out.add(com9);
        } else if (!base.isEmpty()) {
            out.add(base);
            out.add("55" + base);
        }
        return out;
    }
}
