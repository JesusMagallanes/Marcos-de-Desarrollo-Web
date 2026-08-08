package com.backend.usuarios.auth.oauth;

import java.util.Map;

import com.backend.usuarios.usuario.Proveedor;

/**
 * Normaliza los atributos de Google y Facebook, que tienen formas distintas.
 */
public record DatosOAuth(String email, String nombre, String apellido, boolean emailVerificado) {

    public static DatosOAuth desde(Proveedor proveedor, Map<String, Object> atributos) {
        return switch (proveedor) {
            case GOOGLE -> deGoogle(atributos);
            case FACEBOOK -> deFacebook(atributos);
            case LOCAL -> throw new IllegalArgumentException("LOCAL no es un proveedor OAuth");
        };
    }

    private static DatosOAuth deGoogle(Map<String, Object> a) {
        // Google entrega given_name/family_name ya separados y marca si el
        // correo está verificado.
        String email = texto(a.get("email"));
        String nombre = texto(a.get("given_name"));
        String apellido = texto(a.get("family_name"));

        if (nombre == null) {
            String[] partes = partirNombre(texto(a.get("name")));
            nombre = partes[0];
            apellido = apellido != null ? apellido : partes[1];
        }

        boolean verificado = Boolean.TRUE.equals(a.get("email_verified"))
                || "true".equals(texto(a.get("email_verified")));

        return new DatosOAuth(email, nombre, apellido, verificado);
    }

    private static DatosOAuth deFacebook(Map<String, Object> a) {
        String email = texto(a.get("email"));
        String[] partes = partirNombre(texto(a.get("name")));

        // Facebook no expone un equivalente a email_verified; el correo que
        // entrega es el de la cuenta, que ya validó el propio Facebook.
        return new DatosOAuth(email, partes[0], partes[1], email != null);
    }

    private static String[] partirNombre(String completo) {
        if (completo == null || completo.isBlank()) {
            return new String[] { "Usuario", "" };
        }
        String[] trozos = completo.trim().split("\\s+", 2);
        return new String[] { trozos[0], trozos.length > 1 ? trozos[1] : "" };
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }
}
