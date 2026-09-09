package com.backend.compras;

import org.testcontainers.DockerClientFactory;

/**
 * Comprobación de disponibilidad de Docker.
 *
 * Vive en su propia clase a propósito: si estuviera en {@link PruebaIntegracion},
 * evaluar la condición cargaría esa clase y arrancaría el contenedor antes de
 * poder decidir si hay que saltarse las pruebas.
 *
 * <h4>En un portátil se salta; en CI se cae</h4>
 *
 * <p>Saltarse las pruebas cuando no hay Docker es razonable en un equipo donde
 * no todo el mundo lo tiene instalado. En CI es lo contrario de razonable: la
 * ejecución se pone verde sin haber probado nada, y un verde no lo mira nadie.
 * Ya ha pasado: con Docker Engine 29 el sondeo empezó a fallar por la versión de
 * la API, y las pruebas de integración dejaron de ejecutarse sin que se notara.
 *
 * <p>Por eso, cuando la variable {@code CI} está puesta —GitHub Actions, GitLab
 * y los demás la ponen solos—, no hay salto que valga: si Docker no responde se
 * levanta una excepción y la ejecución se cae diciendo por qué. Sin variable
 * nueva que recordar y sin tocar el flujo de trabajo.
 */
public final class Docker {

    private Docker() {
    }

    public static boolean disponible() {
        Throwable fallo = null;
        boolean hay = false;
        try {
            hay = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            // Cualquier fallo al sondear (pipe equivocado, permisos, daemon
            // parado) significa lo mismo: no se puede levantar el contenedor.
            fallo = ex;
        }

        if (!hay && enIntegracionContinua()) {
            throw new IllegalStateException(
                    "Docker no responde y esto es CI: las pruebas de integracion NO pueden"
                            + " saltarse aqui. El log de Testcontainers, justo encima de esta"
                            + " linea, dice que estrategias probo y con que error fallo cada una.",
                    fallo);
        }
        return hay;
    }

    /** La ponen GitHub Actions, GitLab CI, CircleCI y Travis por su cuenta. */
    private static boolean enIntegracionContinua() {
        return Boolean.parseBoolean(System.getenv("CI"));
    }
}
