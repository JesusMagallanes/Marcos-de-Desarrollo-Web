package com.backend.compras.pedido;

public enum EstadoPedido {
    PENDIENTE,
    PAGADO,
    EN_TRANSITO,
    ENTREGADO,
    CANCELADO;

    /** Transiciones permitidas; evita saltar de ENTREGADO a PENDIENTE. */
    public boolean puedePasarA(EstadoPedido siguiente) {
        return switch (this) {
            case PENDIENTE -> siguiente == PAGADO || siguiente == CANCELADO;
            case PAGADO -> siguiente == EN_TRANSITO || siguiente == CANCELADO;
            case EN_TRANSITO -> siguiente == ENTREGADO || siguiente == CANCELADO;
            case ENTREGADO, CANCELADO -> false;
        };
    }
}
