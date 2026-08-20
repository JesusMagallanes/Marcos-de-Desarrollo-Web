package com.backend.compras.envio;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cuánto cuesta llevar un pedido.
 *
 * <p>Existía la configuración —{@code compras.envio.umbral-gratis} y
 * {@code compras.envio.costo}— pero no la leía ningún java: el backend cobraba
 * el subtotal pelado mientras el carrito enseñaba «Total» con los 15 soles
 * sumados. El comprador veía 215 y se le cobraban 200, y el envío no se cobró
 * nunca.
 *
 * <p>La regla vive aquí, en un solo sitio, y de aquí sale tanto el importe que
 * se le cobra a la pasarela como el que se le enseña al comprador: si los dos
 * números salen del mismo cálculo no pueden volver a contradecirse.
 */
@Component
public class TarifaEnvio {

    private final BigDecimal umbralGratis;
    private final BigDecimal costo;

    public TarifaEnvio(@Value("${compras.envio.umbral-gratis}") BigDecimal umbralGratis,
            @Value("${compras.envio.costo}") BigDecimal costo) {
        this.umbralGratis = umbralGratis;
        this.costo = costo;
    }

    /**
     * Lo que se suma al subtotal.
     *
     * <p>Con el carrito vacío es cero y no el costo: cobrar el envío de una
     * compra que no existe es lo que hacía que un carrito recién vaciado
     * enseñara 15 soles a pagar.
     */
    public BigDecimal para(BigDecimal subtotal) {
        if (subtotal == null || subtotal.signum() <= 0 || subtotal.compareTo(umbralGratis) >= 0) {
            return BigDecimal.ZERO;
        }
        return costo;
    }

    /** El importe que se cobra de verdad: subtotal más envío. */
    public BigDecimal total(BigDecimal subtotal) {
        BigDecimal base = subtotal == null ? BigDecimal.ZERO : subtotal;
        return base.add(para(base));
    }

    public BigDecimal umbralGratis() {
        return umbralGratis;
    }

    public BigDecimal costo() {
        return costo;
    }
}
