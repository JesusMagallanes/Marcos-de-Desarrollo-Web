package com.backend.catalogo.shared.seguridad;

import java.util.concurrent.Callable;

/**
 * Marca el hilo actual como "trabajo interno del sistema" para que pueda operar
 * sobre filas que no pertenecen a ningún usuario en curso.
 *
 * Hace falta porque hay trabajo legítimo sin usuario detrás: el barrendero que
 * compensa sagas abandonadas, la purga de claves de idempotencia o cualquier
 * tarea programada. Con RLS activo y sin contexto, esas tareas verían cero filas
 * y fallarían en silencio, que es la peor forma de fallar.
 *
 * Es deliberadamente incómodo de usar: hay que envolver el bloque de forma
 * explícita. Un "bypass" cómodo acaba puesto por todas partes y entonces RLS
 * deja de proteger nada. Y nunca se activa por ausencia de autenticación: una
 * petición HTTP anónima NO es sistema, es una petición anónima, y debe ver cero
 * filas.
 */
public final class ContextoRls {

    private static final ThreadLocal<Boolean> SISTEMA = ThreadLocal.withInitial(() -> false);

    private ContextoRls() {
    }

    public static boolean esSistema() {
        return Boolean.TRUE.equals(SISTEMA.get());
    }

    /** Ejecuta el bloque con permiso para saltarse RLS. Siempre se limpia. */
    public static void comoSistema(Runnable bloque) {
        boolean anterior = esSistema();
        SISTEMA.set(true);
        try {
            bloque.run();
        } finally {
            SISTEMA.set(anterior);
        }
    }

    public static <T> T comoSistema(Callable<T> bloque) throws Exception {
        boolean anterior = esSistema();
        SISTEMA.set(true);
        try {
            return bloque.call();
        } finally {
            SISTEMA.set(anterior);
        }
    }
}
