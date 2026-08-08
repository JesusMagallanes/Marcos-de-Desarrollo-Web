package com.backend.compras.pago.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class PagoDtos {

    private PagoDtos() {
    }

    /**
     * Nótese que NO hay campo de importe: el total lo calcula el servidor
     * releyendo el carrito. En el monolito el precio venía del navegador.
     */
    public record PreferenciaRequest(@NotNull Long metodoPagoId) {
    }

    public record PreferenciaResponse(
            String id,
            String init_point,
            String sandbox_init_point,
            BigDecimal total) {
    }

    public record ConfirmarRequest(@NotBlank String paymentId) {
    }
}
