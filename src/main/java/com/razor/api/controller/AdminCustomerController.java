package com.razor.api.controller;

import com.razor.api.dto.CustomerResponse;
import com.razor.api.service.CustomerService;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Base de clientes da barbearia no painel web (tenant vem do JWT). */
@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final CustomerService customerService;

    @GetMapping
    public List<CustomerResponse> list() {
        return customerService.listCustomers().stream()
                .map(CustomerResponse::from)
                .sorted(Comparator.comparing(CustomerResponse::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }
}
