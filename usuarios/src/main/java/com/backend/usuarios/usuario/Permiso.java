package com.backend.usuarios.usuario;

/**
 * Catálogo de permisos del sistema. Cada permiso abre una operación de gestión
 * dentro de un módulo y se asigna a los roles desde el panel de administración.
 *
 * El código viaja dentro del JWT (claim `permisos`) y cada servicio lo traduce
 * a una autoridad {@code PERMISO_<CODIGO>} con la que se protegen los
 * controladores.
 */
public enum Permiso {

    PRODUCTOS_GESTIONAR("Gestionar productos", "Catálogo"),
    // Distinto de PRODUCTOS_GESTIONAR y no un caso suyo: aquel da el catálogo
    // ENTERO, incluidos los productos ajenos. Este alcanza solo a los del propio
    // usuario, y lo que publique pasa por moderación antes de verse. Es el que
    // lleva COLABORADOR; darle el otro sería abrirle la tienda de los demás.
    PRODUCTOS_PROPIOS("Publicar productos propios", "Catálogo"),
    DESCUENTOS_GESTIONAR("Gestionar descuentos", "Catálogo"),
    CATEGORIAS_GESTIONAR("Gestionar categorías", "Catálogo"),
    MARCAS_GESTIONAR("Gestionar marcas", "Catálogo"),

    USUARIOS_GESTIONAR("Gestionar usuarios", "Usuarios"),
    ROLES_GESTIONAR("Gestionar roles y permisos", "Usuarios"),

    VALORACIONES_GESTIONAR("Moderar valoraciones", "Comunicación"),
    GUIAS_GESTIONAR("Gestionar guías de ayuda", "Comunicación"),

    METODOS_PAGO_GESTIONAR("Gestionar métodos de pago", "Compras"),
    PEDIDOS_GESTIONAR("Gestionar pedidos", "Compras"),
    ENVIOS_GESTIONAR("Gestionar envíos", "Compras");

    private final String descripcion;
    private final String modulo;

    Permiso(String descripcion, String modulo) {
        this.descripcion = descripcion;
        this.modulo = modulo;
    }

    public String descripcion() {
        return descripcion;
    }

    /** Agrupa los permisos por módulo en el panel de administración. */
    public String modulo() {
        return modulo;
    }
}
