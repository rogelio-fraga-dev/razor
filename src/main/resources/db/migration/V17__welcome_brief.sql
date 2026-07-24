-- V17: marca quando o resumo de boas-vindas foi enviado ao dono pelo WhatsApp.
-- Na 1a vez que o dono conclui a configuracao do bot, o sistema manda um breve resumo
-- (o que o bot faz / o que ele comanda no painel). Esta coluna garante que isso aconteca
-- UMA vez so — NULL = ainda nao enviado; timestamp = ja enviado.

ALTER TABLE barbershop_settings ADD COLUMN welcome_brief_sent_at TIMESTAMP;
