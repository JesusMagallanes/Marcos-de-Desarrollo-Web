package com.backend.catalogo.valoracion.dto;

import java.time.Instant;

import com.backend.catalogo.valoracion.Valoracion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ValoracionDtos {

    private ValoracionDtos() {
    }

    /** Lo que se muestra en la tienda. El `usuario_id` jamás viaja hacia afuera. */
    public record ValoracionResponse(
            Long id,
            String nombre,
            Integer calificacion,
            String comentario,
            Instant creadoEn) {

        public static ValoracionResponse desde(Valoracion v) {
            return new ValoracionResponse(
                    v.getId(), v.getNombre(), v.getCalificacion(), v.getComentario(), v.getCreadoEn());
        }
    }

    /**
     * `nombre` es el nombre mostrado que envía el cliente; la identidad real
     * (usuarioId) se resuelve del JWT, así que el nombre es solo decorativo.
     */
    public record ValoracionRequest(
            @NotNull @Min(1) @Max(5) Integer calificacion,
            @NotBlank @Size(max = 1000, message = "El comentario no puede superar 1000 caracteres") String comentario,
            @NotBlank @Size(max = 120, message = "El nombre no puede superar 120 caracteres") String nombre) {
    }
}
