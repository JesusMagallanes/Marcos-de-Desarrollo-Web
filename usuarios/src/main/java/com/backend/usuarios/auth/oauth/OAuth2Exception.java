package com.backend.usuarios.auth.oauth;

/** Fallo del flujo OAuth que debe mostrarse al usuario en la pantalla de login. */
public class OAuth2Exception extends RuntimeException {

    public OAuth2Exception(String mensaje) {
        super(mensaje);
    }
}
