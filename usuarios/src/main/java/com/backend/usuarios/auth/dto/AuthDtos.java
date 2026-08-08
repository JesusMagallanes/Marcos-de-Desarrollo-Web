package com.backend.usuarios.auth.dto;

import com.backend.usuarios.usuario.Rol;
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
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    /**
     * Registro público: no acepta `rol`. Todo el que se registra por aquí es
     * CLIENTE; promover a EMPLEADO/ADMIN solo se puede desde el panel admin.
     */
    public record RegistroRequest(
            @NotBlank @Size(min = 2, max = 50) String name,
            @NotBlank @Size(min = 2, max = 50) String lastname,
            @NotBlank @Email String emailAddress,
            @NotBlank @Pattern(regexp = UsuarioDtos.PATRON_PASSWORD, message = UsuarioDtos.MENSAJE_PASSWORD) String password,
            @NotBlank @Pattern(regexp = "\\d{9}", message = "El teléfono debe tener 9 dígitos") String phoneNumber,
            @NotBlank String address) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /** Forma que consume el AuthService de Angular. */
    public record AuthResponse(
            String accessToken,
            String refreshToken,
            Rol rol,
            long expiraEn,
            UsuarioResponse usuario) {
    }
}
