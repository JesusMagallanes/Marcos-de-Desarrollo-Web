package com.backend.compras.pago.dto;

import java.math.BigDecimal;

import com.backend.compras.shared.validacion.Saneador;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class PagoDtos {

    private PagoDtos() {
    }

    /**
     * Nótese que NO hay campo de importe: el total lo calcula el servidor
     * releyendo el carrito. En el monolito el precio venía del navegador.
     */
    public record PreferenciaRequest(@NotNull @Positive Long metodoPagoId) {
    }

    public record PreferenciaResponse(
            String id,
            String init_point,
            String sandbox_init_point,
            BigDecimal total) {
    }

    /**
     * `paymentId` viaja a la URL de la API de MercadoPago, así que se acota a lo
     * que la pasarela emite de verdad: dígitos. Sin el patrón, un valor con
     * barras o `..` podía torcer la ruta de la llamada saliente.
     */
    public record ConfirmarRequest(
            @NotBlank @Size(max = 100)
            @Pattern(regexp = "^[0-9]+$", message = "El identificador de pago no es válido")
            String paymentId) {

        public ConfirmarRequest {
            paymentId = Saneador.texto(paymentId);
        }
    }
}
