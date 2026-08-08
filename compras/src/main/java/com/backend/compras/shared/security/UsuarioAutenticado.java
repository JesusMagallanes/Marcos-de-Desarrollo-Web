package com.backend.compras.shared.security;

/**
 * Identidad tomada del JWT. Compras nunca acepta un `usuarioId` por URL ni por
 * cuerpo: en el monolito `/pedidos/usuario/{id}` dejaba ver los pedidos ajenos
 * con solo cambiar el número.
 */
public record UsuarioAutenticado(Long id, String email, String rol) {

    public boolean esAdmin() {
        return "ADMINISTRADOR".equals(rol);
    }

    public boolean esStaff() {
        return esAdmin() || "EMPLEADO".equals(rol);
    }
}
