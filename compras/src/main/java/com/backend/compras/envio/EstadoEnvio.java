package com.backend.compras.envio;

public enum EstadoEnvio {
    PENDIENTE,
    EN_TRANSITO,
    ENTREGADO;

    /** Transiciones permitidas; evita saltar de ENTREGADO a PENDIENTE. */
    public boolean puedePasarA(EstadoEnvio siguiente) {
        return switch (this) {
            case PENDIENTE -> siguiente == EN_TRANSITO;
            case EN_TRANSITO -> siguiente == ENTREGADO;
            case ENTREGADO -> false;
        };
    }
}
