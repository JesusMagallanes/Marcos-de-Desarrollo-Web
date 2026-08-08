package com.backend.usuarios.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.backend.usuarios.usuario.Proveedor;

/**
 * Google y Facebook entregan los atributos de forma distinta. Lo crítico es
 * `emailVerificado`: sin esa comprobación, alguien podría crear una cuenta en
 * el proveedor con el correo de otra persona y apropiarse de su cuenta local.
 */
class DatosOAuthTest {

    @Test
    @DisplayName("Google: usa given_name/family_name y respeta email_verified")
    void google() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("email", "ana@ejemplo.com");
        attrs.put("given_name", "Ana");
        attrs.put("family_name", "Pérez");
        attrs.put("email_verified", true);

        DatosOAuth datos = DatosOAuth.desde(Proveedor.GOOGLE, attrs);

        assertThat(datos.email()).isEqualTo("ana@ejemplo.com");
        assertThat(datos.nombre()).isEqualTo("Ana");
        assertThat(datos.apellido()).isEqualTo("Pérez");
        assertThat(datos.emailVerificado()).isTrue();
    }

    @Test
    @DisplayName("Google: un correo sin verificar se marca como tal")
    void googleSinVerificar() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("email", "dudoso@ejemplo.com");
        attrs.put("given_name", "Duda");
        attrs.put("family_name", "Sa");
        attrs.put("email_verified", false);

        assertThat(DatosOAuth.desde(Proveedor.GOOGLE, attrs).emailVerificado()).isFalse();
    }

    @Test
    @DisplayName("Google: si falta given_name se parte el nombre completo")
    void googleSoloNombreCompleto() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("email", "luis@ejemplo.com");
        attrs.put("name", "Luis Gómez Torres");
        attrs.put("email_verified", "true");

        DatosOAuth datos = DatosOAuth.desde(Proveedor.GOOGLE, attrs);

        assertThat(datos.nombre()).isEqualTo("Luis");
        assertThat(datos.apellido()).isEqualTo("Gómez Torres");
        assertThat(datos.emailVerificado()).isTrue();
    }

    @Test
    @DisplayName("Facebook: parte el nombre y da por verificado el correo que entrega")
    void facebook() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("email", "marta@ejemplo.com");
        attrs.put("name", "Marta Ruiz");

        DatosOAuth datos = DatosOAuth.desde(Proveedor.FACEBOOK, attrs);

        assertThat(datos.email()).isEqualTo("marta@ejemplo.com");
        assertThat(datos.nombre()).isEqualTo("Marta");
        assertThat(datos.apellido()).isEqualTo("Ruiz");
        assertThat(datos.emailVerificado()).isTrue();
    }

    @Test
    @DisplayName("Facebook sin correo: no se considera verificado")
    void facebookSinCorreo() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("name", "Sin Correo");

        DatosOAuth datos = DatosOAuth.desde(Proveedor.FACEBOOK, attrs);

        assertThat(datos.email()).isNull();
        assertThat(datos.emailVerificado()).isFalse();
    }

    @Test
    @DisplayName("un nombre vacío no rompe el alta")
    void nombreAusente() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("email", "anon@ejemplo.com");

        DatosOAuth datos = DatosOAuth.desde(Proveedor.FACEBOOK, attrs);

        assertThat(datos.nombre()).isEqualTo("Usuario");
        assertThat(datos.apellido()).isEmpty();
    }
}
