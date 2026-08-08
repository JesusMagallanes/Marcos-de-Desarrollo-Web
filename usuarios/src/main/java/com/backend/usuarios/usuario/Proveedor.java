package com.backend.usuarios.usuario;

/** Origen de la cuenta. Determina si el login por formulario está permitido. */
public enum Proveedor {

    /** Alta con correo y contraseña. */
    LOCAL,
    GOOGLE,
    FACEBOOK;

    public boolean permiteLoginConPassword() {
        return this == LOCAL;
    }

    public static Proveedor desdeRegistrationId(String registrationId) {
        return switch (registrationId == null ? "" : registrationId.toLowerCase()) {
            case "google" -> GOOGLE;
            case "facebook" -> FACEBOOK;
            default -> throw new IllegalArgumentException("Proveedor OAuth no soportado: " + registrationId);
        };
    }
}
