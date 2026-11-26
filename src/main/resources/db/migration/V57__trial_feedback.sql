-- Feedback obrigatório no teste grátis (piloto, 26/11).
-- O dono responde no 3º dia, no 7º e no fim do teste; pode adiar cada etapa uma vez, por 24 h.

create table trial_feedback (
    id              uuid primary key,
    barbershop_id   uuid not null references barbershops(id) on delete cascade,
    user_id         uuid,
    stage           varchar(10) not null,            -- D3 | D7 | FIM
    score           smallint not null check (score between 0 and 10),
    overall         text,                            -- o que está achando
    liked           text,                            -- o que mais ajudou
    missing         text,                            -- o que falta ou atrapalhou
    would_pay       varchar(10),                     -- SIM | TALVEZ | NAO (só no FIM)
    price_suggestion varchar(120),                   -- quanto pagaria por mês (só no FIM)
    created_at      timestamp not null default now(),
    constraint uq_trial_feedback_stage unique (barbershop_id, stage)
);
create index idx_trial_feedback_created on trial_feedback (created_at desc);

create table trial_feedback_snooze (
    barbershop_id   uuid not null references barbershops(id) on delete cascade,
    stage           varchar(10) not null,
    snoozed_until   timestamp not null,
    primary key (barbershop_id, stage)
);

-- Console da plataforma: anotação interna por barbearia.
alter table barbershops add column if not exists platform_note text;
