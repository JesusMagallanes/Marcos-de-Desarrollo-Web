package com.backend.usuarios.shared.security;

/**
 * Identidad extraída del JWT. Se inyecta como argumento del controlador,
 * de modo que nunca se confía en un id que venga por la URL o el cuerpo.
 */
public record UsuarioAutenticado(Long id, String email, String rol) {

    public boolean esAdmin() {
        return "ADMINISTRADOR".equals(rol);
    }
}
