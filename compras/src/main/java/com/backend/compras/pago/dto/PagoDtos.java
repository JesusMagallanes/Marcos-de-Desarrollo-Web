package com.backend.compras.pago.dto;

import java.math.BigDecimal;

import com.backend.compras.shared.validacion.Saneador;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
             * A dónde va el pedido. Se pide AQUÍ, al iniciar el checkout, y no al
             * confirmar el pago: el comprador todavía está en la tienda, y
             * después se va a MercadoPago y puede no volver. Así el importe y el
             * destino quedan fijados juntos antes de pagar.
             *
             * Antes esto no existía y el envío se creaba con la cadena literal
             * "Por confirmar": había cobro, había stock descontado y no había a
             * dónde mandarlo.
             */
            @NotBlank(message = "Indica la dirección de entrega")
            @Size(max = 200) String direccionEnvio,

            /** Piso, referencia, "casa de rejas verdes". Ayuda al repartidor. */
            @Size(max = 200) String referenciaEnvio,

            @NotBlank(message = "Necesitamos un teléfono para coordinar la entrega")
            @Pattern(regexp = "^[0-9]{9}$",
                    message = "El teléfono debe tener 9 dígitos") String telefonoContacto,

            /*
             * Punto de entrega (Épica 3). OPCIONALES: solo llegan si el comprador
             * aceptó compartir su ubicación. Sin ellas la compra sigue igual; lo
             * único que se pierde es el cálculo de distancia para quien reparte.
             */
            @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") BigDecimal latitud,
            @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") BigDecimal longitud) {

        public PreferenciaRequest {
            direccionEnvio = Saneador.texto(direccionEnvio);
            referenciaEnvio = Saneador.texto(referenciaEnvio);
            telefonoContacto = Saneador.texto(telefonoContacto);
        }
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
