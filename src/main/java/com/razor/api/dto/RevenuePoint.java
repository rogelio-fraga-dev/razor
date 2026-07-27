package com.razor.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um ponto do grafico de evolucao de faturamento: uma faixa de datas e o quanto foi faturado nela.
 *
 * <p>A serie sai pronta do backend numa chamada so. Antes o painel montava o grafico pedindo um
 * resumo financeiro por ponto (7 requisicoes), o que estourava o limite de conexoes do navegador
 * e fazia cada ponto varrer os agendamentos do tenant de novo.
 */
public record RevenuePoint(String label, LocalDate from, LocalDate to, BigDecimal revenue) {}
