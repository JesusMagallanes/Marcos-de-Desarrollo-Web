package com.backend.compras.carrito.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class CarritoDtos {

    private CarritoDtos() {
    }

    /** Unidades por línea de carrito. Una tienda no vende 2.000 millones de nada. */
    public static final int MAX_CANTIDAD = 1000;

    /** Coincide con la interfaz CarritoItem del frontend. */
    public record ItemResponse(
            Long itemId,
            Long productId,
            String nombre,
            BigDecimal precio,
            Integer cantidad,
            String image,
            Integer stockDisponible) {
    }

    /**
     * Toda operación de carrito devuelve el carrito completo, así el frontend
     * no necesita un GET extra después de cada cambio.
     *
     * <p>El envío y el total viajan calculados desde aquí y no se recomponen en
     * el navegador. Antes el frontend se traía solo el subtotal y sumaba su
     * propia copia del umbral y del costo: dos reglas de negocio escritas dos
     * veces, y la que se cobraba era la otra. Lo que se enseña y lo que se cobra
     * salen ahora del mismo cálculo.
     */
    public record CarritoResponse(
            List<ItemResponse> items,
            BigDecimal subtotal,
            BigDecimal costoEnvio,
            BigDecimal total) {
    }

    /**
     * El tope de cantidad no es cosmético. Sin `@Max`, un `cantidad` de
     * 2.000.000.000 pasaba la validación y llegaba al cálculo del subtotal y a la
     * reserva de stock: multiplicado por el precio desborda el rango de la
     * columna NUMERIC(12,2) y, de paso, permite bloquear el inventario entero de
     * un producto con una sola petición.
     */
    public record AgregarItemRequest(
            @NotNull @Positive Long productoId,
            @NotNull @Min(value = 1, message = "La cantidad mínima es 1")
            @Max(value = MAX_CANTIDAD, message = "La cantidad máxima por producto es " + MAX_CANTIDAD)
            Integer cantidad) {
    }

    public record CambiarCantidadRequest(
            @NotNull @Min(value = 1, message = "La cantidad mínima es 1")
            @Max(value = MAX_CANTIDAD, message = "La cantidad máxima por producto es " + MAX_CANTIDAD)
            Integer cantidad) {
    }
}
