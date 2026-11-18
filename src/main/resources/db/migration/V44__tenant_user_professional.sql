-- V44: Liga o usuario do painel ao cadastro de profissional ("Meu dia" do barbeiro).
--
-- Ate aqui o login do barbeiro (papel BARBER) nao sabia QUEM ele era na agenda: via a grade de
-- todos e nao tinha como ver a propria comissao. Com o vinculo, o painel mostra "meus horarios"
-- e "quanto eu ganhei", e a API escopa esses dados pelo profissional do usuario logado.
--
-- Mesmo tenant garantido no banco: a FK e composta (professional_id, barbershop_id) contra
-- professionals(id, barbershop_id), entao um usuario nunca aponta para o barbeiro de outra
-- barbearia, nem por bug no servico. professional_id nulo = sem vinculo (MATCH SIMPLE nao
-- confere a FK). Apagar o profissional so desfaz o vinculo: SET NULL apenas na coluna do
-- vinculo (sintaxe do Postgres 15), sem tocar no barbershop_id do usuario.

ALTER TABLE professionals
    ADD CONSTRAINT uq_professionals_id_shop UNIQUE (id, barbershop_id);

ALTER TABLE tenant_users ADD COLUMN professional_id UUID;

ALTER TABLE tenant_users
    ADD CONSTRAINT fk_tenant_users_professional
    FOREIGN KEY (professional_id, barbershop_id)
    REFERENCES professionals (id, barbershop_id)
    ON DELETE SET NULL (professional_id);

-- Um cadastro de profissional pertence a no maximo um login: dois usuarios "sendo" o mesmo
-- barbeiro veriam a mesma comissao como se fosse de cada um.
CREATE UNIQUE INDEX uq_tenant_users_professional
    ON tenant_users (professional_id) WHERE professional_id IS NOT NULL;
