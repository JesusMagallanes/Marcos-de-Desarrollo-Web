package com.backend.usuarios.shared.error;

/** Se traduce a 409. El frontend lo usa para "ese correo ya está registrado". */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
