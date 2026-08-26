package com.backend.catalogo.sincronizacion.dto;

import com.backend.catalogo.valoracion.dto.ValoracionDtos.ValoracionRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class SincronizacionDtos {

    private SincronizacionDtos() {
    }

    /** Lo que el cliente puede hacer con una operación encolada offline. */
    public enum TipoOperacionValoracion {
        GUARDAR, ELIMINAR
    }

    /**
     * Una operación de escritura que el cliente aplicó primero en local y
     * ahora confirma contra el servidor.
     *
     * <p>{@code operacionId} es un UUID v4 que el cliente genera AL ENCOLAR y
     * conserva entre reintentos: si la red se cortó después de que el servidor
     * aplicara el efecto pero antes de recibir la respuesta, el reenvío llega
     * con el mismo id y se reconoce como ya aplicado. Sin él, ese escenario
     * exacto duplicaría reseñas.
     *
     * <p>{@code valoracion} solo va con GUARDAR (para ELIMINAR no hace falta
     * nada del contenido); la obligatoriedad se comprueba en el servicio,
     * porque una validación declarativa no sabe condicionar por el enum.
     */
    public record PeticionSyncValoracion(
            @NotBlank
            @Size(max = 64)
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "El identificador de operación debe ser un UUID")
            String operacionId,

            @NotNull TipoOperacionValoracion tipo,

            @NotNull @Positive Long productoId,

            @Valid ValoracionRequest valoracion) {
    }

    /**
     * Resultado de aplicar (o reconocer) la operación.
     *
     * <p>{@code duplicado} true significa "esto ya lo apliqué con anterioridad;
     * no volví a tocar nada". El cliente entonces saca la operación de su cola.
     * {@code valoracionId} viene con GUARDAR: es la reseña resultante, útil
     * para que el cliente sustituya su copia optimista por la real.
     */
    public record RespuestaSyncValoracion(boolean duplicado, Long valoracionId) {

        public static RespuestaSyncValoracion nueva(Long valoracionId) {
            return new RespuestaSyncValoracion(false, valoracionId);
        }

        public static RespuestaSyncValoracion yaAplicada() {
            return new RespuestaSyncValoracion(true, null);
        }
    }
}
