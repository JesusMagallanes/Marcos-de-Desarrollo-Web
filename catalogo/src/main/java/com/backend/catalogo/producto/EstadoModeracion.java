package com.backend.catalogo.producto;

/**
 * Si un producto se puede enseñar en la tienda.
 *
 * <p>Lo que publica la tienda nace {@link #APROBADO}: no tendría sentido que el
 * administrador se aprobara a sí mismo. Lo que publica un colaborador nace
 * {@link #PENDIENTE} y vuelve a estarlo cada vez que lo edita, porque si no
 * bastaría con publicar algo inocuo, esperar el visto bueno y cambiarlo después.
 */
public enum EstadoModeracion {

    PENDIENTE,
    APROBADO,
    RECHAZADO;

    /** Lo único que ve quien entra en la tienda. */
    public boolean esVisibleAlPublico() {
        return this == APROBADO;
    }
}
