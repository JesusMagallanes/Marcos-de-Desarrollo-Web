package com.backend.compras.shared.seguridad;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resuelve la IP real del cliente para usarla como clave del rate limit.
 *
 * El problema que arregla: `X-Forwarded-For` es una cabecera que ESCRIBE quien
 * hace la petición. Tomarla siempre —que es lo que se hacía— convierte el
 * limitador en decorativo, porque basta mandar una cabecera distinta en cada
 * intento para estrenar cupo cada vez. Justo en el endpoint de login, que es
 * donde el límite protege de la fuerza bruta. Y los puertos 8081-8083 son
 * alcanzables directamente, así que no vale suponer que todo pasa por el gateway.
 *
 * La regla correcta es: solo creerse la cabecera si quien nos habla es un proxy
 * nuestro. Se recorre la lista de derecha a izquierda (el orden en que la fueron
 * añadiendo los saltos) y se coge la primera entrada que NO sea un proxy de
 * confianza: esa es la más cercana al cliente que ningún intermediario nuestro
 * ha podido falsificar. Si quien conecta no es un proxy conocido, se ignora la
 * cabecera entera y se usa la dirección del socket, que no se puede mentir.
 */
@Component
public class IpCliente {

    /** Una IPv6 completa cabe en 45 caracteres; más que eso es basura. */
    private static final int MAX_LONGITUD = 45;

    private final List<String> proxiesDeConfianza;

    public IpCliente(@Value("${seguridad.proxies-de-confianza:127.0.0.1,::1,10.,172.,192.168.}") List<String> proxies) {
        this.proxiesDeConfianza = proxies.stream().map(String::trim).filter(p -> !p.isBlank()).toList();
    }

    public String de(HttpServletRequest peticion) {
        String remota = peticion.getRemoteAddr();

        if (remota == null) {
            return "desconocida";
        }
        if (!esProxyDeConfianza(remota)) {
            return recortar(remota);
        }

        String reenviada = peticion.getHeader("X-Forwarded-For");
        if (reenviada == null || reenviada.isBlank()) {
            return recortar(remota);
        }

        String[] saltos = reenviada.split(",");
        for (int i = saltos.length - 1; i >= 0; i--) {
            String salto = saltos[i].trim();
            if (!salto.isEmpty() && !esProxyDeConfianza(salto)) {
                return recortar(salto);
            }
        }

        return recortar(remota);
    }

    /**
     * Público porque CorsConfig necesita la misma noción de "esto viene de un
     * proxy nuestro" para decidir si se cree `X-Forwarded-Host`.
     */
    public boolean esProxyDeConfianza(String direccion) {
        return direccion != null && proxiesDeConfianza.stream().anyMatch(direccion::startsWith);
    }

    private String recortar(String valor) {
        return valor.length() > MAX_LONGITUD ? valor.substring(0, MAX_LONGITUD) : valor;
    }
}
