package com.razor.api.model;

/**
 * Papel de um usuario do painel web dentro de uma barbearia (tenant).
 * O papel de dono da plataforma (super-admin, Fase 4) sera adicionado depois.
 */
public enum UserRole {
    OWNER,
    RECEPTION,
    BARBER
}
