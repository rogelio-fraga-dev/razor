package com.razor.api.dto;

import com.razor.api.model.PaymentStatus;
import java.time.LocalTime;

/** Item da agenda do dia para o painel web. {@code subscriber} marca cliente do clube (assinante). */
public record AppointmentView(
        LocalTime time,
        String customerName,
        String service,
        PaymentStatus paymentStatus,
        String professionalName,
        boolean subscriber) {
}
