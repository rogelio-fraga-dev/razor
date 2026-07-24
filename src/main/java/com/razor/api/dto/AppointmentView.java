package com.razor.api.dto;

import com.razor.api.model.PaymentStatus;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Item da agenda do dia para o painel web. {@code subscriber} marca cliente do clube;
 * {@code durationMinutes} vem do catalogo e permite desenhar o bloco na agenda (calendario).
 */
public record AppointmentView(
        LocalTime time,
        String customerName,
        String customerPhone,
        String service,
        Integer durationMinutes,
        PaymentStatus paymentStatus,
        UUID professionalId,
        String professionalName,
        boolean subscriber) {
}
