package com.backend.catalogo.guia;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Guía de ayuda de la sección "Aprende con nosotros".
 *
 * El contenido lo escribe el administrador desde el panel; antes eran dos
 * enlaces vacíos escritos a mano en el pie.
 */
@Entity
@Table(name = "guia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Guia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Va en la URL: /guias/{slug}. */
    @Column(nullable = false, length = 140)
    private String slug;

    @Column(nullable = false, length = 160)
    private String titulo;

    @Column(nullable = false, length = 300)
    private String resumen;

    /** Nombre de icono de FontAwesome, sin prefijo (p. ej. "cart-shopping"). */
    @Column(length = 60)
    private String icono;

    @Column(nullable = false)
    private Integer posicion;

    /** Sin publicar, solo la ve el administrador. */
    @Column(nullable = false)
    private Boolean publicada;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /**
     * Igual que en Producto: `@Builder.Default` es obligatorio, porque el
     * builder de Lombok ignora el inicializador del campo y dejaría la lista a
     * null en cuanto se cree una guía nueva.
     */
    @OneToMany(mappedBy = "guia", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("posicion ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<GuiaPaso> pasos = new ArrayList<>();
}
