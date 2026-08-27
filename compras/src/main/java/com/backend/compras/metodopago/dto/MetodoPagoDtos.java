package com.backend.compras.metodopago.dto;

import com.backend.compras.metodopago.MetodoPago;
import com.backend.compras.shared.validacion.Saneador;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class MetodoPagoDtos {

    private MetodoPagoDtos() {
    }

    public record MetodoPagoResponse(Long id, String name, String description,
            MetodoPago.TipoPasarela tipo) {

        public static MetodoPagoResponse desde(MetodoPago m) {
            return new MetodoPagoResponse(m.getId(), m.getName(), m.getDescription(), m.getTipo());
        }
    }

    /**
     * El TIPO es obligatorio, y no se deduce del nombre.
     *
     * <p>Es lo que decide si el checkout manda a la pasarela o cierra el pedido
     * para cobrarlo contra entrega, así que dejarlo fuera del formulario no era
     * una omisión menor: todo método creado desde el panel nacía con el valor
     * por defecto —OTRO— y el checkout lo trataba como pago en efectivo. Un
     * método llamado «MercadoPago» daba el pedido por bueno, confirmaba el stock
     * y vaciaba el carrito <b>sin cobrar nada</b>.
     */
    public record MetodoPagoRequest(
            @NotBlank @Size(max = 50) String name,
            @NotBlank @Size(max = 200) String description,
            @NotNull MetodoPago.TipoPasarela tipo) {

        /** A03: limpio antes de validar y antes del control de duplicados. */
        public MetodoPagoRequest {
            name = Saneador.texto(name);
            description = Saneador.textoMultilinea(description);
        }
    }
}
