package com.backend.usuarios.shared.correo;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.backend.usuarios.shared.metricas.MetricasSeguridad;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Avisos por correo.
 *
 * <p>Dos decisiones que valen para todo lo que salga de aquí:
 *
 * <ol>
 *   <li><b>Un fallo al enviar no deshace nada.</b> Si el servidor de correo no
 *       responde, la aprobación ya está hecha y tiene que seguir en pie: sería
 *       absurdo negarle el rol a alguien porque no salió un correo. Se registra
 *       y se sigue.
 *   <li><b>Sin credenciales configuradas, no se rompe.</b> El correo es opcional
 *       igual que el login con Google: en desarrollo casi nunca está puesto. En
 *       ese caso se escribe en el log lo que se habría enviado, que además
 *       permite comprobar el texto sin mandar nada a nadie.
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CorreoService {

    /**
     * `ObjectProvider` y no inyección directa: si no hay
     * `spring.mail.host` configurado, Spring Boot no crea el `JavaMailSender` y
     * una inyección normal impediría arrancar el servicio entero.
     */
    private final ObjectProvider<JavaMailSender> remitente;
    private final MetricasSeguridad metricas;

    @Value("${spring.mail.username:}")
    private String usuario;

    @Value("${app.correo.remitente:SmartZone <no-responder@smartzone.com>}")
    private String de;

    /** Si no, el enlace del correo no lleva a ninguna parte. */
    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    /* ── Solicitudes de colaborador ── */

    public void solicitudAprobada(String destinatario, String nombreComercial) {
        enviar(destinatario,
                "Ya puedes vender en SmartZone",
                """
                        ¡Buenas noticias!

                        Revisamos la solicitud de %s y está aprobada. Ya puedes publicar
                        productos en la tienda.

                        Entra aquí para empezar: %s/vender/mis-productos

                        Ten en cuenta que lo que publiques pasa por una revisión antes de
                        aparecer en la tienda. Suele ser rápido.

                        — El equipo de SmartZone
                        """.formatted(nombreComercial, frontendUrl));
    }

    /**
     * El motivo va tal cual en el cuerpo: es lo único que le dice al solicitante
     * qué tiene que corregir, y sin él el correo solo sirve para disgustar.
     */
    public void solicitudRechazada(String destinatario, String nombreComercial, String motivo) {
        enviar(destinatario,
                "Sobre tu solicitud para vender en SmartZone",
                """
                        Hola:

                        Revisamos la solicitud de %s y esta vez no pudimos aprobarla.

                        Motivo:
                        %s

                        Puedes corregirlo y volver a enviarla cuando quieras: %s/vender

                        — El equipo de SmartZone
                        """.formatted(nombreComercial, motivo, frontendUrl));
    }

    /* ── Envío ── */

    private void enviar(String destinatario, String asunto, String cuerpo) {
        if (destinatario == null || destinatario.isBlank()) {
            log.warn("Aviso sin destinatario: {}", asunto);
            metricas.correo("omitido");
            return;
        }

        JavaMailSender emisor = remitente.getIfAvailable();
        if (emisor == null || usuario == null || usuario.isBlank()) {
            // En desarrollo esto es lo normal. Se deja el texto en el log para
            // poder revisarlo sin mandar correos de verdad.
            log.info("""
                    CORREO NO CONFIGURADO — se habría enviado:
                      para: {}
                      asunto: {}
                    {}""", destinatario, asunto, cuerpo);
            metricas.correo("omitido");
            return;
        }

        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom(de);
            mensaje.setTo(destinatario);
            mensaje.setSubject(asunto);
            mensaje.setText(cuerpo);
            emisor.send(mensaje);

            log.info("Aviso enviado: {}", asunto);
            metricas.correo("enviado");

        } catch (RuntimeException ex) {
            // No se propaga: lo que provocó el correo ya ocurrió y es válido.
            // Un servidor de correo caído es invisible si no se cuenta: los
            // avisos fallan en silencio a propósito para no deshacer lo que los
            // provocó.
            log.error("No se pudo enviar el aviso «{}»: {}", asunto, ex.getMessage());
            metricas.correo("fallido");
        }
    }
}
