package com.backend.catalogo.categoria.dto;

import com.backend.catalogo.categoria.Categoria;

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
            String urlImage) {

        public static CategoriaResponse desde(Categoria c) {
            return new CategoriaResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription(), c.getUrlImage());
        }
    }

    public record CategoriaRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "^[a-z0-9-]+$", message = "El slug solo admite minúsculas, números y guiones") String slug,
            @NotBlank @Size(max = 500) String description,
            String urlImage) {
    }
}
