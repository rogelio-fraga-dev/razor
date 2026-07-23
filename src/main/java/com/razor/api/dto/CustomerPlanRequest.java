package com.razor.api.dto;

import java.util.UUID;

/**
 * Payload para vincular (ou desvincular) um cliente a um plano de assinatura.
 * {@code planId} nulo remove o vinculo (cliente volta a ser avulso).
 */
public record CustomerPlanRequest(UUID planId) {
}
