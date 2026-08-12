package com.backend.catalogo.valoracion;

import java.time.Instant;

import com.backend.catalogo.producto.Producto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Valoración de un cliente sobre un producto (una por usuario y producto). */
@Entity
@Table(name = "valoracion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Valoracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    /** Identidad del cliente tomada del JWT; nunca se acepta por URL ni cuerpo. */
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** Nombre mostrado, enviado por el cliente al valorar (solo decorativo). */
    @Column(nullable = false, length = 120)
    private String nombre;

    /** De 1 a 5 estrellas; el rango también lo exige el CHECK de la tabla. */
    @Column(nullable = false)
    private Integer calificacion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String comentario;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;
}
