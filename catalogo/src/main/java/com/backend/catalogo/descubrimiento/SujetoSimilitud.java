package com.backend.catalogo.descubrimiento;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuánto se parecen dos perfiles. Nunca dos personas.
 *
 * <p>La distinción no es retórica. Un sujeto es un identificador opaco que puede
 * no tener cuenta detrás, y esta tabla no sale nunca del backend: alimenta el
 * generador de candidatos y ahí se acaba. No hay endpoint que la exponga, ni lo
 * habrá, porque «gente parecida a ti» es un dato sobre terceros que ellos no han
 * ofrecido. Lo que llega al navegador es el resultado agregado —unos productos—
 * y jamás de quién salieron.
 *
 * <p>Las claves ajenas van con {@code ON DELETE CASCADE} en los dos lados: al
 * borrar un sujeto desaparecen sus aristas sin que nadie tenga que acordarse.
 */
@Entity
@Table(name = "sujeto_similitud")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SujetoSimilitud {

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "sujeto_a")
        private UUID sujetoA;

        @Column(name = "sujeto_b")
        private UUID sujetoB;

        @Override
        public boolean equals(Object otro) {
            if (this == otro) {
                return true;
            }
            if (!(otro instanceof Id id)) {
                return false;
            }
            return Objects.equals(sujetoA, id.sujetoA) && Objects.equals(sujetoB, id.sujetoB);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sujetoA, sujetoB);
        }
    }

    @EmbeddedId
    private Id id;

    /** Coseno entre los dos vectores de facetas. Entre 0 y 1. */
    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal score;

    /** Facetas que comparten. Una sola coincidencia no es un parecido. */
    @Column(nullable = false)
    private int soporte;

    @Column(name = "ventana_dias", nullable = false)
    private short ventanaDias;

    @Column(name = "calculado_en", nullable = false)
    private Instant calculadoEn;
}
