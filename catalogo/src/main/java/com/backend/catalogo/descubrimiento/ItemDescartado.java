package com.backend.catalogo.descubrimiento;

import java.io.Serializable;
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
 * Lo que alguien dijo explícitamente que no quiere ver.
 *
 * <p>Tabla propia y no una consulta sobre el log de eventos porque esto se lee
 * en CADA recomendación para filtrar duro. Escanear los eventos para decidir
 * qué no mostrar sería el cuello de botella del Home.
 */
@Entity
@Table(name = "item_descartado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemDescartado {

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
            return Objects.equals(sujetoId, id.sujetoId)
                    && itemTipo == id.itemTipo
                    && Objects.equals(itemId, id.itemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sujetoId, itemTipo, itemId);
        }
    }

    @EmbeddedId
    private Id id;

    /** Qué evento lo descartó: DISMISS o NOT_INTERESTED. */
    @Column(nullable = false, length = 24)
    private String motivo;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;
}
