package com.backend.compras.metodopago.dto;

import com.backend.compras.metodopago.MetodoPago;
import com.backend.compras.shared.validacion.Saneador;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class MetodoPagoDtos {

    private MetodoPagoDtos() {
    }

    public record MetodoPagoResponse(Long id, String name, String description) {

        public static MetodoPagoResponse desde(MetodoPago m) {
            return new MetodoPagoResponse(m.getId(), m.getName(), m.getDescription());
        }
    }

    public record MetodoPagoRequest(
            @NotBlank @Size(max = 50) String name,
            @NotBlank @Size(max = 200) String description) {

        /** A03: limpio antes de validar y antes del control de duplicados. */
        public MetodoPagoRequest {
            name = Saneador.texto(name);
            description = Saneador.textoMultilinea(description);
        }
    }
}
