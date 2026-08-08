package com.backend.compras.shared.resiliencia;

/** Se traduce a 503: se rechaza sin llamar porque el destino está caído. */
public class CircuitoAbiertoException extends RuntimeException {

    public CircuitoAbiertoException(String mensaje) {
        super(mensaje);
    }
}
