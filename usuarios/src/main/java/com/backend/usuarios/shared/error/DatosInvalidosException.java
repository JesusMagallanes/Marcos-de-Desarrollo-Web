package com.backend.usuarios.shared.error;

/**
 * Los datos llegaron mal, pero no lo detecta una anotación de validación.
 *
 * <p>Es para las reglas que dependen de varios campos a la vez o del contenido
 * real de lo que llega: que el tipo de documento case con el tipo de persona, o
 * que un fichero que dice ser una imagen lo sea de verdad. Sin esto acabarían
 * lanzándose como conflicto (409) o cayendo en el manejador genérico (500), y
 * ninguno de los dos le dice al usuario que lo que tiene que hacer es corregir
 * el formulario.
 */
public class DatosInvalidosException extends RuntimeException {

    public DatosInvalidosException(String mensaje) {
        super(mensaje);
    }
}
