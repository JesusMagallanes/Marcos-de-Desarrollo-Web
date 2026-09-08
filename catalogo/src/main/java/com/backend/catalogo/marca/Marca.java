package com.backend.catalogo.marca;

import java.util.LinkedHashSet;
import java.util.Set;

import com.backend.catalogo.categoria.Categoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "marca")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Marca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @Column(nullable = false, length = 1000)
    private String descripcion;

    /**
     * En qué categorías vende esta marca.
     *
     * <p>Era un {@code @ManyToOne} a UNA categoría, y eso es falso en cualquier
     * tienda: LG vende monitores, televisores y refrigeradoras. Con el modelo
     * viejo había que dar de alta «LG», «LG (TV)» y «LG (Línea blanca)» como
     * marcas distintas, y la restricción de nombre único lo impedía.
     *
     * <p>Formalmente era una violación de 4FN: {@code marca ↠ categoria} es una
     * dependencia multivaluada, y una dependencia multivaluada que no está
     * proyectada en su propia tabla es justo lo que esa forma normal prohíbe.
     * Ver {@code docs/modelo-datos.md} y la migración V18.
     *
     * <p>{@code LinkedHashSet} y no {@code HashSet}: el orden de inserción hace
     * que la categoría «principal» —la primera que se envía— sea estable, y de
     * ella sale el {@code categoriaId} que sigue devolviendo la API por
     * compatibilidad.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "marca_categoria",
            joinColumns = @JoinColumn(name = "marca_id"),
            inverseJoinColumns = @JoinColumn(name = "categoria_id"))
    @OrderBy("name ASC")
    @Builder.Default
    private Set<Categoria> categorias = new LinkedHashSet<>();
}
