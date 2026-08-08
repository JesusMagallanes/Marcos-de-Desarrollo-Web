package com.backend.catalogo.shared.seguridad;

import java.io.IOException;
import java.time.Duration;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.backend.catalogo.shared.metricas.MetricasSeguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Segunda barrera tras la del gateway. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final LimitadorPeticiones limitador;
    private final MetricasSeguridad metricas;

    private record Cupo(String ambito, int maximo, Duration ventana) {
    }

    private static final Cupo CHATBOT = new Cupo("chatbot", 30, Duration.ofMinutes(1));
    private static final Cupo INVENTARIO = new Cupo("inventario", 600, Duration.ofMinutes(1));
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
            log.warn("Cupo '{}' excedido desde {}", cupo.ambito(), ipCliente(peticion));

            respuesta.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            respuesta.setHeader("Retry-After", String.valueOf(reintentar));
            respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            respuesta.setCharacterEncoding("UTF-8");
            respuesta.getWriter().write("""
                    {"type":"about:blank","title":"Demasiadas peticiones","status":429,\
                    "detail":"Has hecho demasiadas peticiones. Espera %d segundos."}"""
                    .formatted(reintentar));
            return;
        }

        cadena.doFilter(peticion, respuesta);
    }

    private Cupo cupoDe(HttpServletRequest peticion) {
        String ruta = peticion.getRequestURI();

        if (ruta.startsWith("/api/chatbot/")) {
            return CHATBOT;
        }
        // Contrato interno con compras: cupo alto, lo consume un servicio, no
        // un navegador, y estrangularlo rompería el carrito.
        if (ruta.startsWith("/api/inventario/")) {
            return INVENTARIO;
        }
        return "GET".equals(peticion.getMethod()) ? LECTURA : ESCRITURA;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        return peticion.getRequestURI().startsWith("/actuator/health");
    }

    private String ipCliente(HttpServletRequest peticion) {
        String reenviada = peticion.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            String primera = reenviada.split(",")[0].trim();
            return primera.length() > 45 ? primera.substring(0, 45) : primera;
        }
        return peticion.getRemoteAddr();
    }
}
