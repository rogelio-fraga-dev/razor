package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class BookingLockTest {

    @Test
    void slotConflict_reconhecidoPeloNomeDoIndice() {
        var e = new DataIntegrityViolationException("could not execute statement",
                new SQLException("ERROR: duplicate key value violates unique constraint \"uq_appointments_slot\""));
        assertThat(BookingLock.isSlotConflict(e)).isTrue();
    }

    @Test
    void outraViolacao_naoEConflitoDeHorario() {
        var e = new DataIntegrityViolationException("x", new SQLException("violates foreign key constraint \"fk_x\""));
        assertThat(BookingLock.isSlotConflict(e)).isFalse();
    }

    @Test
    void inline_soExecuta() {
        assertThat(BookingLock.inline().run(java.util.UUID.randomUUID(), () -> 42)).isEqualTo(42);
    }
}
