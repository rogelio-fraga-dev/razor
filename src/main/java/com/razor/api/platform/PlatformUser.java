package com.razor.api.platform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Operador da plataforma (V51) — fora dos tenants, 2FA obrigatorio. */
@Entity
@Table(name = "platform_users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformUser {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String name;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(name = "totp_secret_enc")
    private String totpSecretEnc;
    /** Chave do 2FA em configuracao (cifrada), estavel ate confirmar (V56). */
    @Column(name = "totp_pending_enc")
    private String totpPendingEnc;
    /** Fim da carencia sem 2FA (V56). */
    @Column(name = "mfa_deadline_at")
    private LocalDateTime mfaDeadlineAt;
    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;
    @Column(name = "last_totp_step")
    private Long lastTotpStep;
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
