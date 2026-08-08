package com.backend.compras.shared.error;

/** Se traduce a 503: un servicio del que dependemos no respondió. */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
