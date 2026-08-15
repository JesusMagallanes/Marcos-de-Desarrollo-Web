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

    // Subida de documentos de identidad. Cupo propio y mucho más estrecho que el
    // general porque cada petición trae hasta 5 MB: con el general (300 por
    // minuto) una sola cuenta podía mandar 1,5 GB por minuto.
    //
    // Quien de verdad acota el DISCO es el índice único de la migración: un
    // archivo por tipo y usuario, se suba las veces que se suba. Esto es la
    // segunda capa, contra el ancho de banda y el trabajo de validar. Por eso el
    // número no necesita ser mezquino: el trámite pide tres archivos y 30 cada
    // diez minutos deja repetir de sobra los que salgan borrosos.
    private static final String RUTA_ADJUNTOS = "/api/colaboradores/solicitudes/adjuntos";
    private static final Duration VENTANA_ADJUNTOS = Duration.ofMinutes(10);
    private static final int MAXIMO_ADJUNTOS = 30;

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
        } else if (esSubidaDeDocumento(peticion, ruta)) {
            maximo = MAXIMO_ADJUNTOS;
            ventana = VENTANA_ADJUNTOS;
        } else {
            maximo = MAXIMO_GENERAL;
            ventana = VENTANA_GENERAL;
        }

        String clave = ip + "|" + claveDeAmbito(peticion, ruta);

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

    /**
     * Solo la SUBIDA lleva cupo estrecho, no la descarga: comparten prefijo pero
     * son cosas distintas. Mirar la descarga con el mismo rasero dejaría al
     * administrador sin poder revisar una bandeja con varias solicitudes.
     */
    private boolean esSubidaDeDocumento(HttpServletRequest peticion, String ruta) {
        return "POST".equals(peticion.getMethod()) && ruta.startsWith(RUTA_ADJUNTOS);
    }

    /** Cada cupo necesita su propia clave, o compartirían el contador. */
    private String claveDeAmbito(HttpServletRequest peticion, String ruta) {
        if (ruta.startsWith("/api/auth/")) {
            return ruta;
        }
        return esSubidaDeDocumento(peticion, ruta) ? "adjuntos" : "general";
    }

    /** Etiqueta de baja cardinalidad para la métrica. */
    private String ambito(String ruta) {
        if (ruta.startsWith("/api/auth/login")) return "login";
        if (ruta.startsWith("/api/auth/registrar")) return "registro";
        if (ruta.startsWith(RUTA_ADJUNTOS)) return "adjuntos";
        return "general";
    }

}
