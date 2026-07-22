package com.razor.api.dto;

import com.razor.api.model.Customer;
import java.time.LocalDateTime;
import java.util.UUID;

/** Cliente da barbearia para o painel web. */
public record CustomerResponse(
        UUID id,
        String name,
        String phone,
        int fidelityPoints,
        LocalDateTime lastServiceDate,
        LocalDateTime createdAt) {

    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(
                c.getId(),
                c.getName(),
                c.getPhoneNumber(),
                c.getFidelityPoints() != null ? c.getFidelityPoints() : 0,
                c.getLastServiceDate(),
                c.getCreatedAt());
    }
}
