package com.backend.usuarios.shared.seguridad;

import java.io.IOException;
import java.time.Duration;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.backend.usuarios.shared.metricas.MetricasSeguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Aplica cupos distintos según lo caro que sea abusar del endpoint. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final LimitadorPeticiones limitador;
    private final MetricasSeguridad metricas;
    private final IpCliente ipCliente;

    private static final Duration VENTANA_LOGIN = Duration.ofMinutes(15);
    private static final int MAXIMO_LOGIN = 10;

    private static final Duration VENTANA_REGISTRO = Duration.ofHours(1);
    private static final int MAXIMO_REGISTRO = 5;

    private static final Duration VENTANA_GENERAL = Duration.ofMinutes(1);
    private static final int MAXIMO_GENERAL = 300;

    @Override
    protected void doFilterInternal(HttpServletRequest peticion,
            HttpServletResponse respuesta,
            FilterChain cadena) throws ServletException, IOException {

        String ruta = peticion.getRequestURI();
        String ip = ipCliente.de(peticion);

        int maximo;
        Duration ventana;

        if (ruta.startsWith("/api/auth/login")) {
            maximo = MAXIMO_LOGIN;
            ventana = VENTANA_LOGIN;
        } else if (ruta.startsWith("/api/auth/registrar")) {
            maximo = MAXIMO_REGISTRO;
            ventana = VENTANA_REGISTRO;
        } else {
            maximo = MAXIMO_GENERAL;
            ventana = VENTANA_GENERAL;
        }

        String clave = ip + "|" + (ruta.startsWith("/api/auth/") ? ruta : "general");

        if (!limitador.permitir(clave, maximo, ventana)) {
            long reintentar = limitador.segundosParaReintentar(clave, ventana);
            metricas.rateLimitBloqueado(ambito(ruta));
            log.warn("Cupo excedido para {} en {}", ip, ruta);

            respuesta.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            respuesta.setHeader("Retry-After", String.valueOf(reintentar));
            respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            respuesta.getWriter().write("""
                    {"type":"about:blank","title":"Demasiadas peticiones",\
                    "status":429,"detail":"Has hecho demasiados intentos. Espera %d segundos."}"""
                    .formatted(reintentar));
            return;
        }

        cadena.doFilter(peticion, respuesta);
    }

    /** Etiqueta de baja cardinalidad para la métrica. */
    private String ambito(String ruta) {
        if (ruta.startsWith("/api/auth/login")) return "login";
        if (ruta.startsWith("/api/auth/registrar")) return "registro";
        return "general";
    }

}
