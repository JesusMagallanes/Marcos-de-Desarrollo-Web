package com.backend.usuarios.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El monolito daba la misma contraseña ("Aa@12345") a todas las cuentas
 * sociales, así que bastaba conocer un correo para entrar por el formulario.
 */
class UsuarioTest {

    @Test
    @DisplayName("una cuenta local con contraseña sí puede usar el formulario")
    void cuentaLocal() {
        Usuario usuario = Usuario.builder()
                .emailAddress("local@ejemplo.com")
                .password("$2a$12$hashficticio")
                .proveedor(Proveedor.LOCAL)
                .build();

        assertThat(usuario.puedeIniciarSesionConPassword()).isTrue();
    }

    @Test
    @DisplayName("una cuenta de Google no puede entrar por el formulario")
    void cuentaGoogle() {
        Usuario usuario = Usuario.builder()
                .emailAddress("google@ejemplo.com")
                .password(null)
                .proveedor(Proveedor.GOOGLE)
                .build();

        assertThat(usuario.puedeIniciarSesionConPassword()).isFalse();
    }

    @Test
    @DisplayName("aunque una cuenta social tuviera hash, se sigue rechazando")
    void cuentaSocialConHashResidual() {
        Usuario usuario = Usuario.builder()
                .emailAddress("facebook@ejemplo.com")
                .password("$2a$12$hashquenodeberiaestar")
                .proveedor(Proveedor.FACEBOOK)
                .build();

        assertThat(usuario.puedeIniciarSesionConPassword()).isFalse();
    }

    @Test
    @DisplayName("una cuenta local sin contraseña tampoco entra")
    void localSinPassword() {
        Usuario usuario = Usuario.builder()
                .emailAddress("rota@ejemplo.com")
                .password(null)
                .proveedor(Proveedor.LOCAL)
                .build();

        assertThat(usuario.puedeIniciarSesionConPassword()).isFalse();
    }

    @Test
    @DisplayName("solo LOCAL admite login con contraseña")
    void permisosPorProveedor() {
        assertThat(Proveedor.LOCAL.permiteLoginConPassword()).isTrue();
        assertThat(Proveedor.GOOGLE.permiteLoginConPassword()).isFalse();
        assertThat(Proveedor.FACEBOOK.permiteLoginConPassword()).isFalse();
    }
}
