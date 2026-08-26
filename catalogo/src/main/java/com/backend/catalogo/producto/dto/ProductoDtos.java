package com.backend.catalogo.producto.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.backend.catalogo.producto.EstadoModeracion;
import com.backend.catalogo.categoria.dto.CategoriaDtos.CategoriaResponse;
import com.backend.catalogo.producto.Producto;
import com.backend.catalogo.producto.ProductoImagen;
import com.backend.catalogo.shared.validacion.Limites;
import com.backend.catalogo.shared.validacion.Saneador;
import com.backend.catalogo.shared.validacion.UrlSegura;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class ProductoDtos {

    private ProductoDtos() {
    }

    /**
     * Categoría y marca viajan como id + nombre planos, nunca como objeto
     * anidado: así `compras` puede referenciar un producto sin compartir
     * entidades JPA con este servicio.
     *
     * `precioActual` es lo que paga el cliente hoy (el de oferta si está
     * vigente, si no el de lista) y `enOferta` indica si el descuento está
     * activo, para que la tienda no tenga que replicar la lógica de fechas.
     */
    public record ProductoResponse(
            Long id,
            String name,
            String description,
            String specifications,
            BigDecimal precio,
            BigDecimal precioOferta,
            String descuentoTipo,
            BigDecimal descuentoValor,
            Instant ofertaInicio,
            Instant ofertaFin,
            BigDecimal precioActual,
            boolean enOferta,
            Double calificacionPromedio,
            Long cantidadValoraciones,
            String imageUrl,
            List<String> imagenes,
            Integer stock,
            Long categoriaId,
            String categoriaName,
            Long marcaId,
            String marcaName,
            // ── Dueño y moderación (SZ-B08) ──
            // Van en la respuesta de siempre y no en un DTO aparte porque el
            // colaborador consulta sus productos por el mismo camino que la
            // tienda los suyos. Para el visitante son siempre `null` y APROBADO:
            // solo se le devuelve lo publicado.
            Long propietarioId,
            EstadoModeracion estadoModeracion,
            String motivoRechazo) {

        public static ProductoResponse desde(Producto p) {
            return desde(p, Instant.now(), null, null);
        }

        public static ProductoResponse desde(Producto p, Instant ahora) {
            return desde(p, ahora, null, null);
        }

        public static ProductoResponse desde(Producto p, Instant ahora,
                Double calificacionPromedio, Long cantidadValoraciones) {
            boolean enOferta = estaEnOferta(p, ahora);
            return new ProductoResponse(
                    p.getId(),
                    p.getName(),
                    p.getDescription(),
                    p.getSpecifications(),
                    p.getPrecio(),
                    p.getPrecioOferta(),
                    p.getDescuentoTipo(),
                    p.getDescuentoValor(),
                    p.getOfertaInicio(),
                    p.getOfertaFin(),
                    precioEfectivo(p, ahora),
                    enOferta,
                    calificacionPromedio,
                    cantidadValoraciones,
                    p.getImageUrl(),
                    p.getImagenes().stream().map(ProductoImagen::getUrl).toList(),
                    p.getStock(),
                    p.getCategoria().getId(),
                    p.getCategoria().getName(),
                    p.getMarca() != null ? p.getMarca().getId() : null,
                    p.getMarca() != null ? p.getMarca().getName() : null,
                    p.getPropietarioId(),
                    p.getEstadoModeracion(),
                    p.getMotivoRechazo());
        }
    }

    public record ProductoRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 500) String description,
            @Size(max = 2000) String specifications,
            @NotNull @DecimalMin(value = "0.01", message = "El precio debe ser mayor que 0")
            @Digits(integer = 10, fraction = 2, message = "El precio admite como mucho 2 decimales")
            BigDecimal precio,
            @Size(max = 20, message = "Como máximo 20 imágenes")
            List<@Size(max = 1000) @UrlSegura String> imagenes,
            @NotNull @Min(value = 0, message = "El stock no puede ser negativo")
            @Max(value = 1_000_000, message = "El stock es demasiado alto") Integer stock,
            @NotNull @Positive Long categoriaId,
            @Positive Long marcaId) {

        /**
         * A03: saneado en el constructor compacto, antes de que corra la
         * validación y antes de que el servicio construya la entidad. Las URLs se
         * limpian y se descartan las vacías aquí mismo, así que `aplicarImagenes`
         * recibe la lista ya normalizada.
         */
        public ProductoRequest {
            name = Saneador.texto(name);
            description = Saneador.textoMultilinea(description);
            specifications = Saneador.textoMultilineaONulo(specifications);
            imagenes = imagenes == null ? List.of()
                    : imagenes.stream()
                            .map(Saneador::texto)
                            .filter(u -> u != null && !u.isEmpty())
                            .toList();
        }
    }

    /** Forma que espera el `Pagina<T>` del frontend Angular. */
    public record PaginaResponse<T>(
            List<T> content,
            int number,
            int size,
            long totalElements,
            int totalPages) {
    }

    /**
     * Todo lo que la portada necesita, en una respuesta y acotado.
     *
     * <p>La portada pedía el catálogo COMPLETO y luego, en el navegador, se
     * quedaba con diez destacados, filtraba los que estaban en oferta y agrupaba
     * el resto por categoría de doce en doce. Es decir: se descargaba cada
     * producto de la tienda para enseñar unas decenas, y crecía con el catálogo
     * aunque la pantalla no cambiara.
     *
     * <p>Es un DTO pegado a una pantalla, y eso es deliberado: las tres listas
     * salen de tres consultas acotadas y de un solo viaje. La alternativa
     * —tres endpoints genéricos más uno por categoría— son diez peticiones para
     * pintar lo mismo.
     */
    public record PortadaResponse(
            List<ProductoResponse> destacados,
            List<ProductoResponse> ofertas,
            List<BloqueCategoria> porCategoria) {
    }

    /**
     * El panel de descuentos: una página de productos y los conteos de las
     * pestañas.
     *
     * <p>Los conteos van con la página y no en otra llamada porque se pintan a
     * la vez: separarlos sería un segundo viaje para dibujar la misma pantalla,
     * y un momento en que las pestañas dicen un número y la lista enseña otro.
     *
     * <p>Cuentan sobre TODO el catálogo, no sobre lo filtrado: es lo que hacía
     * la versión que calculaba esto en el navegador, y es lo que se quiere de
     * una pestaña —cuánto hay de cada cosa— frente a un número que baila al
     * escribir en el buscador.
     */
    public record PaginaDescuentos(
            List<ProductoResponse> content,
            int number,
            int size,
            long totalElements,
            int totalPages,
            ConteosDescuento conteos) {
    }

    public record ConteosDescuento(long todos, long activo, long programado, long inactivo) {
    }

    /** Una fila de la portada: la categoría y sus primeros productos. */
    public record BloqueCategoria(
            CategoriaResponse categoria,
            List<ProductoResponse> productos) {
    }

    /**
     * Aplicar un descuento a un lote de productos. `tipo` es `PORCENTAJE`
     * (valor en %) o `MONTO` (valor en soles).
     */
    public record AplicarDescuentoRequest(
            @NotEmpty @Size(max = Limites.MAX_LOTE, message = "Demasiados productos en un solo lote")
            List<@NotNull @Positive Long> productoIds,
            // Lista cerrada: `tipo` decide una rama de cálculo, así que no puede
            // ser texto libre que llegue hasta el servicio a ver qué pasa.
            @NotBlank @Pattern(regexp = "PORCENTAJE|MONTO",
                    message = "El tipo debe ser PORCENTAJE o MONTO") String tipo,
            @NotNull @DecimalMin(value = "0.01", message = "El valor del descuento debe ser mayor que 0")
            @Digits(integer = 10, fraction = 2) BigDecimal valor,
            @NotNull Instant inicio,
            @NotNull Instant fin) {

        public AplicarDescuentoRequest {
            tipo = tipo == null ? null : Saneador.texto(tipo).toUpperCase(Locale.ROOT);
        }
    }

    /** Quitar el descuento de un lote de productos. */
    public record QuitarDescuentoRequest(
            @NotEmpty @Size(max = Limites.MAX_LOTE, message = "Demasiados productos en un solo lote")
            List<@NotNull @Positive Long> productoIds) {
    }

    /* ── Contrato interno consumido por el servicio de compras ── */

    /**
     * `precio` ya llega efectivo (el de oferta si está vigente): `compras`
     * calcula el subtotal del carrito con este valor sin conocer descuentos.
     */
    public record LineaPrecio(Long productoId, String nombre, String imageUrl, BigDecimal precio, Integer stock) {

        public static LineaPrecio desde(Producto p) {
            return desde(p, Instant.now());
        }

        public static LineaPrecio desde(Producto p, Instant ahora) {
            return new LineaPrecio(
                    p.getId(), p.getName(), p.getImageUrl(), precioEfectivo(p, ahora), p.getStock());
        }
    }

    public record AjusteStock(@NotNull Long productoId, @NotNull Integer cantidad) {
    }

    public record AjusteStockLote(@NotNull List<AjusteStock> lineas) {
    }

    /** La oferta está activa si hay precio de oferta y la fecha actual cae en la vigencia. */
    static boolean estaEnOferta(Producto p, Instant ahora) {
        return p.getPrecioOferta() != null
                && (p.getOfertaInicio() == null || !ahora.isBefore(p.getOfertaInicio()))
                && (p.getOfertaFin() == null || !ahora.isAfter(p.getOfertaFin()));
    }

    /** Lo que paga el cliente: precio de oferta si está vigente, si no el de lista. */
    static BigDecimal precioEfectivo(Producto p, Instant ahora) {
        return estaEnOferta(p, ahora) ? p.getPrecioOferta() : p.getPrecio();
    }

    /** Motivo del rechazo. Se le enseña al colaborador, así que tiene que servirle. */
    public record RechazoProductoRequest(
            @NotBlank @Size(min = 10, max = 500,
                    message = "Explica el motivo en entre 10 y 500 caracteres") String motivo) {

        public RechazoProductoRequest {
            motivo = Saneador.textoMultilinea(motivo);
        }
    }
}
