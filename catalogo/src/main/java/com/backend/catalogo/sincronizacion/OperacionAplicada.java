package com.backend.catalogo.sincronizacion;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Registro de una operación de escritura offline ya aplicada.
 *
 * <p>El identificador lo genera el cliente al ENCOLAR la operación y lo
 * conserva entre reintentos: es lo que permite al servidor reconocer un
 * reenvío y responder sin volver a aplicar el efecto. No guarda el contenido,
 * solo qué pasó: para reprocesar no hay nada que reconstruir, la respuesta de
 * un duplicado siempre dice "ya estaba".
 */
@Entity
@Table(name = "operaciones_aplicadas")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OperacionAplicada {

    /** UUID v4 generado por el cliente. Es la clave anti-duplicados. */
    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;
}
