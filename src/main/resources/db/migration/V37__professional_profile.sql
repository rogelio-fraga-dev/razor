-- V37: Perfil completo do barbeiro.
--
-- Ate aqui o profissional era so nome, telefone, comissao e ativo. Ele e a cara da barbearia:
-- aparece na agenda, nas comissoes e, principalmente, na pagina da equipe do site white-label.
--
-- DECISOES:
--
-- 1. FOTO EM DATA URI, NAO EM STORAGE (opcao (a) do ROADMAP). Nao ha storage de objetos hoje.
--    O painel recorta a foto em quadrado e redimensiona para ~256px JPEG no navegador; o que
--    chega aqui e um `data:image/jpeg;base64,...` de ~20-40KB. A aplicacao recusa qualquer outro
--    esquema (http, javascript:, data:text/html...) e qualquer coisa acima de ~60KB — o campo
--    nunca vira porta de XSS nem de upload de arquivo arbitrario. Quando o upload de fotos do
--    site exigir storage (opcao (b)), as duas coisas migram juntas.
--
-- 2. ESPECIALIDADES em texto, uma por linha (como a galeria do site em V33). Sao etiquetas de
--    vitrine, sem relacao com nada — tabela propria seria cerimonia sem ganho.
--
-- 3. SERVICOS QUE ATENDE em tabela de ligacao. Vazio significa "faz todos": e o estado de
--    quem ja existia antes desta migracao, entao ninguem deixa de receber agendamento por causa
--    dela (sem regressao). ON DELETE CASCADE nos dois lados: apagar um servico ou um barbeiro
--    nao pode deixar ligacao orfa.

ALTER TABLE professionals
    ADD COLUMN photo_data    TEXT,
    ADD COLUMN nickname      VARCHAR(60),
    ADD COLUMN bio           VARCHAR(500),
    ADD COLUMN specialties   TEXT,
    ADD COLUMN instagram_url VARCHAR(255);

CREATE TABLE professional_services (
    professional_id UUID NOT NULL REFERENCES professionals(id) ON DELETE CASCADE,
    service_id      UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    PRIMARY KEY (professional_id, service_id)
);

CREATE INDEX idx_professional_services_service ON professional_services(service_id);
