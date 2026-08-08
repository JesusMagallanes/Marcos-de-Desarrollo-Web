package com.backend.web_gateway.config;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * El gateway no toca base de datos, así que lo único imprescindible es el secreto JWT:
 * sin él no puede descartar los tokens inválidos antes de reenviarlos.
 */
public class ValidacionArranque implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final int LONGITUD_MINIMA_SECRETO = 32;

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent evento) {
        ConfigurableEnvironment entorno = evento.getEnvironment();
        String secreto = entorno.getProperty("JWT_SECRET");

        String problema = null;
        if (secreto == null || secreto.isBlank()) {
            problema = "  · JWT_SECRET — la clave de verificación de los tokens";
        } else if (secreto.getBytes(StandardCharsets.UTF_8).length < LONGITUD_MINIMA_SECRETO) {
            problema = "  · JWT_SECRET — tiene %d bytes y necesita al menos %d para HS256"
                    .formatted(secreto.getBytes(StandardCharsets.UTF_8).length, LONGITUD_MINIMA_SECRETO);
        }

        if (problema != null) {
            throw new IllegalStateException("""

                    ╔══════════════════════════════════════════════════════════════════╗
                    ║  CONFIGURACIÓN INCOMPLETA — el gateway no puede arrancar          ║
                    ╚══════════════════════════════════════════════════════════════════╝

                    Falta configurar:

                    %s

                    Cómo resolverlo:

                      1. Copia la plantilla en la raíz del repositorio:
                           cp .env.example .env
                      2. Genera un JWT_SECRET si aún no lo tienes:
                           node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"

                    Debe ser EXACTAMENTE el mismo valor en los cuatro servicios.
                    """.formatted(problema));
        }
    }
}
