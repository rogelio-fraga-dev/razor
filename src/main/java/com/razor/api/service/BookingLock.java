package com.razor.api.service;

import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fila unica de gravacao da agenda de UMA barbearia: bot, site e painel passam por aqui para
 * "checar o horario e gravar".
 *
 * <p><b>Por que no banco.</b> A versao anterior era um {@code synchronized} em memoria que (1) so
 * valia numa instancia, (2) so cobria bot/site — o painel criava e remarcava por fora — e (3)
 * soltava a trava ANTES do commit: a outra requisicao entrava, lia a agenda sem o agendamento
 * ainda nao confirmado e gravava o mesmo horario. Aqui a trava e um
 * {@code pg_advisory_xact_lock} tirado DENTRO da transacao que grava: o Postgres so a solta no
 * commit/rollback, entao quem espera sempre le a agenda ja com o agendamento do outro. Vale
 * entre instancias, sem tabela nova.
 *
 * <p>Chamado dentro de uma transacao ja aberta (ex.: o painel e {@code @Transactional}), a trava
 * entra nela e vai ate o commit dela — que e exatamente o que se quer.
 *
 * <p><b>Rede de seguranca.</b> O indice unico {@code uq_appointments_slot} (V43) impede dois
 * agendamentos do mesmo barbeiro no mesmo inicio mesmo se algum caminho escapar desta fila; a
 * violacao vira {@link BookingConflictException} (409 "horario ocupado").
 */
@Slf4j
@Component
public class BookingLock {

    /** Nome do indice unico de horario (V43). */
    public static final String SLOT_INDEX = "uq_appointments_slot";
    static final String HORARIO_OCUPADO = "Esse horário acabou de ser ocupado. Escolha outro.";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    @Autowired
    public BookingLock(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /** Sem banco: so executa. Para testes de unidade dos servicos que gravam agenda. */
    public static BookingLock inline() {
        return new BookingLock(null, null);
    }

    /**
     * Roda {@code work} numa transacao (a atual, se houver) com a agenda da barbearia travada ate
     * o commit. Violacao do indice de horario vira {@link BookingConflictException}.
     */
    public <T> T run(UUID barbershopId, Supplier<T> work) {
        if (tx == null) return work.get();
        try {
            return tx.execute(status -> {
                jdbc.query("select pg_advisory_xact_lock(hashtext(?))", rs -> null, "agenda:" + barbershopId);
                return work.get();
            });
        } catch (DataIntegrityViolationException e) {
            if (isSlotConflict(e)) {
                log.info("[AGENDA] Horario ja ocupado (indice {}) no tenant {}", SLOT_INDEX, barbershopId);
                throw new BookingConflictException(HORARIO_OCUPADO);
            }
            throw e;
        }
    }

    /** A violacao e do indice unico de horario (e nao de outra restricao)? */
    public static boolean isSlotConflict(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(SLOT_INDEX)) return true;
            if (t.getCause() == t) break;
        }
        Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
        return root.getMessage() != null && root.getMessage().contains(SLOT_INDEX);
    }
}
