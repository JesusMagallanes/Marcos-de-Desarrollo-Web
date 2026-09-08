package com.backend.catalogo.descubrimiento;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quien explora, tenga cuenta o no.
 *
 * <p>El sistema NO se construye alrededor de {@code usuarioId}. La mayor parte
 * del tráfico de descubrimiento viene de gente sin sesión iniciada, y colgar el
 * perfil de la cuenta perdería justo la señal que se quiere capturar: la del
 * que entra «solo a mirar».
 *
 * <p>{@code usuarioId} es un valor plano, sin clave foránea: el usuario vive en
 * el esquema de otro servicio. Misma regla que {@code valoracion.usuarioId}.
 */
@Entity
@Table(name = "sujeto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sujeto {

    /** Lo genera el servidor y viaja al cliente; nunca se acepta uno inventado. */
    @Id
    private UUID id;

    /** {@code null} mientras sea anónimo. */
    @Column(name = "usuario_id")
    private Long usuarioId;

    /** Último distrito conocido (ubigeo INEI de seis dígitos). */
    @Column(length = 6)
    private String ubigeo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "visto_en", nullable = false)
    private Instant vistoEn;

    /**
     * Si se fusionó en otro sujeto, a cuál.
     *
     * <p>La fila no se borra al fusionar: hay pestañas abiertas mandando
     * todavía el id viejo, y borrarla tiraría esos eventos. Queda como
     * redirección y {@code SujetoService} resuelve el superviviente.
     */
    @Column(name = "fusionado_en")
    private UUID fusionadoEn;

    public boolean estaFusionado() {
        return fusionadoEn != null;
    }
}
