package com.backend.usuarios.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * El secreto debe ser idéntico en los 4 servicios: `usuarios` firma y los demás
 * verifican con la misma clave HMAC.
 */
@ConfigurationProperties(prefix = "seguridad.jwt")
public record JwtProperties(
        String secreto,
        String emisor,
        String audiencia,
        long expiracionCliente,
        long expiracionEmpleado,
        long expiracionAdministrador,
        long expiracionRefresh) {
}
