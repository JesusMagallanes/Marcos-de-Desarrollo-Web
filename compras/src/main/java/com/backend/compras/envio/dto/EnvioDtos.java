package com.backend.compras.envio.dto;

import java.time.LocalDateTime;

import com.backend.compras.envio.Distancia;
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
            String referencia,
            String telefonoContacto,
            EstadoEnvio estadoEnvio,
            LocalDateTime fechaEnvioProgramado,
            LocalDateTime fechaEnvioEntregado,

            /*
             * Épica 3: a qué distancia está el destino de la tienda y cuánto se
             * tarda. Son null si el comprador no compartió su ubicación.
             *
             * `distanciaEsEstimada` no es decoración: el cálculo es en línea
             * recta con un ajuste, NO una ruta real. Enseñar "23 min" sin decir
             * que es una estimación sería mentirle a quien organiza el reparto.
             */
            Double distanciaKm,
            Integer minutosEstimados,
            Boolean distanciaEsEstimada) {

        public static EnvioResponse desde(Envio e) {
            return desde(e, null);
        }

        public static EnvioResponse desde(Envio e, Distancia.Estimacion estimacion) {
            return new EnvioResponse(
                    e.getId(),
                    e.getPedido().getId(),
                    e.getDireccion(),
                    e.getReferencia(),
                    e.getTelefonoContacto(),
                    e.getEstadoEnvio(),
                    e.getFechaEnvioProgramado(),
                    e.getFechaEnvioEntregado(),
                    estimacion == null ? null : estimacion.kilometros(),
                    estimacion == null ? null : estimacion.minutos(),
                    estimacion == null ? null : estimacion.esEstimacion());
        }
    }

    public record CambioEstadoEnvio(@NotNull EstadoEnvio estadoEnvio) {
    }
}
