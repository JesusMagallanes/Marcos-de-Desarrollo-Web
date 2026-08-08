package com.backend.compras.saga;

import com.backend.compras.shared.error.ConflictoException;

/**
 * Referencia externa que viaja con el pago: {@code
 * sz-<usuarioId>-<metodoPagoId>-<marcaTiempo>}.
 */
public record Referencia(Long usuarioId, Long metodoPagoId, long marcaTiempo) {

    private static final String PREFIJO = "sz";

    public static Referencia crear(Long usuarioId, Long metodoPagoId) {
        return new Referencia(usuarioId, metodoPagoId, System.currentTimeMillis());
    }

    public String formatear() {
        return "%s-%d-%d-%d".formatted(PREFIJO, usuarioId, metodoPagoId, marcaTiempo);
    }

    public static Referencia parsear(String crudo) {
        if (crudo == null || crudo.isBlank()) {
            throw new ConflictoException("El pago no trae referencia; no se puede conciliar");
        }

        String[] partes = crudo.split("-");
        if (partes.length != 4 || !PREFIJO.equals(partes[0])) {
            throw new ConflictoException("Referencia de pago con formato inesperado: " + crudo);
        }

        try {
            return new Referencia(
                    Long.parseLong(partes[1]),
                    Long.parseLong(partes[2]),
                    Long.parseLong(partes[3]));
        } catch (NumberFormatException ex) {
            throw new ConflictoException("Referencia de pago inválida: " + crudo);
        }
    }
}
