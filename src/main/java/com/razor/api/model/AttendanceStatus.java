package com.razor.api.model;

/**
 * O que aconteceu com um agendamento depois do horario (V39). Ausente (null) = ainda agendado
 * ou nao informado.
 */
public enum AttendanceStatus {
    /** O cliente veio e foi atendido. */
    ATTENDED,
    /** O cliente faltou sem avisar. Libera a cadeira para encaixe e nao recebe pedido de avaliacao. */
    NO_SHOW
}
