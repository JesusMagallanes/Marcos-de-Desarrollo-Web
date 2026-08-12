package com.backend.catalogo.shared.seguridad;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;

/**
 * A05: CORS cerrado a la lista blanca de `seguridad.cors.origenes` (variable de
 * entorno CORS_ORIGENES). Solo la aplicación puede llamar a la API desde un
 * navegador; cualquier otra web recibe un preflight denegado.
 *
 * Tres decisiones deliberadas:
 *
 * - Nada de "*", ni en orígenes ni en cabeceras. Con `setAllowedHeaders("*")` un
 *   atacante puede colar cabeceras que el backend no espera, y el comodín en
 *   orígenes convierte la API en pública para cualquier página del mundo.
 * - `allowCredentials=false`: el token viaja en la cabecera Authorization, no en
 *   una cookie. Sin credenciales el navegador no adjunta nada de forma
 *   automática, así que no hay sesión que un tercero pueda reutilizar.
 * - Se falla al arrancar si la lista está vacía o trae un comodín: es preferible
 *   no levantar el servicio a levantarlo con el CORS abierto de par en par.
 *
 * LO IMPORTANTE, y lo que no es evidente: el MISMO ORIGEN siempre se permite,
 * esté o no en la lista. Los navegadores envían la cabecera `Origin` también en
 * las peticiones del propio sitio cuando no son GET (POST, PUT, PATCH, DELETE).
 * Si solo se mirara la lista blanca, desplegar la tienda en un dominio que no
 * esté enumerado dejaría la web servida y navegable pero con TODAS las
 * escrituras devolviendo 403: login, carrito, checkout. Un fallo silencioso y
 * desconcertante, porque los GET seguirían funcionando.
 *
 * Permitirlo no abre nada: una petición del mismo origen no es cross-origin por
 * definición, y CORS solo protege del navegador. Quien llama desde fuera de un
 * navegador puede omitir `Origin` y ya pasa igual (eso es así en cualquier
 * configuración de CORS); la protección real de esos casos es el JWT.
 */
@Configuration
public class CorsConfig {

    /** Lo único que la aplicación necesita enviar. Lista cerrada. */
    private static final List<String> CABECERAS_PERMITIDAS = List.of(
            "Authorization", "Content-Type", "Accept", "X-Correlation-Id");

    private static final List<String> METODOS_PERMITIDOS = List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    private final List<String> origenes;
    private final IpCliente ipCliente;

    public CorsConfig(@Value("${seguridad.cors.origenes}") List<String> origenes, IpCliente ipCliente) {
        this.origenes = validar(origenes);
        this.ipCliente = ipCliente;
    }

    static List<String> validar(List<String> configurados) {
        List<String> limpios = configurados == null ? List.of()
                : configurados.stream().map(String::trim).filter(o -> !o.isBlank()).toList();

        if (limpios.isEmpty()) {
            throw new IllegalStateException(
                    "seguridad.cors.origenes está vacío: define CORS_ORIGENES con el origen de la aplicación");
        }
        if (limpios.stream().anyMatch(o -> o.contains("*"))) {
            throw new IllegalStateException(
                    "seguridad.cors.origenes no admite comodines: enumera los orígenes exactos");
        }
        return limpios;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration listaBlanca = base();
        listaBlanca.setAllowedOrigins(origenes);

        return peticion -> {
            String origen = peticion.getHeader(HttpHeaders.ORIGIN);

            if (origen != null && esMismoOrigen(peticion, origen)) {
                CorsConfiguration propio = base();
                propio.setAllowedOrigins(List.of(origen));
                return propio;
            }
            return listaBlanca;
        };
    }

    private CorsConfiguration base() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedMethods(METODOS_PERMITIDOS);
        config.setAllowedHeaders(CABECERAS_PERMITIDAS);
        config.setExposedHeaders(List.of("X-Correlation-Id"));
        config.setAllowCredentials(false);
        // Cachea el preflight media hora: menos OPTIONS sin relajar nada.
        config.setMaxAge(1800L);
        return config;
    }

    /**
     * Compara el `Origin` con el host al que el navegador cree que está hablando.
     *
     * Detrás del gateway hay un salto que rompe la comparación ingenua: al
     * reenviar la petición, el proxy reescribe `Host` con el destino interno
     * (localhost:8082), mientras que `Origin` sigue siendo el del navegador. Los
     * dos nunca coinciden y una petición legítima del propio sitio acababa
     * rechazada con 403 por el servicio de dentro, no por el gateway.
     *
     * Por eso se mira antes `X-Forwarded-Host`, que es donde el proxy deja el
     * host original. Solo se hace caso a esa cabecera si quien nos habla es un
     * proxy de la lista de confianza: si no, cualquiera podría enviarla para que
     * su origen pareciera el nuestro.
     */
    private boolean esMismoOrigen(HttpServletRequest peticion, String origen) {
        boolean deProxyFiable = ipCliente.esProxyDeConfianza(peticion.getRemoteAddr());

        String host = deProxyFiado(peticion, "X-Forwarded-Host", deProxyFiable);
        if (host == null) {
            host = peticion.getHeader(HttpHeaders.HOST);
        }
        if (host == null || host.isBlank()) {
            return false;
        }

        String esquema = deProxyFiado(peticion, "X-Forwarded-Proto", deProxyFiable);
        if (esquema == null) {
            esquema = peticion.getScheme();
        }

        return origen.equalsIgnoreCase(esquema + "://" + host);
    }

    /** Primer valor de una cabecera de reenvío, solo si el emisor es de fiar. */
    private String deProxyFiado(HttpServletRequest peticion, String cabecera, boolean fiable) {
        if (!fiable) {
            return null;
        }
        String valor = peticion.getHeader(cabecera);
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String primero = valor.split(",")[0].trim();
        return primero.isEmpty() ? null : primero;
    }
}
