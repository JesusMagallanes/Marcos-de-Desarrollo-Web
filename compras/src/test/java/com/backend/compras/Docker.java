package com.backend.compras;

import org.testcontainers.DockerClientFactory;

/**
 * Comprobación de disponibilidad de Docker.
 *
 * Vive en su propia clase a propósito: si estuviera en {@link PruebaIntegracion},
 * evaluar la condición cargaría esa clase y arrancaría el contenedor antes de
 * poder decidir si hay que saltarse las pruebas.
 */
public final class Docker {

    private Docker() {
    }

    public static boolean disponible() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            // Cualquier fallo al sondear (pipe equivocado, permisos, daemon
            // parado) significa lo mismo: no se puede levantar el contenedor.
            return false;
        }
    }
}
