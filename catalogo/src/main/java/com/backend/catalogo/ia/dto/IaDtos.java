package com.backend.catalogo.ia.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;

import com.backend.catalogo.shared.validacion.Limites;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Entradas y salidas de las funciones de IA aplicada del catálogo. */
public final class IaDtos {

    private IaDtos() {
    }

    /** Si hay modelo configurado; el frontend enseña o esconde los botones de IA. */
    public record EstadoIa(boolean disponible) {
    }

    public record BusquedaRequest(
            @NotBlank @Size(max = Limites.MAX_BUSQUEDA * 3,
                    message = "La búsqueda es demasiado larga") String consulta) {
    }

    /**
     * Una búsqueda en lenguaje natural convertida en filtros de verdad.
     *
     * <p>Todo lo que hay aquí se comprobó contra el catálogo: la categoría
     * existe, cada marca es un id real, cada atributo es un código y un valor
     * que algún producto tiene. El {@code resumen} lo compone el servidor a
     * partir de eso, no el modelo, para que diga exactamente lo que se aplicó.
     *
     * @param orden en la forma del frontend: {@code precio-asc}, {@code relevancia}…
     */
    public record BusquedaInterpretada(
            String texto,
            String categoriaSlug,
            String categoriaNombre,
            List<Long> marcaIds,
            List<String> marcaNombres,
            BigDecimal precioMin,
            BigDecimal precioMax,
            boolean soloDisponibles,
            String orden,
            List<String> atributos,
            String resumen) {
    }

    /** Lo que el panel sabe de un producto antes de tener ficha. */
    public record RedaccionRequest(
            @NotBlank @Size(max = 150) String nombre,
            @NotNull @Positive Long categoriaId,
            @Positive Long marcaId,
            @Size(max = 1000, message = "Las notas no pueden pasar de 1000 caracteres") String notas) {
    }

    /**
     * Borrador de ficha. Es una PROPUESTA: el formulario la rellena y la persona
     * la revisa antes de guardar; nada se publica desde aquí.
     *
     * @param especificaciones en el formato del formulario: {@code · **Nombre**: valor}
     */
    public record RedaccionSugerida(String descripcion, String especificaciones) {
    }

    /** Nivel de riesgo que ve quien modera. */
    public enum Riesgo {
        BAJO, MEDIO, ALTO
    }

    /**
     * Análisis previo para quien modera. Nunca aprueba ni rechaza nada: ordena
     * la atención de una persona, que es quien decide.
     *
     * @param conIa false si solo corrieron las comprobaciones deterministas (sin
     *     modelo configurado o sin respuesta); el panel lo dice para no hacer pasar
     *     un análisis parcial por completo
     */
    public record AnalisisModeracion(Riesgo riesgo, List<String> motivos, String sugerencia,
            boolean conIa) {
    }

    /**
     * Resumen de las reseñas aprobadas de un producto.
     *
     * @param basadoEn cuántas reseñas se resumieron, para enseñarlo junto al texto
     */
    public record ResumenResenas(String resumen, List<String> aFavor, List<String> enContra,
            int basadoEn) {
    }

    /* ══════════════ Preguntar a la ficha ══════════════ */

    public record PreguntaRequest(
            @NotBlank @Size(max = 300, message = "La pregunta no puede pasar de 300 caracteres") String pregunta) {
    }

    /**
     * @param consta false si la ficha y las reseñas no dicen nada: entonces la
     *     respuesta lo reconoce en vez de suponer
     */
    public record RespuestaFicha(String respuesta, boolean consta) {
    }

    /* ══════════════ Comparador ══════════════ */

    public record ComparacionRequest(
            @NotNull @Size(min = 2, max = 3, message = "Se comparan de 2 a 3 productos")
            List<@NotNull @Positive Long> productoIds,
            @Size(max = 200, message = "El uso no puede pasar de 200 caracteres") String uso) {
    }

    /** Una fila de la tabla: una característica y su valor en cada producto, en orden. */
    public record FilaComparacion(String caracteristica, List<String> valores) {
    }

    /**
     * La tabla sale de la base; la IA solo añade la explicación y, si el uso lo
     * permite, cuál encaja mejor. Sin IA llegan la tabla y {@code explicacion} nula.
     */
    public record Comparacion(List<ProductoResponse> productos, List<FilaComparacion> filas,
            String explicacion, Long recomendadoId) {
    }

    /* ══════════════ Carrito ══════════════ */

    public record ComplementosRequest(
            @NotEmpty @Size(max = 20) List<@NotNull @Positive Long> productoIds) {
    }

    /** Accesorios que completan lo que hay en el carrito, con el porqué de cada uno. */
    public record Complementos(List<ProductoResponse> productos, Map<Long, String> motivos) {
    }

    /* ══════════════ Reseñas ══════════════ */

    public record AyudaResenaRequest(
            @NotNull @Positive Long productoId,
            @NotNull @Min(1) @Max(5) Integer calificacion,
            @Size(max = 1500) String borrador) {
    }

    /** Preguntas que ayudan a escribir; nunca el texto de la reseña. */
    public record AyudaResena(List<String> preguntas) {
    }

    /* ══════════════ Colaborador ══════════════ */

    public record RevisionFotoRequest(
            @NotBlank @Size(max = 200) String url,
            @NotBlank @Size(max = 150) String nombre) {
    }

    /**
     * @param coincide si la foto parece el producto; null si el modelo no lo tiene claro
     * @param textoAlternativo descripción breve para lectores de pantalla
     */
    public record RevisionFoto(Boolean coincide, List<String> avisos, String textoAlternativo) {
    }

    public record ComoCorregir(List<String> pasos) {
    }

    /* ══════════════ Búsqueda por foto ══════════════ */

    /** Lo que se vio en la foto y, si se pudo, los filtros que le corresponden. */
    public record BusquedaPorFoto(String consulta, BusquedaInterpretada busqueda) {
    }
}
