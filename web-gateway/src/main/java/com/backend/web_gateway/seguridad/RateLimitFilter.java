package com.backend.web_gateway.seguridad;

import java.io.IOException;
import java.time.Duration;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.backend.web_gateway.metricas.MetricasSeguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Primera línea de defensa frente a abuso: el gateway es la única puerta desde
 * internet, así que aquí se frena lo que no debería llegar a los servicios.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final LimitadorPeticiones limitador;
    private final MetricasSeguridad metricas;

    private record Cupo(String ambito, int maximo, Duration ventana) {
    }

    private static final Cupo AUTENTICACION = new Cupo("autenticacion", 20, Duration.ofMinutes(15));
    private static final Cupo PAGOS = new Cupo("pagos", 20, Duration.ofMinutes(10));
    private static final Cupo ESCRITURA = new Cupo("escritura", 120, Duration.ofMinutes(1));
    private static final Cupo LECTURA = new Cupo("lectura", 600, Duration.ofMinutes(1));

    @Override
    protected void doFilterInternal(HttpServletRequest peticion,
            HttpServletResponse respuesta,
            FilterChain cadena) throws ServletException, IOException {

        Cupo cupo = cupoDe(peticion);
        String clave = ipCliente(peticion) + "|" + cupo.ambito();

        if (!limitador.permitir(clave, cupo.maximo(), cupo.ventana())) {
            long reintentar = limitador.segundosParaReintentar(clave, cupo.ventana());
            metricas.rateLimitBloqueado(cupo.ambito());
            log.warn("Cupo '{}' excedido desde {} en {}",
                    cupo.ambito(), ipCliente(peticion), peticion.getRequestURI());

            rechazar(respuesta, reintentar);
            return;
        }

        cadena.doFilter(peticion, respuesta);
    }

    private Cupo cupoDe(HttpServletRequest peticion) {
        String ruta = peticion.getRequestURI();

        if (ruta.startsWith("/api/auth/") || ruta.startsWith("/oauth2/")) {
            return AUTENTICACION;
        }
        if (ruta.startsWith("/api/pagos/")) {
            return PAGOS;
        }
        return "GET".equals(peticion.getMethod()) ? LECTURA : ESCRITURA;
    }

    /** El healthcheck y los assets no consumen cupo. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        String ruta = peticion.getRequestURI();
        return ruta.startsWith("/actuator/health")
                || ruta.equals("/")
                || ruta.equals("/index.html")
                || ruta.startsWith("/Img/")
                || ruta.startsWith("/assets/")
                || ruta.endsWith(".js")
                || ruta.endsWith(".css")
                || ruta.endsWith(".ico");
    }

    /**
     * Detrás de un balanceador la IP real llega en X-Forwarded-For. Se toma solo el primer
     * salto: el resto de la cadena la puede falsificar el cliente.
     */
    private String ipCliente(HttpServletRequest peticion) {
        String reenviada = peticion.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            String primera = reenviada.split(",")[0].trim();
            // Acota la longitud: una cabecera enorme no debe crear una clave enorme.
            return primera.length() > 45 ? primera.substring(0, 45) : primera;
        }
        return peticion.getRemoteAddr();
    }

    private void rechazar(HttpServletResponse respuesta, long reintentar) throws IOException {
        respuesta.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        respuesta.setHeader("Retry-After", String.valueOf(reintentar));
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        respuesta.getWriter().write("""
                {"type":"about:blank","title":"Demasiadas peticiones","status":429,\
                "detail":"Has hecho demasiadas peticiones. Espera %d segundos."}"""
                .formatted(reintentar));
    }
}
