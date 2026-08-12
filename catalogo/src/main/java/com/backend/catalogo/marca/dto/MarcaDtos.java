package com.backend.catalogo.marca.dto;

import com.backend.catalogo.marca.Marca;
import com.backend.catalogo.shared.validacion.Saneador;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class MarcaDtos {

    private MarcaDtos() {
    }

    public record MarcaResponse(
            Long id,
            String name,
            String descripcion,
            Long categoriaId) {

        public static MarcaResponse desde(Marca m) {
            return new MarcaResponse(m.getId(), m.getName(), m.getDescripcion(), m.getCategoria().getId());
        }
    }

    public record MarcaRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 1000) String descripcion,
            @NotNull @Positive Long categoriaId) {

        /** A03: se limpia antes de validar y antes del control de duplicados. */
        public MarcaRequest {
            name = Saneador.texto(name);
            descripcion = Saneador.textoMultilinea(descripcion);
        }
    }
}
