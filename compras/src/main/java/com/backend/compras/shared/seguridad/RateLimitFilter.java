package com.backend.compras.shared.seguridad;

import java.io.IOException;
import java.time.Duration;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.backend.compras.shared.metricas.MetricasSeguridad;

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
    private final IpCliente ipCliente;

    private record Cupo(String ambito, int maximo, Duration ventana) {
    }

    private static final Cupo PAGOS = new Cupo("pagos", 60, Duration.ofMinutes(10));

    /*
     * El webhook de la pasarela no es una persona: son todas las notificaciones
     * de todas las compras, y llegan desde el puñado de IPs de MercadoPago. Con
     * el cupo de "pagos" se bloqueaban entre ellas en cuanto habia algo de
     * trafico, y una notificacion perdida es una venta que se queda sin cerrar.
     *
     * El gateway ya le dio cupo propio; aqui seguia cayendo en el de pagos, asi
     * que el limite efectivo era este y el arreglo de alli no servia de nada.
     */
    private static final Cupo WEBHOOK = new Cupo("webhook", 300, Duration.ofMinutes(1));
    private static final Cupo CARRITO = new Cupo("carrito", 200, Duration.ofMinutes(1));
    private static final Cupo ESCRITURA = new Cupo("escritura", 120, Duration.ofMinutes(1));
    private static final Cupo LECTURA = new Cupo("lectura", 600, Duration.ofMinutes(1));

    @Override
    protected void doFilterInternal(HttpServletRequest peticion,
            HttpServletResponse respuesta,
            FilterChain cadena) throws ServletException, IOException {

        Cupo cupo = cupoDe(peticion);
        String clave = ipCliente.de(peticion) + "|" + cupo.ambito();

        if (!limitador.permitir(clave, cupo.maximo(), cupo.ventana())) {
            long reintentar = limitador.segundosParaReintentar(clave, cupo.ventana());
            metricas.rateLimitBloqueado(cupo.ambito());
            log.warn("Cupo '{}' excedido desde {}", cupo.ambito(), ipCliente.de(peticion));

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

        // Antes que el de pagos: `/api/pagos/webhook` empieza igual y acababa
        // compartiendo el cupo de las personas.
        if (ruta.equals("/api/pagos/webhook")) {
            return WEBHOOK;
        }
        // El checkout mueve stock y dinero: es el más restrictivo de los que usa
        // una persona. El tope de verdad, el que cuenta por comprador y no por
        // IP, lo pone PagoController: aquí detrás del gateway todos los
        // compradores comparten una misma dirección.
        if (ruta.startsWith("/api/pagos/")) {
            return PAGOS;
        }
        if (ruta.startsWith("/api/carrito/")) {
            return CARRITO;
        }
        return "GET".equals(peticion.getMethod()) ? LECTURA : ESCRITURA;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        return peticion.getRequestURI().startsWith("/actuator/health");
    }

}
