package com.backend.compras.pedido;

import java.util.Set;

public enum EstadoPedido {
    PENDIENTE,
    PAGADO,
    EN_TRANSITO,
    ENTREGADO,
    CANCELADO;

    /**
     * Los estados en los que el dinero YA se cobró: esto, y solo esto, es una
     * compra.
     *
     * <p>Un PENDIENTE es un checkout empezado y no pagado —quien lo abandona no
     * ha comprado nada— y un CANCELADO tampoco, y además se le devolvió el
     * dinero. Ninguno de los dos tiene sitio en «Mis compras»: el comprador ve
     * pedidos de cosas que no tiene y no sabe si le han cobrado.
     *
     * <p>Está aquí y no repetido en cada consulta porque la misma regla decide
     * dos cosas distintas —qué sale en «Mis compras» y quién puede valorar un
     * producto— y si las dos listas se separan, alguien acaba pudiendo valorar
     * algo que no compró, o al revés.
     */
    public static final Set<EstadoPedido> COMPRADOS = Set.of(PAGADO, EN_TRANSITO, ENTREGADO);

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
