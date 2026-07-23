package com.razor.api.dto;

import com.razor.api.model.Customer;
import java.time.LocalDateTime;
import java.util.UUID;

/** Cliente da barbearia para o painel web. {@code planId}/{@code planName} nulos = cliente avulso. */
public record CustomerResponse(
        UUID id,
        String name,
        String phone,
        int fidelityPoints,
        LocalDateTime lastServiceDate,
        LocalDateTime createdAt,
        UUID planId,
        String planName) {

    public static CustomerResponse from(Customer c, String planName) {
        return new CustomerResponse(
                c.getId(),
                c.getName(),
                c.getPhoneNumber(),
                c.getFidelityPoints() != null ? c.getFidelityPoints() : 0,
                c.getLastServiceDate(),
                c.getCreatedAt(),
                c.getSubscriptionPlanId(),
                planName);
    }
}
