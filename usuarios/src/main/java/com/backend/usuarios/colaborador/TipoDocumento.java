package com.backend.usuarios.colaborador;

import java.util.regex.Pattern;

/**
 * Documento con el que se identifica el solicitante.
 *
 * <p>Cada uno trae su patrón porque comprobarlos con una expresión común
 * («ocho u once dígitos») aceptaba cosas que no existen: un RUC que no empieza
 * por 10 ni por 20, por ejemplo, no lo emite nadie.
 */
public enum TipoDocumento {

    /** Documento nacional de identidad: 8 dígitos. */
    DNI(Pattern.compile("^\\d{8}$"),
            "El DNI son 8 dígitos"),

    /**
     * Carné de extranjería. Longitud variable y admite letras, así que el
     * patrón es más suelto a propósito: apretarlo de más deja fuera a gente con
     * un carné perfectamente válido.
     */
    CE(Pattern.compile("^[A-Z0-9]{9,12}$"),
            "El carné de extranjería son de 9 a 12 caracteres, letras mayúsculas o números"),

    /**
     * RUC: 11 dígitos que empiezan por 10 (persona con negocio) o 20 (empresa).
     * Los demás prefijos existen pero no corresponden a quien vende aquí.
     */
    RUC(Pattern.compile("^(10|20)\\d{9}$"),
            "El RUC son 11 dígitos y empieza por 10 o 20");

    private final Pattern patron;
    private final String mensaje;

    TipoDocumento(Pattern patron, String mensaje) {
        this.patron = patron;
        this.mensaje = mensaje;
    }

    public boolean valida(String numero) {
        return numero != null && patron.matcher(numero).matches();
    }

    /** Explica qué se esperaba, para poder señalar el campo en el formulario. */
    public String mensaje() {
        return mensaje;
    }
}
