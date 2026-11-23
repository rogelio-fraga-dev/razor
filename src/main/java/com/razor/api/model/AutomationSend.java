package com.razor.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Um envio de automacao (V47). A chave unica (barbearia, tipo, telefone, ocasiao) faz o mesmo
 * envio nunca sair duas vezes, nem depois de um restart; os SENT recentes de marketing sao a base
 * do limite de frequencia por cliente.
 */
@Entity
@Table(name = "automation_sends")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomationSend {

    public enum Kind {
        /** "Sumiu ha N dias". */
        LAPSED,
        /** Aniversario com cupom. */
        BIRTHDAY,
        /** Lembrete de retorno do corte. */
        RETURN,
        /** Relatorio semanal para o dono (nao e marketing: fora do limite de frequencia). */
        WEEKLY_REPORT;

        public boolean isMarketing() {
            return this != WEEKLY_REPORT;
        }
    }

    public enum Status { PENDING, SENT, FAILED }

    /** Tentativas antes de desistir de um envio que falhou. */
    public static final int MAX_ATTEMPTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "barbershop_id", nullable = false)
    private UUID barbershopId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private Kind kind;

    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @Column(name = "reference_key", nullable = false, length = 60)
    private String referenceKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Builder.Default
    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    /** Dado da ocasiao (ex.: o codigo do cupom de aniversario, reaproveitado na nova tentativa). */
    @Column(name = "detail", length = 255)
    private String detail;

    @Column(name = "last_error", length = 255)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now(com.razor.api.util.AppointmentDates.ZONE);
    }

    /** Ja resolvido: enviado, em envio, ou falhou vezes demais. */
    public boolean isSettled() {
        return status == Status.SENT || status == Status.PENDING
                || (status == Status.FAILED && attempts >= MAX_ATTEMPTS);
    }
}
