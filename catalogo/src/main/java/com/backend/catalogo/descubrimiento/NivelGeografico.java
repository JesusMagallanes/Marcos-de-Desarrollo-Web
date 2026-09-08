package com.backend.catalogo.descubrimiento;

/**
 * La escalera de degradación geográfica.
 *
 * <p>Una tendencia se sirve al nivel más fino que tenga sujetos suficientes.
 * En Ica el distrito se quedará corto a menudo al principio, y entonces se
 * sube un peldaño: es a la vez un control de privacidad —con cuatro personas
 * la «tendencia» delata a quien la generó— y de validez estadística.
 *
 * <p>Los prefijos son los del ubigeo del INEI que ya está en
 * {@code usuarios.ubigeo}: 110101 es el distrito de Ica, 1101 su provincia y
 * 11 el departamento.
 */
public enum NivelGeografico {

    DISTRITO(6),
    PROVINCIA(4),
    DEPARTAMENTO(2),
    NACIONAL(0);

    private final int digitos;

    NivelGeografico(int digitos) {
        this.digitos = digitos;
    }

    /** Cuántos dígitos del ubigeo identifican la zona en este nivel. */
    public int digitos() {
        return digitos;
    }

    /** La zona de este nivel para un ubigeo dado; cadena vacía en nacional. */
    public String zonaDe(String ubigeo) {
        if (ubigeo == null || ubigeo.length() < digitos) {
            return "";
        }
        return ubigeo.substring(0, digitos);
    }

    /** El siguiente peldaño hacia arriba, o {@code null} si ya es nacional. */
    public NivelGeografico siguiente() {
        return switch (this) {
            case DISTRITO -> PROVINCIA;
            case PROVINCIA -> DEPARTAMENTO;
            case DEPARTAMENTO -> NACIONAL;
            case NACIONAL -> null;
        };
    }
}
