package com.backend.web_gateway.config;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A09: el gateway es la puerta de entrada, así que aquí nace el identificador de
 * correlación que después arrastran los demás servicios.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelacionFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest peticion,
            HttpServletResponse respuesta,
            FilterChain cadena) throws ServletException, IOException {

        String correlacion = peticion.getHeader(CABECERA);
        if (correlacion == null || correlacion.isBlank() || correlacion.length() > 64) {
            correlacion = UUID.randomUUID().toString();
        }

        MDC.put("correlacionId", correlacion);

        try {
            cadena.doFilter(peticion, respuesta);
        } finally {
            // Se escribe DESPUÉS de la cadena, no antes.
            if (!respuesta.isCommitted()) {
                respuesta.setHeader(CABECERA, correlacion);
            }
            MDC.clear();
        }
    }
}
