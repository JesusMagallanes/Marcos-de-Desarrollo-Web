package com.backend.usuarios.shared.security;

import com.backend.usuarios.usuario.Rol;

/**
 * Identidad extraída del JWT. Se inyecta como argumento del controlador,
 * de modo que nunca se confía en un id que venga por la URL o el cuerpo.
 */
public record UsuarioAutenticado(Long id, String email, Rol rol) {

    public boolean esAdmin() {
        return rol == Rol.ADMINISTRADOR;
    }
}
