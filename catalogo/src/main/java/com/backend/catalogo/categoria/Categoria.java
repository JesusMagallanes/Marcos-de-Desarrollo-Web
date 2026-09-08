package com.backend.catalogo.categoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sin @OneToMany hacia Producto: la colección bidireccional del monolito (con cascade
 * ALL) arrastraba borrados en cascada que nadie pedía.
 */
@Entity
@Table(name = "categoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @Column(nullable = false, length = 120, unique = true)
    private String slug;

    @Column(nullable = false, length = 500)
    private String description;

    /**
     * Nombre de ícono de FontAwesome, sin el prefijo (p. ej. "laptop").
     */
    @Column(length = 60)
    private String icono;

    /**
     * Categoría que la contiene. {@code null} = raíz del árbol.
     *
     * <p>La taxonomía era plana: una lista de categorías hermanas. Un catálogo
     * real es un árbol —«Tecnología › Computación › Laptops»— y sin él no hay
     * migas de pan, ni menú desplegable, ni «ver todo lo de Computación»
     * incluyendo lo que cuelga por debajo.
     *
     * <p>Autorreferencia y no tabla aparte porque es la misma entidad en los dos
     * extremos. Ver la migración V18.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_padre_id")
    private Categoria padre;
}
