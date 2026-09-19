package com.backend.usuarios.auth.token;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "token_revocado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenRevocado {

    public enum Motivo {
        LOGOUT,
        ROTACION,
        REUSO_DETECTADO,
        CAMBIO_ROL,
        CUENTA_ELIMINADA
    }

    /** Identificador único del token (claim `jti`). */
    @Id
    @Column(length = 64)
    private String jti;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private Motivo motivo;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
    }
}
