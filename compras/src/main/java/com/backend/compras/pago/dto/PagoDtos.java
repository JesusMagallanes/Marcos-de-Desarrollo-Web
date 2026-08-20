package com.backend.compras.pago.dto;

import java.math.BigDecimal;

import com.backend.compras.shared.validacion.Saneador;

import jakarta.validation.Valid;
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
    public record PreferenciaRequest(
            @NotNull @Positive Long metodoPagoId,

            /*
             * A donde va el pedido, en partes y no en una linea suelta: sin
             * codigo postal no hay costo de envio, sin distrito no se agrupa el
             * reparto, y la pasarela no acepta una cadena.
             */
            @NotNull(message = "Falta la dirección de entrega")
            @Valid DireccionEntrega entrega) {
    }

    /**
     * Lo que el checkout devuelve al navegador.
     *
     * <p>No todos los medios de pago mandan a una pasarela: el contra entrega se
     * cierra aquí mismo y no hay ninguna URL a la que ir. De ahí
     * {@code requierePasarela}, que es lo que el frontend mira para decidir si
     * redirige o si lleva al comprador directo a sus compras. Deducirlo de que
     * las URLs vengan vacías sería adivinar: vacías también vienen cuando
     * MercadoPago falla, y son dos situaciones opuestas.
     */
    public record PreferenciaResponse(
            String id,
            String init_point,
            String sandbox_init_point,
            BigDecimal total,
            boolean requierePasarela,
            /** El pedido ya creado. Solo viene cuando no hay pasarela de por medio. */
            Long pedidoId) {

        /** Checkout con pasarela: hay que mandar al comprador a pagar fuera. */
        public static PreferenciaResponse conPasarela(String id, String initPoint,
                String sandboxInitPoint, BigDecimal total) {
            return new PreferenciaResponse(id, initPoint, sandboxInitPoint, total, true, null);
        }

        /** Contra entrega: la compra ya está hecha, se cobra al recibir. */
        public static PreferenciaResponse sinPasarela(BigDecimal total, Long pedidoId) {
            return new PreferenciaResponse(null, null, null, total, false, pedidoId);
        }
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
