package com.backend.compras.pedido;

import java.util.Set;

public enum EstadoPedido {
    PENDIENTE,
    /**
     * Pedido en firme que todavía no se ha cobrado: el pago contra entrega.
     *
     * <p>El stock ya salió del inventario y hay que llevarlo, pero el dinero
     * llega al final, con el repartidor. Ninguno de los otros estados servía:
     * PENDIENTE es un checkout abandonado —y no sale en «Mis compras», así que
     * el comprador no vería el pedido que acaba de hacer— y PAGADO sería
     * mentira, además de habilitar la valoración de algo ni recibido ni pagado.
     */
    CONFIRMADO,
    PAGADO,
    EN_TRANSITO,
    ENTREGADO,
    CANCELADO;

    /**
     * Los estados en los que el dinero YA se cobró: esto, y solo esto, da
     * derecho a valorar el producto.
     *
     * <p>CONFIRMADO se queda fuera a propósito: es un contra entrega que aún no
     * ha salido, así que no se ha pagado ni se ha recibido nada de lo que
     * opinar. Cuando el pedido avanza a EN_TRANSITO ya entra, igual que un
     * prepagado.
     */
    public static final Set<EstadoPedido> COMPRADOS = Set.of(PAGADO, EN_TRANSITO, ENTREGADO);

    /**
     * Lo que el comprador ve en «Mis compras»: sus pedidos de verdad.
     *
     * <p>Es un conjunto aparte de {@link #COMPRADOS} porque las dos preguntas
     * dejaron de tener la misma respuesta cuando apareció el contra entrega:
     * ese pedido existe y hay que enseñárselo desde el primer momento, pero no
     * da derecho a valorar hasta que se cobre. Antes eran la misma lista y
     * juntarlas otra vez significaría, o esconderle al comprador un pedido que
     * acaba de hacer, o dejarle puntuar algo que no ha pagado.
     *
     * <p>Un PENDIENTE sigue fuera —es un checkout abandonado— y un CANCELADO
     * también.
     */
    public static final Set<EstadoPedido> EN_MIS_COMPRAS =
            Set.of(CONFIRMADO, PAGADO, EN_TRANSITO, ENTREGADO);

    /** Transiciones permitidas; evita saltar de ENTREGADO a PENDIENTE. */
    public boolean puedePasarA(EstadoPedido siguiente) {
        return switch (this) {
            // CONFIRMADO es la salida del checkout contra entrega, que crea el
            // pedido PENDIENTE como cualquier otro y lo cierra acto seguido.
            case PENDIENTE -> siguiente == PAGADO || siguiente == CONFIRMADO || siguiente == CANCELADO;
            // El contra entrega no pasa por PAGADO: se cobra al entregar.
            case CONFIRMADO -> siguiente == EN_TRANSITO || siguiente == CANCELADO;
            case PAGADO -> siguiente == EN_TRANSITO || siguiente == CANCELADO;
            case EN_TRANSITO -> siguiente == ENTREGADO || siguiente == CANCELADO;
            case ENTREGADO, CANCELADO -> false;
        };
    }
}
