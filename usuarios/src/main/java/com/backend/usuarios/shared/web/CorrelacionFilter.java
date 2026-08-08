package com.backend.usuarios.shared.web;

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

/** A09 (Fallos de registro y monitorización). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelacionFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Correlation-Id";
    public static final String MDC_CORRELACION = "correlacionId";
    public static final String MDC_USUARIO = "usuarioId";

    @Override
    protected void doFilterInternal(HttpServletRequest peticion,
            HttpServletResponse respuesta,
            FilterChain cadena) throws ServletException, IOException {

        String correlacion = peticion.getHeader(CABECERA);
        if (correlacion == null || correlacion.isBlank() || correlacion.length() > 64) {
            correlacion = UUID.randomUUID().toString();
        }

        MDC.put(MDC_CORRELACION, correlacion);
        respuesta.setHeader(CABECERA, correlacion);

        try {
            cadena.doFilter(peticion, respuesta);
        } finally {
            // Imprescindible: los hilos se reutilizan y el MDC se filtraría
            // a peticiones de otros usuarios.
            MDC.clear();
        }
    }
}
