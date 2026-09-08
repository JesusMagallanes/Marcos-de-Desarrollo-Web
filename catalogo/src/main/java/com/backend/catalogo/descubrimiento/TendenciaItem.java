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
 * Lo que se está moviendo en una zona, ya calculado.
 *
 * <p>Tendencia NO es «lo más visto de siempre». Un producto con mil visitas
 * históricas y veinte esta semana está apagándose; uno con cien históricas y
 * ochenta esta semana está despegando, y es el segundo el que interesa. Por eso
 * el score pondera la velocidad reciente por su crecimiento frente al periodo
 * anterior.
 *
 * <p>{@code sujetos} es el piso de privacidad: una fila sostenida por menos de
 * N sujetos distintos no se sirve, porque con cuatro personas en un distrito la
 * «tendencia» delata a quien la generó.
 */
@Entity
@Table(name = "tendencia_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TendenciaItem {

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {

        private static final long serialVersionUID = 1L;

        @Enumerated(EnumType.STRING)
        @Column(length = 12)
        private NivelGeografico nivel;

        /** Prefijo del ubigeo según el nivel; cadena vacía en nacional. */
        @Column(length = 6)
        private String zona;

        @Enumerated(EnumType.STRING)
        @Column(name = "item_tipo", length = 16)
        private TipoItem itemTipo;

        @Column(name = "item_id")
        private Long itemId;

        @Override
        public boolean equals(Object otro) {
            if (this == otro) {
                return true;
            }
            if (!(otro instanceof Id id)) {
                return false;
            }
            return nivel == id.nivel && Objects.equals(zona, id.zona)
                    && itemTipo == id.itemTipo && Objects.equals(itemId, id.itemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nivel, zona, itemTipo, itemId);
        }
    }

    @EmbeddedId
    private Id id;

    @Column(name = "categoria_id")
    private Long categoriaId;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal score;

    @Column(nullable = false)
    private Integer sujetos;

    @Column(name = "calculado_en", nullable = false)
    private Instant calculadoEn;
}
