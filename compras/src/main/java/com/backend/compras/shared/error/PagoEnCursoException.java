package com.backend.compras.shared.error;

/**
 * El pago existe y la pasarela todavía no ha decidido.
 *
 * <p>Es un {@link ConflictoException} —al comprador se le responde 409 igual—
 * pero se distingue por una razón concreta: <b>una compra con un pago en curso
 * no se compensa</b>. Cancelar el pedido y devolver el stock de un cobro que
 * puede aprobarse dentro de un rato deja el peor de los finales, dinero cobrado
 * sin pedido, y encima cierra la saga en un estado final del que ya no se sale.
 *
 * <p>Existe como tipo propio y no como un booleano porque quien decide
 * compensar está dos catch más abajo, y ahí solo llega la excepción.
 */
public class PagoEnCursoException extends ConflictoException {

    public PagoEnCursoException(String mensaje) {
        super(mensaje);
    }
}
