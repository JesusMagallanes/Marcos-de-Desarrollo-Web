package com.backend.usuarios.auth.recuperacion;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un código para restablecer la contraseña. Ver V14__recuperacion_clave.sql.
 *
 * <p>Guarda el hash, nunca el código: el código solo existe en el correo.
 */
@Entity
@Table(name = "codigo_recuperacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodigoRecuperacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "codigo_hash", nullable = false, length = 100)
    private String codigoHash;

    @Column(nullable = false)
    @Builder.Default
    private int intentos = 0;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "usado_en")
    private Instant usadoEn;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
    }

    /** Sirve todavía: sin usar, sin caducar y sin agotar los intentos. */
    public boolean vigente(Instant ahora, int maximoIntentos) {
        return usadoEn == null && ahora.isBefore(expiraEn) && intentos < maximoIntentos;
    }
}
