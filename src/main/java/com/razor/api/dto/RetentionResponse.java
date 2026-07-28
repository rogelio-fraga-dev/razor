package com.razor.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Retenção da base: quem volta, quem está prestes a sumir e quem já sumiu.
 *
 * <p><b>O status é relativo ao ritmo de cada cliente, não a um prazo fixo.</b> Um cliente que
 * corta a cada 15 dias e sumiu há 40 é um problema; um que corta a cada 60 e sumiu há 40 está
 * dentro do normal. Um limite único trataria os dois igual — e o dono cobraria quem não devia,
 * perdendo quem devia.
 *
 * <p>Quem ainda não tem duas visitas não tem ritmo próprio para comparar: cai no
 * {@code defaultIntervalDays} (a mediana da barbearia, ou 30 dias quando nem isso existe).
 *
 * <p>{@code atRisk} e {@code lost} vêm com a lista de clientes, não só a contagem. Um número que
 * diz "12 clientes sumiram" sem dizer quais não é acionável — o dono precisa do telefone para
 * disparar.
 */
public record RetentionResponse(
        LocalDate referenceDate,
        int totalCustomers,
        Counts counts,
        /** Mediana de dias entre visitas dos clientes com 2+ atendimentos. Nulo se ninguém voltou. */
        Integer medianIntervalDays,
        /** Intervalo assumido para quem ainda não tem ritmo próprio. */
        int defaultIntervalDays,
        /** % da base que voltou ao menos uma vez. Nulo com base vazia. */
        java.math.BigDecimal returnRate,
        List<CustomerAtRisk> atRisk,
        List<CustomerAtRisk> lost) {

    /**
     * Quantos clientes em cada estágio.
     *
     * <p>{@code novo} = cadastrado há pouco e ainda sem histórico para julgar — não é elogio nem
     * alerta, é "cedo demais para dizer". Separá-lo evita inflar tanto "ativo" quanto "sumido".
     */
    public record Counts(int novo, int ativo, int emRisco, int sumido) {
    }

    /**
     * Cliente que merece um contato.
     *
     * <p>{@code expectedIntervalDays} é o ritmo dele (ou o padrão, quando não há histórico), e
     * {@code daysSinceLastVisit} é o atraso real. Os dois juntos é que justificam a cobrança —
     * mostrar só "há 40 dias" não diz se é muito.
     */
    public record CustomerAtRisk(
            UUID customerId,
            String name,
            String phone,
            LocalDate lastVisit,
            int daysSinceLastVisit,
            int expectedIntervalDays,
            int visits,
            boolean subscriber) {
    }
}
