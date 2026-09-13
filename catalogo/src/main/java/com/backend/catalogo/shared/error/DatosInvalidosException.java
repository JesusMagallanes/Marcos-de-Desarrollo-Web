package com.backend.catalogo.shared.error;

/**
 * Una regla de negocio que no cabe en una anotación de Bean Validation: el
 * archivo no es lo que dice ser, está vacío, pasa del tamaño…
 *
 * <p>Sale como 400 con el mensaje tal cual: los textos los redacta el código,
 * nunca salen de la entrada del usuario, así que se pueden enseñar.
 */
public class DatosInvalidosException extends RuntimeException {

    public DatosInvalidosException(String mensaje) {
        super(mensaje);
    }
}
