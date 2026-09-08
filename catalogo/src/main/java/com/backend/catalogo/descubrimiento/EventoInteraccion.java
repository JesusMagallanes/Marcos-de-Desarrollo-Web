package com.backend.catalogo.descubrimiento;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una cosa que pasó. Solo se anexa: nunca se edita ni se borra fila a fila.
 *
 * <p>Es el dato crudo del que sale todo lo demás. El perfil se actualiza al
 * escribir el evento, así que esta tabla NO se consulta para recomendar; se
 * guarda para poder recalibrar, medir y entrenar más adelante.
 */
@Entity
@Table(name = "evento_interaccion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoInteraccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sujeto_id", nullable = false)
    private UUID sujetoId;

    @Column(name = "sesion_id")
    private UUID sesionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TipoEvento tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_tipo", length = 16)
    private TipoItem itemTipo;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "categoria_id")
    private Long categoriaId;

    @Column(length = 6)
    private String ubigeo;

    /** Permanencia en bruto. El techo se aplica al puntuar, no al guardar. */
    @Column(name = "dwell_ms")
    private Integer dwellMs;

    /**
     * De qué módulo salió lo que se tocó.
     *
     * <p>Parece metadato y es lo que permite medir qué carrusel funciona y,
     * más adelante, corregir el sesgo de posición al entrenar. Sin esto no hay
     * evaluación posible.
     */
    @Column(length = 32)
    private String origen;

    @Column
    private Short posicion;

    /** Lo específico del evento: el término buscado, los filtros aplicados. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant ocurridoEn;
}
