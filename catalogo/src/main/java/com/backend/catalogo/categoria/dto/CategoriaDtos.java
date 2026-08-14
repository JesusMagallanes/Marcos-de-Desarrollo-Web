package com.backend.catalogo.categoria.dto;

import com.backend.catalogo.categoria.Categoria;
import com.backend.catalogo.shared.validacion.Saneador;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CategoriaDtos {

    private CategoriaDtos() {
    }

    public record CategoriaResponse(
            Long id,
            String name,
            String slug,
            String description,
            String icono) {

        public static CategoriaResponse desde(Categoria c) {
            return new CategoriaResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription(), c.getIcono());
        }
    }

    /**
     * Los topes coinciden con los de las columnas (ver V1__esquema_inicial.sql y
     * V14__categoria_icono_fontawesome.sql): sin ellos una cadena más larga no la
     * rechaza la validación sino Postgres, y el usuario recibe un 500 en vez de un
     * 400 explicando el campo.
     *
     * `icono` es el nombre de un ícono de FontAwesome sin el prefijo (p. ej.
     * "laptop"), igual que en las guías.
     */
    public record CategoriaRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 120)
            @Pattern(regexp = "^[a-z0-9-]+$", message = "El slug solo admite minúsculas, números y guiones") String slug,
            @NotBlank @Size(max = 500) String description,
            @Size(max = 60)
            @Pattern(regexp = "^[a-z0-9-]*$", message = "El icono solo admite minúsculas, números y guiones") String icono) {

        /**
         * A03: el saneado ocurre AQUÍ, en el constructor compacto, y no en el
         * servicio. Jackson construye el record, este constructor limpia y solo
         * después corre la validación, así que `@NotBlank` ve el texto ya
         * recortado y `existsByName(dto.name())` compara la forma normalizada.
         * Si se saneara en el servicio, "Laptops " y "Laptops" seguirían
         * esquivando el control de duplicados.
         */
        public CategoriaRequest {
            name = Saneador.texto(name);
            slug = Saneador.texto(slug);
            description = Saneador.textoMultilinea(description);
            icono = Saneador.textoONulo(icono);
        }
    }
}
