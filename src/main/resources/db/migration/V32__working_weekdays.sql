-- V32: Dias de funcionamento como DADO, nao como frase.
--
-- `working_days` (V15) sempre foi texto livre — "Seg a Sáb", "SEG,TER,QUA,QUI,SEX,SAB",
-- "segunda a sabado", o que o dono digitasse. Ninguem nunca interpretou esse campo: ele so era
-- concatenado numa frase do prompt da IA.
--
-- A consequencia era um bug de agendamento em producao: o AgendaService monta os horarios livres
-- a partir de opening_time/closing_time e dos bloqueios (V16), e nada mais. Sem um bloqueio
-- recorrente de domingo cadastrado a mao, o bot OFERECE E MARCA horario no domingo — mesmo com
-- working_days dizendo que a barbearia abre de segunda a sabado. O bot vinha prometendo uma coisa
-- ao cliente e agendando outra.
--
-- Tambem e o que travava a taxa de ocupacao: "horas disponiveis" precisa saber quais dias contam.
-- Contar domingo como disponivel derrubaria a metrica em ~1/7 em silencio.
--
-- FORMATO: lista de dias ISO separada por virgula, 1=segunda ... 7=domingo — o mesmo numero de
-- java.time.DayOfWeek.getValue(), para nao existir conversao no meio do caminho (a V16 usa
-- 0=domingo por causa do padrao do JS, e misturar as duas convencoes seria pedir erro).
ALTER TABLE barbershop_settings
    ADD COLUMN working_weekdays VARCHAR(20);

-- BACKFILL: todo mundo nasce de segunda a sabado.
--
-- Nao e chute: "Seg a Sáb" e o proprio default do sistema (BarbershopSettingsService), ou seja, e
-- o que o bot ja vinha dizendo ao cliente. A migracao faz a AGENDA passar a obedecer o que o bot
-- ja prometia — nao inventa um horario novo.
--
-- Parsear o texto livre foi descartado: "Seg a Sáb" e um INTERVALO, e procurar tokens nele
-- devolveria {segunda, sabado} — dois dias em vez de seis, que e pior que o default. Quem abre
-- em outro ritmo (fecha segunda, abre domingo) ajusta em Configuracoes; o campo aparece la com
-- os sete dias marcaveis.
UPDATE barbershop_settings SET working_weekdays = '1,2,3,4,5,6' WHERE working_weekdays IS NULL;

COMMENT ON COLUMN barbershop_settings.working_weekdays IS
    'Dias abertos, ISO 1=seg..7=dom, separados por virgula. Fonte da verdade do expediente.';

-- `working_days` continua na tabela por enquanto, mas o codigo parou de le-lo: a frase mostrada
-- a IA passou a ser derivada daqui. Nao e dropado na mesma migracao de proposito — apagar dado
-- e irreversivel, e nao custa nada esperar uma versao para confirmar que ninguem depende dele.
COMMENT ON COLUMN barbershop_settings.working_days IS
    'DEPRECADO desde a V32 — substituido por working_weekdays. Nao ler; remover em migracao futura.';
