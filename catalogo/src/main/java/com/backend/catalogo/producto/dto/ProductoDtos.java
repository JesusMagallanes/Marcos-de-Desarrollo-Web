package com.backend.catalogo.producto.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.backend.catalogo.producto.Producto;
import com.backend.catalogo.producto.ProductoImagen;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
            String marcaName) {

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
                    p.getMarca() != null ? p.getMarca().getName() : null);
        }
    }

    public record ProductoRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 500) String description,
            @Size(max = 2000) String specifications,
            @NotNull @DecimalMin(value = "0.01", message = "El precio debe ser mayor que 0") BigDecimal precio,
            @Size(max = 20) List<@Size(max = 1000) String> imagenes,
            @NotNull @Min(value = 0, message = "El stock no puede ser negativo") Integer stock,
            @NotNull Long categoriaId,
            Long marcaId) {
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
     * Aplicar un descuento a un lote de productos. `tipo` es `PORCENTAJE`
     * (valor en %) o `MONTO` (valor en soles).
     */
    public record AplicarDescuentoRequest(
            @NotEmpty List<Long> productoIds,
            @NotBlank String tipo,
            @NotNull @DecimalMin(value = "0.01", message = "El valor del descuento debe ser mayor que 0") BigDecimal valor,
            @NotNull Instant inicio,
            @NotNull Instant fin) {
    }

    /** Quitar el descuento de un lote de productos. */
    public record QuitarDescuentoRequest(@NotEmpty List<Long> productoIds) {
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
}
