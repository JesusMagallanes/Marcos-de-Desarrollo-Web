package com.backend.compras.carrito.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class CarritoDtos {

    private CarritoDtos() {
    }

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
     */
    public record CarritoResponse(List<ItemResponse> items, BigDecimal subtotal) {
    }

    public record AgregarItemRequest(
            @NotNull Long productoId,
            @NotNull @Min(value = 1, message = "La cantidad mínima es 1") Integer cantidad) {
    }

    public record CambiarCantidadRequest(
            @NotNull @Min(value = 1, message = "La cantidad mínima es 1") Integer cantidad) {
    }
}
