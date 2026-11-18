package com.razor.api.dto;

import com.razor.api.model.Customer;
import com.razor.api.model.CustomerStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cliente da barbearia para o painel web. {@code planId}/{@code planName} nulos = cliente avulso.
 * {@code tags} e {@code status} (V36): grupos do dono e a situacao calculada (novo/ativo/em
 * risco/sumido); {@code status} nulo quando nao foi calculado para esta resposta.
 */
public record CustomerResponse(
        UUID id,
        String name,
        String phone,
        int fidelityPoints,
        LocalDateTime lastServiceDate,
        LocalDateTime createdAt,
        UUID planId,
        String planName,
        List<CustomerTagResponse> tags,
        CustomerStatus status) {

    public static CustomerResponse from(Customer c, String planName) {
        return from(c, planName, List.of(), null);
    }

    public static CustomerResponse from(Customer c, String planName, List<CustomerTagResponse> tags,
                                        CustomerStatus status) {
        return new CustomerResponse(
                c.getId(),
                c.getName(),
                c.getPhoneNumber(),
                c.getFidelityPoints() != null ? c.getFidelityPoints() : 0,
                c.getLastServiceDate(),
                c.getCreatedAt(),
                c.getSubscriptionPlanId(),
                planName,
                tags == null ? List.of() : tags,
                status);
    }
}
