package com.backend.compras.shared.error;

/**
 * El comprador ha abierto demasiados checkouts en poco rato.
 *
 * <p>Es distinto del cupo del filtro, y por eso existe: aquel cuenta por IP y
 * detrás del gateway <b>todos los compradores comparten una</b>. Con un tope de
 * veinte cada diez minutos, la tienda entera se quedaba sin poder pagar en
 * cuanto había algo de tráfico —y en Perú buena parte del móvil sale por CGNAT,
 * así que «una IP» puede ser un barrio—. Aquí se cuenta por usuario, que es lo
 * que de verdad se quiere limitar, y con una identidad ya verificada.
 */
public class DemasiadasPeticionesException extends RuntimeException {

    /** Segundos que el comprador tiene que esperar; viaja en `Retry-After`. */
    private final long segundosParaReintentar;

    public DemasiadasPeticionesException(String mensaje, long segundosParaReintentar) {
        super(mensaje);
        this.segundosParaReintentar = segundosParaReintentar;
    }

    public long segundosParaReintentar() {
        return segundosParaReintentar;
    }
}
