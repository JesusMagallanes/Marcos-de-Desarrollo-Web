package com.backend.catalogo.categoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
     * Admite un `data:image/` (base64) subido desde el panel, por eso la columna
     * creció hasta 200.000 (ver V11__categoria_url_imagen_amplia.sql).
     */
    @Column(name = "url_image", length = 200_000)
    private String urlImage;
}
