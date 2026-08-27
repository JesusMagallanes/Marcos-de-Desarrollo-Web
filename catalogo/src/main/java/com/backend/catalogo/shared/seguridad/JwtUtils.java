package com.backend.catalogo.shared.seguridad;

import org.springframework.security.oauth2.jwt.Jwt;

/** Extrae el ID de usuario del JWT, común a varios controllers. */
public final class JwtUtils {

    private JwtUtils() {
    }

    public static Long uidDe(Jwt jwt) {
        Object uid = jwt == null ? null : jwt.getClaim("uid");
        return uid == null ? null : ((Number) uid).longValue();
    }
}
