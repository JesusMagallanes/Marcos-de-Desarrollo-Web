package com.backend.usuarios.shared.seguridad;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import lombok.extern.slf4j.Slf4j;

/**
 * Fija el contexto que consumen las políticas de Row Level Security
 * (ver V7__row_level_security.sql) en cada conexión que sale del pool.
 *
 * Por qué aquí y no con `SET LOCAL` dentro de la transacción: `SET LOCAL` solo
 * surte efecto si ya hay una transacción abierta; fuera de ella es una operación
 * vacía que además avisa por el log. Una sola consulta que se ejecutara sin
 * transacción quedaría sin contexto y, con RLS activo, devolvería cero filas sin
 * error visible. Poniéndolo en el momento de coger la conexión, TODA sentencia
 * que viaje por ella lleva contexto, haya transacción o no.
 *
 * El contrapunto es que la variable vive en la sesión, y la sesión se reutiliza:
 * si no se limpiara al devolver la conexión al pool, la siguiente petición
 * heredaría la identidad de la anterior — exactamente el fallo que este código
 * intenta evitar. De ahí el proxy que hace RESET en `close()`.
 */
@Slf4j
public class DataSourceRls extends DelegatingDataSource {

    public DataSourceRls(DataSource delegado) {
        super(delegado);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return preparar(super.getConnection());
    }

    @Override
    public Connection getConnection(String usuario, String clave) throws SQLException {
        return preparar(super.getConnection(usuario, clave));
    }

    private Connection preparar(Connection conexion) throws SQLException {
        aplicarContexto(conexion);
        return envolver(conexion);
    }

    /**
     * `set_config(..., false)` es el equivalente a SET a nivel de sesión, pero en
     * forma de función, lo que permite pasar el valor como parámetro y no
     * concatenarlo: un id de usuario nunca se interpola en el SQL.
     */
    private void aplicarContexto(Connection conexion) throws SQLException {
        Identidad identidad = identidadActual();

        try (var sentencia = conexion.prepareStatement(
                "SELECT set_config('app.usuario_id', ?, false),"
                        + " set_config('app.rol', ?, false),"
                        + " set_config('app.omitir_rls', ?, false)")) {

            sentencia.setString(1, identidad.usuarioId() == null ? "" : identidad.usuarioId().toString());
            sentencia.setString(2, identidad.rol() == null ? "" : identidad.rol());
            sentencia.setString(3, identidad.sistema() ? "on" : "off");
            sentencia.execute();
        }
    }

    /**
     * Identidad del JWT. Si no hay autenticación NO se concede nada: una petición
     * anónima se queda con el contexto vacío y las políticas le devuelven cero
     * filas. El único camino a `omitir_rls` es ContextoRls.comoSistema().
     */
    private Identidad identidadActual() {
        if (ContextoRls.esSistema()) {
            return new Identidad(null, null, true);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return new Identidad(null, null, false);
        }

        Object uid = jwt.getClaim("uid");
        Long id = uid instanceof Number numero ? numero.longValue() : null;
        return new Identidad(id, jwt.getClaimAsString("rol"), false);
    }

    /** Devuelve la conexión al pool sin rastro del usuario anterior. */
    private Connection envolver(Connection conexion) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                new LimpiezaAlCerrar(conexion));
    }

    private record Identidad(Long usuarioId, String rol, boolean sistema) {
    }

    private static final class LimpiezaAlCerrar implements InvocationHandler {

        private final Connection real;

        private LimpiezaAlCerrar(Connection real) {
            this.real = real;
        }

        @Override
        public Object invoke(Object proxy, Method metodo, Object[] argumentos) throws Throwable {
            if ("close".equals(metodo.getName())) {
                limpiar();
                real.close();
                return null;
            }
            // equals/hashCode sobre el proxy deben mirar la conexión real.
            if ("equals".equals(metodo.getName())) {
                return proxy == argumentos[0];
            }
            if ("hashCode".equals(metodo.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("unwrap".equals(metodo.getName()) && argumentos != null && argumentos.length == 1
                    && argumentos[0] instanceof Class<?> tipo && tipo.isInstance(real)) {
                return real;
            }

            try {
                return metodo.invoke(real, argumentos);
            } catch (java.lang.reflect.InvocationTargetException ex) {
                throw ex.getCause();
            }
        }

        /**
         * Si la limpieza falla no se propaga el error: la conexión probablemente
         * ya esté rota y lo importante es que se cierre. Se registra para que no
         * pase inadvertido.
         */
        private void limpiar() {
            if (real == null) {
                return;
            }
            try {
                if (real.isClosed()) {
                    return;
                }
                try (Statement sentencia = real.createStatement()) {
                    sentencia.execute("SELECT set_config('app.usuario_id', '', false),"
                            + " set_config('app.rol', '', false),"
                            + " set_config('app.omitir_rls', 'off', false)");
                }
            } catch (SQLException ex) {
                log.warn("No se pudo limpiar el contexto RLS de la conexión: {}", ex.getMessage());
            }
        }
    }
}
