package com.backend.catalogo.guia;

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

/**
 * Un paso de una guía, con su posición dentro de la lista.
 *
 * Los pasos son filas y no un bloque de texto con formato: así el panel los
 * edita de uno en uno y la tienda los pinta numerados sin interpretar ningún
 * lenguaje de marcado, que sería una puerta abierta a inyectar HTML.
 */
@Entity
@Table(name = "guia_paso")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuiaPaso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guia_id", nullable = false)
    private Guia guia;

    @Column(nullable = false)
    private Integer posicion;

    @Column(nullable = false, length = 160)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;
}
