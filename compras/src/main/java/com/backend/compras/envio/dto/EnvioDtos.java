package com.backend.compras.envio.dto;

import java.time.LocalDateTime;

import com.backend.compras.envio.Envio;
import com.backend.compras.envio.EstadoEnvio;

import jakarta.validation.constraints.NotNull;

public final class EnvioDtos {

    private EnvioDtos() {
    }

    public record EnvioResponse(
            Long id,
            Long pedidoId,
            String direccion,
            EstadoEnvio estadoEnvio,
            LocalDateTime fechaEnvioProgramado,
            LocalDateTime fechaEnvioEntregado) {

        public static EnvioResponse desde(Envio e) {
            return new EnvioResponse(
                    e.getId(),
                    e.getPedido().getId(),
                    e.getDireccion(),
                    e.getEstadoEnvio(),
                    e.getFechaEnvioProgramado(),
                    e.getFechaEnvioEntregado());
        }
    }

    public record CambioEstadoEnvio(@NotNull EstadoEnvio estadoEnvio) {
    }
}
