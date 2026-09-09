package com.backend.catalogo.descubrimiento;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
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
 * «Quien se interesó por esto, se interesó también por aquello».
 *
 * <p>Es la relación que ninguna ficha de producto contiene. Que un monitor y un
 * brazo articulado van juntos no se deduce de sus categorías —son distintas— ni
 * de sus atributos —no comparten ninguno—: se deduce de que la misma gente mira
 * los dos. La fase 1, que solo sabe mirar el historial de cada persona por
 * separado, no puede llegar aquí ni con más datos.
 *
 * <p>Tabla derivada: se reconstruye entera desde {@code evento_interaccion} en
 * cada pasada del proceso por lotes. Borrarla no pierde información, solo
 * obliga a recalcular.
 */
@Entity
@Table(name = "item_relacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemRelacion {

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {

        private static final long serialVersionUID = 1L;

        @Enumerated(EnumType.STRING)
        @Column(name = "item_tipo", length = 16)
        private TipoItem itemTipo;

        @Column(name = "item_a")
        private Long itemA;

        @Column(name = "item_b")
        private Long itemB;

        @Override
        public boolean equals(Object otro) {
            if (this == otro) {
                return true;
            }
            if (!(otro instanceof Id id)) {
                return false;
            }
            return itemTipo == id.itemTipo
                    && Objects.equals(itemA, id.itemA)
                    && Objects.equals(itemB, id.itemB);
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemTipo, itemA, itemB);
        }
    }

    @EmbeddedId
    private Id id;

    /** Coseno sobre los conjuntos de sujetos. Entre 0 y 1. */
    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal score;

    /** Sujetos distintos que sostienen la relación. Nunca clics. */
    @Column(nullable = false)
    private int soporte;

    @Column(name = "ventana_dias", nullable = false)
    private short ventanaDias;

    @Column(name = "calculado_en", nullable = false)
    private Instant calculadoEn;
}
