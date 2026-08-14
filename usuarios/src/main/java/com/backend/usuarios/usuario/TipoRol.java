package com.backend.usuarios.usuario;

/**
 * Clasificación de los roles: separa el personal (trabajadores) de los
 * clientes. Los roles de tipo TRABAJADOR dan acceso al panel de gestión;
 * los de tipo CLIENTE, solo a la tienda.
 */
public enum TipoRol {
    TRABAJADOR,
    CLIENTE
}
