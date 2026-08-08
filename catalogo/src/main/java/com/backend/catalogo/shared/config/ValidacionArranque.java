package com.backend.catalogo.shared.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

/** Comprueba la configuración crítica ANTES de levantar el contexto. */
public class ValidacionArranque implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final int LONGITUD_MINIMA_SECRETO = 32;

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent evento) {
        ConfigurableEnvironment entorno = evento.getEnvironment();
        List<String> problemas = new ArrayList<>();

        exigir(entorno, problemas, "DB_URL", "la cadena JDBC de PostgreSQL");
        exigir(entorno, problemas, "DB_USER", "el usuario de base de datos");
        exigir(entorno, problemas, "DB_PASSWORD", "la contraseña de base de datos");

        String secreto = entorno.getProperty("JWT_SECRET");
        if (secreto == null || secreto.isBlank()) {
            problemas.add("  · JWT_SECRET — la clave de firma de los tokens");
        } else if (secreto.getBytes(StandardCharsets.UTF_8).length < LONGITUD_MINIMA_SECRETO) {
            problemas.add("  · JWT_SECRET — tiene %d bytes y necesita al menos %d para HS256"
                    .formatted(secreto.getBytes(StandardCharsets.UTF_8).length, LONGITUD_MINIMA_SECRETO));
        }

        if (!problemas.isEmpty()) {
            throw new IllegalStateException("""

                    ╔══════════════════════════════════════════════════════════════════╗
                    ║  CONFIGURACIÓN INCOMPLETA — el servicio no puede arrancar         ║
                    ╚══════════════════════════════════════════════════════════════════╝

                    Falta configurar:

                    %s

                    Cómo resolverlo:

                      1. Copia la plantilla en la raíz del repositorio:
                           cp .env.example .env
                      2. Rellena los valores que faltan.
                      3. Genera un JWT_SECRET propio si aún no lo tienes:
                           node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"

                    El mismo JWT_SECRET debe estar en los cuatro servicios.
                    """.formatted(String.join("\n", problemas)));
        }
    }

    private void exigir(ConfigurableEnvironment entorno, List<String> problemas,
            String clave, String descripcion) {
        String valor = entorno.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            problemas.add("  · %s — %s".formatted(clave, descripcion));
        }
    }
}
