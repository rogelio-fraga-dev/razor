-- V11: Configuracoes do bot por tenant (Fase 3). Textos e links que o cliente recebe pelo
-- WhatsApp (endereco, mapa, tabela de servicos, agendamento, Instagram) passam a ser
-- editaveis por barbearia no painel web, em vez de ficarem so no config global.
--
-- Tabela 1:1 com barbershops, criada sob demanda. Enquanto um tenant nao tiver linha aqui,
-- o BarbershopSettingsService recai nos textos do config global (razor.menu.*) — sem regressao.

CREATE TABLE barbershop_settings (
    barbershop_id     UUID PRIMARY KEY REFERENCES barbershops(id),
    address_text      TEXT,
    address_maps_url  TEXT,
    services_text     TEXT,
    schedule_url      TEXT,
    instagram_url     TEXT,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP
);
