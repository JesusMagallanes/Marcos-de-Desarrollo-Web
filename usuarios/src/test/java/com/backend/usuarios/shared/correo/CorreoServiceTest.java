package com.backend.usuarios.shared.correo;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import com.backend.usuarios.shared.metricas.MetricasSeguridad;

/**
 * Lo que se prueba del correo no es que envíe: eso lo hace la librería. Es que
 * <b>no tumbe lo que lo provocó</b>. Aprobar a alguien y que la operación se
 * caiga porque el servidor de correo no respondía sería absurdo: la aprobación
 * ya está hecha y es válida.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Avisos por correo")
class CorreoServiceTest {

    @Mock
    private ObjectProvider<JavaMailSender> proveedor;
    @Mock
    private JavaMailSender emisor;
    @Mock
    private MetricasSeguridad metricas;

    private CorreoService correo;

    @BeforeEach
    void preparar() {
        correo = new CorreoService(proveedor, metricas);
        ReflectionTestUtils.setField(correo, "usuario", "tienda@smartzone.com");
        ReflectionTestUtils.setField(correo, "de", "SmartZone <tienda@smartzone.com>");
        ReflectionTestUtils.setField(correo, "frontendUrl", "https://smartzone.test");
    }

    @Test
    @DisplayName("un fallo del servidor NO se propaga")
    void falloNoSePropaga() {
        when(proveedor.getIfAvailable()).thenReturn(emisor);
        doThrow(new MailSendException("servidor caído")).when(emisor).send(any(SimpleMailMessage.class));

        // Si esto lanzara, la aprobación que lo provocó se desharía.
        assertThatCode(() -> correo.solicitudAprobada("ana@t.com", "Importaciones Vega"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sin credenciales no se intenta enviar nada")
    void sinCredenciales() {
        // El caso normal en desarrollo. El texto queda en el log, que además
        // permite revisarlo sin mandar correos de verdad.
        ReflectionTestUtils.setField(correo, "usuario", "");

        correo.solicitudAprobada("ana@t.com", "Importaciones Vega");

        verify(emisor, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("sin destinatario tampoco")
    void sinDestinatario() {
        correo.solicitudAprobada(null, "Importaciones Vega");

        verify(emisor, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("el rechazo lleva el motivo: es lo único útil del correo")
    void rechazoLlevaMotivo() {
        when(proveedor.getIfAvailable()).thenReturn(emisor);

        correo.solicitudRechazada("ana@t.com", "Importaciones Vega",
                "La foto del reverso está movida y no se lee el código.");

        ArgumentCaptor<SimpleMailMessage> enviado = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(emisor).send(enviado.capture());

        assertThatCode(() -> {
            String cuerpo = enviado.getValue().getText();
            org.assertj.core.api.Assertions.assertThat(cuerpo).contains("movida");
            // Y el enlace para volver a intentarlo, o el correo solo sirve para
            // disgustar.
            org.assertj.core.api.Assertions.assertThat(cuerpo).contains("https://smartzone.test/vender");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la aprobación enlaza directamente a publicar")
    void aprobacionEnlazaAPublicar() {
        when(proveedor.getIfAvailable()).thenReturn(emisor);

        correo.solicitudAprobada("ana@t.com", "Importaciones Vega");

        ArgumentCaptor<SimpleMailMessage> enviado = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(emisor).send(enviado.capture());

        org.assertj.core.api.Assertions.assertThat(enviado.getValue().getText())
                .contains("https://smartzone.test/vender/mis-productos");
    }
}
