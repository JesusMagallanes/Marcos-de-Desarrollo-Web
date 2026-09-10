package com.backend.catalogo.descubrimiento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
 * Lo que el sistema DECIDIÓ enseñar, se llegara a ver o no.
 *
 * <p>Es distinta de {@link Impresion} y la diferencia es la que hace que las dos
 * sirvan. Una impresión la escribe el navegador cuando una tarjeta entra en
 * pantalla, y de ahí sale la fatiga; si aquí se mezclaran las dos cosas, la
 * fatiga empezaría a castigar productos hasta los que nadie llegó a desplazarse,
 * que es lo contrario de «ya te lo enseñé y lo ignoraste».
 *
 * <p>Esto es la decisión del backend, y con ella se puede medir el hueco entre
 * lo que el sistema propone y lo que la gente alcanza a mirar, que en un Home
 * largo es enorme.
 *
 * <p>Guarda la razón y el score, que la impresión no tiene y no puede deducir:
 * el módulo no es la razón, porque el carrusel colaborativo mezcla dos.
 */
@Entity
@Table(name = "recomendacion_servida")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecomendacionServida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sujeto_id", nullable = false)
    private UUID sujetoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_tipo", nullable = false, length = 16)
    private TipoItem itemTipo;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false, length = 40)
    private String modulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RazonRecomendacion razon;

    /** Empieza en 0. La posición sesga el clic más que la calidad. */
    @Column(nullable = false)
    private short posicion;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal score;

    @Column(name = "ranker_version", nullable = false, length = 24)
    private String rankerVersion;

    /** Si había perfil del que tirar. El arranque en frío se mide aparte. */
    @Column(name = "con_perfil", nullable = false)
    private boolean conPerfil;

    @Column(name = "servido_en", nullable = false)
    private Instant servidoEn;
}
