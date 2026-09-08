package com.backend.catalogo.atributo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

import com.backend.catalogo.producto.Producto;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * El valor de una característica para un producto.
 *
 * <p>Es la fila que sustituye a una línea del antiguo {@code specifications}.
 * La clave {@code (producto_id, atributo_id)} no es un detalle técnico: es la
 * garantía de que una ficha no pueda mostrar dos «RAM» distintas.
 */
@Entity
@Table(name = "producto_atributo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoAtributo {

    /** Clave compuesta: un producto tiene un valor de cada característica. */
    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "producto_id")
        private Long productoId;

        @Column(name = "atributo_id")
        private Long atributoId;

        @Override
        public boolean equals(Object otro) {
            if (this == otro) {
                return true;
            }
            if (!(otro instanceof Id id)) {
                return false;
            }
            return Objects.equals(productoId, id.productoId)
                    && Objects.equals(atributoId, id.atributoId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(productoId, atributoId);
        }
    }

    @EmbeddedId
    @Builder.Default
    private Id id = new Id();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("productoId")
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("atributoId")
    @JoinColumn(name = "atributo_id")
    private Atributo atributo;

    @Column(nullable = false, length = 300)
    private String valor;

    /**
     * Copia numérica del valor cuando el atributo es {@code NUMERO}.
     *
     * <p>Permite ordenar y filtrar por rango —«monitores de 27 pulgadas o
     * más»— sin castear texto en cada consulta, que además fallaría en cuanto
     * un valor no fuera un número.
     */
    @Column(name = "valor_numero", precision = 14, scale = 3)
    private BigDecimal valorNumero;

    /** Orden en la ficha. Sin esto salen por id, que no significa nada. */
    @Column(nullable = false)
    @Builder.Default
    private Integer posicion = 0;
}
