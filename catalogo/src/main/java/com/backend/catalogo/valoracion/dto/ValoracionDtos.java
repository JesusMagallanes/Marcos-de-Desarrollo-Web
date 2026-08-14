package com.backend.catalogo.valoracion.dto;

import java.time.Instant;

import com.backend.catalogo.shared.validacion.Saneador;
import com.backend.catalogo.valoracion.EstadoValoracion;
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
            EstadoValoracion estado,
            Instant creadoEn) {

        public static ValoracionResponse desde(Valoracion v) {
            return new ValoracionResponse(
                    v.getId(), v.getNombre(), v.getCalificacion(), v.getComentario(),
                    v.getEstado(), v.getCreadoEn());
        }
    }

    /**
     * La reseña de la portada: además del comentario, el producto al que va,
     * para que quien la lea sepa de qué se habla. `productoImagenUrl` es la
     * imagen principal (la de las tarjetas), no la galería.
     */
    public record ValoracionDestacadaResponse(
            Long id,
            Long productoId,
            String productoNombre,
            String productoImagenUrl,
            String nombre,
            Integer calificacion,
            String comentario,
            Instant creadoEn) {

        public static ValoracionDestacadaResponse desde(Valoracion v) {
            return new ValoracionDestacadaResponse(
                    v.getId(),
                    v.getProducto().getId(),
                    v.getProducto().getName(),
                    v.getProducto().getImageUrl(),
                    v.getNombre(),
                    v.getCalificacion(),
                    v.getComentario(),
                    v.getCreadoEn());
        }
    }

    /** La vista del panel de moderación: incluye el producto para ubicar la reseña. */
    public record ValoracionAdminResponse(
            Long id,
            Long productoId,
            String productoNombre,
            String nombre,
            Integer calificacion,
            String comentario,
            EstadoValoracion estado,
            Instant creadoEn,
            Instant actualizadoEn) {

        public static ValoracionAdminResponse desde(Valoracion v) {
            return new ValoracionAdminResponse(
                    v.getId(),
                    v.getProducto().getId(),
                    v.getProducto().getName(),
                    v.getNombre(),
                    v.getCalificacion(),
                    v.getComentario(),
                    v.getEstado(),
                    v.getCreadoEn(),
                    v.getActualizadoEn());
        }
    }

    /** Lo que puede fijar el administrador al moderar. */
    public record CambioEstadoValoracion(@NotNull EstadoValoracion estado) {
    }

    /**
     * `nombre` es el nombre mostrado que envía el cliente; la identidad real
     * (usuarioId) se resuelve del JWT, así que el nombre es solo decorativo.
     */
    public record ValoracionRequest(
            @NotNull @Min(1) @Max(5) Integer calificacion,
            @NotBlank @Size(max = 1000, message = "El comentario no puede superar 1000 caracteres") String comentario,
            @NotBlank @Size(max = 120, message = "El nombre no puede superar 120 caracteres") String nombre) {

        /**
         * A03: es el único texto libre que un CLIENTE cualquiera puede dejar
         * visible al resto de la tienda, así que es donde más importa limpiar.
         * Se quitan controles e invisibles y se normaliza a NFC; el HTML se
         * conserva tal cual y se escapa al pintarlo (Angular interpola escapando).
         */
        public ValoracionRequest {
            comentario = Saneador.textoMultilinea(comentario);
            nombre = Saneador.texto(nombre);
        }
    }
}
