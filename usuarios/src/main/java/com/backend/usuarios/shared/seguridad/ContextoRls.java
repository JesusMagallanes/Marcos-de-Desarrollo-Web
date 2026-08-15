package com.backend.usuarios.shared.seguridad;

import java.util.function.Supplier;

/**
 * Marca el hilo actual como "trabajo interno del sistema" para que pueda operar
 * sobre filas que no pertenecen a ningún usuario en curso.
 *
 * Hace falta porque hay trabajo legítimo sin usuario detrás. En este servicio
 * son dos cosas muy distintas:
 *
 * <ul>
 *   <li><b>Autenticar.</b> El login busca a alguien por su correo ANTES de que
 *       exista ninguna sesión; el refresco consulta la lista de tokens
 *       revocados sin JWT en contexto. Es sistema por definición: no se puede
 *       exigir identidad a la operación que precisamente sirve para
 *       establecerla.
 *   <li><b>Tareas de fondo.</b> El sembrador del administrador, la purga de
 *       tokens caducados y la de documentos de identidad.
 * </ul>
 *
 * <p>Con RLS activo y sin contexto, todo eso vería cero filas. Y ojo con la
 * forma de fallar: el login diría "credenciales incorrectas" a un usuario que
 * las escribió bien, y la comprobación de tokens revocados daría por bueno uno
 * revocado. La segunda falla ABRIENDO, que es la peor manera posible.
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

    /**
     * Igual, para bloques que devuelven algo.
     *
     * <p>Recibe un {@link Supplier} y no un {@code Callable} como la versión de
     * {@code compras}. El motivo es práctico: aquí se usa desde el controlador
     * de autenticación, y un {@code Callable} obligaría a declarar
     * {@code throws Exception} en métodos que no lanzan nada comprobado. No
     * pueden convivir los dos: para una lambda sin argumentos que devuelve
     * valor, el compilador no sabría cuál elegir.
     */
    public static <T> T comoSistema(Supplier<T> bloque) {
        boolean anterior = esSistema();
        SISTEMA.set(true);
        try {
            return bloque.get();
        } finally {
            SISTEMA.set(anterior);
        }
    }
}
