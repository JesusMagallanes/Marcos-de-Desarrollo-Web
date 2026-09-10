package com.backend.catalogo.descubrimiento;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Las cuentas de un día, ya hechas.
 *
 * <p>Nada aquí describe a una persona: son totales por módulo, razón, versión de
 * ranking y banda de posición. {@code sujetos} está para lo contrario de
 * identificar — es el piso que permite descartar una fila sostenida por una sola
 * persona, que no describe un patrón sino a esa persona.
 *
 * <p>Sobrevive a la purga del detalle a propósito: es lo que permite comparar el
 * recomendador de hoy con el de hace un año sin conservar cada fila que lo
 * produjo.
 */
@Entity
@Table(name = "metrica_descubrimiento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricaDescubrimiento {

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {

        private static final long serialVersionUID = 1L;

        private LocalDate dia;

        @Column(length = 40)
        private String modulo;

        @Enumerated(EnumType.STRING)
        @Column(length = 24)
        private RazonRecomendacion razon;

        @Column(name = "ranker_version", length = 24)
        private String rankerVersion;

        /** Posición agrupada de tres en tres; ver la migración V23. */
        @Column(name = "banda_posicion")
        private short bandaPosicion;

        @Column(name = "con_perfil")
        private boolean conPerfil;

        @Override
        public boolean equals(Object otro) {
            if (this == otro) {
                return true;
            }
            if (!(otro instanceof Id id)) {
                return false;
            }
            return Objects.equals(dia, id.dia)
                    && Objects.equals(modulo, id.modulo)
                    && razon == id.razon
                    && Objects.equals(rankerVersion, id.rankerVersion)
                    && bandaPosicion == id.bandaPosicion
                    && conPerfil == id.conPerfil;
        }

        @Override
        public int hashCode() {
            return Objects.hash(dia, modulo, razon, rankerVersion, bandaPosicion, conPerfil);
        }
    }

    @EmbeddedId
    private Id id;

    /** Lo que el backend decidió enseñar. */
    @Column(nullable = false)
    private long servidas;

    /** Lo que llegó a entrar en pantalla. La diferencia con lo servido es real. */
    @Column(nullable = false)
    private long vistas;

    @Column(nullable = false)
    private long clics;

    @Column(name = "vistas_profundas", nullable = false)
    private long vistasProfundas;

    @Column(nullable = false)
    private long carritos;

    @Column(nullable = false)
    private long compras;

    @Column(nullable = false)
    private long sujetos;

    @Column(name = "calculado_en", nullable = false)
    private Instant calculadoEn;

    /**
     * Clics sobre lo que de verdad se vio.
     *
     * <p>Sobre {@code vistas} y no sobre {@code servidas} a propósito: dividir
     * por lo servido mezcla dos preguntas —si la recomendación era buena y si el
     * usuario llegó a desplazarse hasta ella— y hunde por igual a un módulo
     * acertado que quede al final de la página.
     *
     * @return 0 si no hubo ninguna vista; un CTR sin denominador no es 0, es
     *     «no se sabe», y quien lea esto tiene que mirar {@code vistas} antes de
     *     sacar conclusiones
     */
    public double ctr() {
        return vistas == 0 ? 0.0 : (double) clics / vistas;
    }

    /** Compras sobre lo visto. Un clic no es un éxito; esto se acerca más. */
    public double tasaCompra() {
        return vistas == 0 ? 0.0 : (double) compras / vistas;
    }
}
