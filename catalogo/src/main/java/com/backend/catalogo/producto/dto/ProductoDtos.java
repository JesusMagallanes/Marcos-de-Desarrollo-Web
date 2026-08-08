package com.backend.catalogo.producto.dto;

import java.math.BigDecimal;
import java.util.List;

import com.backend.catalogo.producto.Producto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ProductoDtos {

    private ProductoDtos() {
    }

    /**
     * Categoría y marca viajan como id + nombre planos, nunca como objeto
     * anidado: así `compras` puede referenciar un producto sin compartir
     * entidades JPA con este servicio.
     */
    public record ProductoResponse(
            Long id,
            String name,
            String description,
            BigDecimal precio,
            String imageUrl,
            Integer stock,
            Long categoriaId,
            String categoriaName,
            Long marcaId,
            String marcaName) {

        public static ProductoResponse desde(Producto p) {
            return new ProductoResponse(
                    p.getId(),
                    p.getName(),
                    p.getDescription(),
                    p.getPrecio(),
                    p.getImageUrl(),
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
            @NotNull @DecimalMin(value = "0.01", message = "El precio debe ser mayor que 0") BigDecimal precio,
            String imageUrl,
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

    /* ── Contrato interno consumido por el servicio de compras ── */

    public record LineaPrecio(Long productoId, String nombre, String imageUrl, BigDecimal precio, Integer stock) {

        public static LineaPrecio desde(Producto p) {
            return new LineaPrecio(p.getId(), p.getName(), p.getImageUrl(), p.getPrecio(), p.getStock());
        }
    }

    public record AjusteStock(@NotNull Long productoId, @NotNull Integer cantidad) {
    }

    public record AjusteStockLote(@NotNull List<AjusteStock> lineas) {
    }
}
