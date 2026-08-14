package com.backend.usuarios.auth.dto;

import com.backend.usuarios.shared.validacion.Saneador;
import com.backend.usuarios.usuario.dto.UsuarioDtos;
import com.backend.usuarios.usuario.dto.UsuarioDtos.UsuarioResponse;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = UsuarioDtos.MAX_EMAIL) String email,
            @NotBlank @Size(max = 200) String password) {

        /**
         * Se normaliza el correo (minúsculas, sin invisibles) porque es la clave
         * de búsqueda. La contraseña NO se toca: es un secreto que se compara
         * byte a byte, y recortarla cambiaría lo que el usuario escribió.
         *
         * El `@Size` del password no es cosmético: sin tope, un cuerpo con una
         * cadena de megas obliga a BCrypt a trabajar sobre ella y sale gratis
         * tumbar el servicio a base de logins fallidos.
         */
        public LoginRequest {
            email = Saneador.email(email);
        }
    }

    /**
     * Registro público: no acepta `rol`. Todo el que se registra por aquí es
     * CLIENTE; promover a EMPLEADO/ADMIN solo se puede desde el panel admin.
     */
    public record RegistroRequest(
            @NotBlank @Size(min = 2, max = 50) String name,
            @NotBlank @Size(min = 2, max = 50) String lastname,
            @NotBlank @Email @Size(max = UsuarioDtos.MAX_EMAIL) String emailAddress,
            @NotBlank @Size(max = 200)
            @Pattern(regexp = UsuarioDtos.PATRON_PASSWORD, message = UsuarioDtos.MENSAJE_PASSWORD) String password,
            @NotBlank @Pattern(regexp = "\\d{9}", message = "El teléfono debe tener 9 dígitos") String phoneNumber,
            @NotBlank @Size(max = UsuarioDtos.MAX_DIRECCION) String address) {

        /** Igual que en el alta de admin: todo se limpia salvo la contraseña. */
        public RegistroRequest {
            name = Saneador.texto(name);
            lastname = Saneador.texto(lastname);
            emailAddress = Saneador.email(emailAddress);
            phoneNumber = Saneador.texto(phoneNumber);
            address = Saneador.texto(address);
        }
    }

    /** El tope evita que un "refresh token" de megas llegue al parser de JWT. */
    public record RefreshRequest(@NotBlank @Size(max = 4000) String refreshToken) {

        public RefreshRequest {
            refreshToken = Saneador.texto(refreshToken);
        }
    }

    /** Forma que consume el AuthService de Angular. */
    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String rol,
            long expiraEn,
            UsuarioResponse usuario) {
    }
}
