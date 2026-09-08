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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuánto le interesa a un sujeto una cosa concreta.
 *
 * <p>UNA tabla para categorías, marcas, atributos y precio. Se propuso
 * separarlas en tres, pero todas tienen la misma forma —sujeto, algo, un
 * score— y separarlas obligaría a consultar tres sitios y a repetir la lógica
 * de olvido en cada uno.
 *
 * <p>Las facetas de tipo {@link TipoFaceta#ATRIBUTO} salen de
 * {@code producto_atributo}: aquí no se guarda ninguna característica del
 * catálogo, solo el interés que despierta.
 */
@Entity
@Table(name = "perfil_faceta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerfilFaceta {

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "sujeto_id")
        private UUID sujetoId;

        @Enumerated(EnumType.STRING)
        @Column(name = "tipo_faceta", length = 16)
        private TipoFaceta tipoFaceta;

        @Column(name = "faceta", length = 120)
        private String faceta;

        @Override
        public boolean equals(Object otro) {
            if (this == otro) {
                return true;
            }
            if (!(otro instanceof Id id)) {
                return false;
            }
            return Objects.equals(sujetoId, id.sujetoId)
                    && tipoFaceta == id.tipoFaceta
                    && Objects.equals(faceta, id.faceta);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sujetoId, tipoFaceta, faceta);
        }
    }

    @EmbeddedId
    private Id id;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal score;

    /** Cuántos eventos lo sostienen. Alimenta la confianza. */
    @Column(nullable = false)
    private Integer eventos;

    /**
     * No es auditoría: es el parámetro del olvido.
     *
     * <p>El decaimiento se aplica al ESCRIBIR usando el tiempo transcurrido
     * desde aquí, así que el perfil se mantiene al día con un UPDATE por evento
     * y sin recorrer nunca el historial.
     */
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;
}
