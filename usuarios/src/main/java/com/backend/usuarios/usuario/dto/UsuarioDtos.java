package com.backend.usuarios.usuario.dto;

import com.backend.usuarios.shared.validacion.Saneador;
import com.backend.usuarios.usuario.Proveedor;
import com.backend.usuarios.usuario.Rol;
import com.backend.usuarios.usuario.Usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs del recurso usuario. Records: sin setters, sin Lombok, inmutables. */
public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    public static final String PATRON_PASSWORD = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$";
    public static final String MENSAJE_PASSWORD = "La contraseña debe tener 8+ caracteres con mayúscula, minúscula, número y símbolo";

    /** Lo que se devuelve al cliente. Nunca incluye el hash de la contraseña. */
    public record UsuarioResponse(
            Long id,
            String name,
            String lastname,
            String emailAddress,
            String phoneNumber,
            String address,
            Rol rol,
            Proveedor proveedor) {

        public static UsuarioResponse desde(Usuario u) {
            return new UsuarioResponse(
                    u.getId(),
                    u.getName(),
                    u.getLastname(),
                    u.getEmailAddress(),
                    u.getPhoneNumber(),
                    u.getAddress(),
                    u.getRol(),
                    u.getProveedor());
        }
    }

    /** Tope de `address` en la tabla usuario; sin él, un texto largo era un 500. */
    public static final int MAX_DIRECCION = 200;

    /** Tope de `email_address` en la tabla usuario. */
    public static final int MAX_EMAIL = 100;

    /** Actualización de perfil. Deliberadamente NO trae `rol` ni `password`: */
    public record PerfilUpdate(
            @NotBlank @Size(min = 2, max = 50) String name,
            @NotBlank @Size(min = 2, max = 50) String lastname,
            @NotBlank @Email @Size(max = MAX_EMAIL) String emailAddress,
            @NotBlank @Pattern(regexp = "\\d{9}", message = "El teléfono debe tener 9 dígitos") String phoneNumber,
            @NotBlank @Size(max = MAX_DIRECCION) String address) {

        public PerfilUpdate {
            name = Saneador.texto(name);
            lastname = Saneador.texto(lastname);
            emailAddress = Saneador.email(emailAddress);
            phoneNumber = Saneador.texto(phoneNumber);
            address = Saneador.texto(address);
        }
    }

    /** Alta desde el panel admin: aquí sí se puede fijar el rol. */
    public record UsuarioCreate(
            @NotBlank @Size(min = 2, max = 50) String name,
            @NotBlank @Size(min = 2, max = 50) String lastname,
            @NotBlank @Email @Size(max = MAX_EMAIL) String emailAddress,
            @NotBlank @Pattern(regexp = PATRON_PASSWORD, message = MENSAJE_PASSWORD) String password,
            @NotBlank @Pattern(regexp = "\\d{9}", message = "El teléfono debe tener 9 dígitos") String phoneNumber,
            @NotBlank @Size(max = MAX_DIRECCION) String address,
            Rol rol) {

        /**
         * A03. Ojo con lo que NO se toca: `password` se deja exactamente como
         * llegó. Recortarlo o normalizarlo cambiaría el secreto que el usuario
         * escribió, y a partir de ahí no podría volver a entrar con la
         * contraseña que él cree tener. Los secretos se comparan byte a byte,
         * no se "limpian".
         */
        public UsuarioCreate {
            name = Saneador.texto(name);
            lastname = Saneador.texto(lastname);
            emailAddress = Saneador.email(emailAddress);
            phoneNumber = Saneador.texto(phoneNumber);
            address = Saneador.texto(address);
        }
    }

    /** Cambio de rol: endpoint aparte, solo ADMINISTRADOR. */
    public record CambioRol(@NotNull Rol rol) {
    }
}
