package com.backend.compras.shared.seguridad;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

/**
 * Fija el contexto que consumen las políticas de Row Level Security
 * (ver V4__row_level_security.sql) en cada conexión que sale del pool.
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
 *
 * <h4>La invariante: una conexión en el pool está siempre vacía</h4>
 *
 * <p>Con la base a ~130 ms de ida y vuelta, el `set_config` al coger la conexión
 * y el RESET al devolverla eran DOS viajes por transacción, más que la propia
 * consulta en la mayoría de lecturas del catálogo. Y casi todas esas lecturas
 * son anónimas: ni usuario, ni rol, ni `omitir_rls`. Para ellas el contexto
 * vacío es exactamente el que tiene una conexión recién creada o recién
 * limpiada, así que no hay nada que poner ni nada que quitar.
 *
 * <p>Eso solo es seguro si se cumple que TODA conexión que vuelve al pool está
 * vacía. Se cumple porque: (1) una petición anónima no toca el contexto; (2) una
 * con identidad lo pone al coger la conexión y lo limpia al devolverla; (3) si
 * esa limpieza falla, la conexión se EXPULSA del pool en vez de devolverse, de
 * modo que nadie hereda una identidad a medias. Con eso, una lectura pública
 * pasa de cuatro viajes a dos: la consulta y el COMMIT.
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
        Identidad identidad = identidadActual();
        if (identidad.vacia()) {
            // Anónimo: la conexión ya está vacía (invariante) y no hay que limpiar nada.
            return envolver(conexion, false);
        }
        try {
            aplicarContexto(conexion, identidad);
        } catch (SQLException fallo) {
            // Sin contexto no puede salir al pool con esta identidad a medias.
            expulsar(conexion);
            throw fallo;
        }
        return envolver(conexion, true);
    }

    /**
     * Saca la conexión del pool. Se usa cuando no se puede garantizar que esté
     * limpia: una conexión sucia en el pool es una identidad que hereda el
     * siguiente, y eso es peor que abrir otra.
     */
    private void expulsar(Connection conexion) {
        try {
            if (getTargetDataSource() instanceof HikariDataSource hikari) {
                hikari.evictConnection(conexion);
            } else {
                conexion.close();
            }
        } catch (RuntimeException | SQLException ex) {
            log.warn("No se pudo expulsar una conexión con contexto RLS a medias: {}", ex.getMessage());
        }
    }

    /**
     * `set_config(..., false)` es el equivalente a SET a nivel de sesión, pero en
     * forma de función, lo que permite pasar el valor como parámetro y no
     * concatenarlo: un id de usuario nunca se interpola en el SQL.
     */
    private void aplicarContexto(Connection conexion, Identidad identidad) throws SQLException {
        try (var sentencia = conexion.prepareStatement(
                "SELECT set_config('app.usuario_id', ?, false),"
                        + " set_config('app.rol', ?, false),"
                        + " set_config('app.permisos', ?, false),"
                        + " set_config('app.omitir_rls', ?, false)")) {

            sentencia.setString(1, identidad.usuarioId() == null ? "" : identidad.usuarioId().toString());
            sentencia.setString(2, identidad.rol() == null ? "" : identidad.rol());
            sentencia.setString(3, identidad.permisos());
            sentencia.setString(4, identidad.sistema() ? "on" : "off");
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
            return new Identidad(null, null, "", true);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return new Identidad(null, null, "", false);
        }

        Object uid = jwt.getClaim("uid");
        Long id = uid instanceof Number numero ? numero.longValue() : null;
        return new Identidad(id, jwt.getClaimAsString("rol"), permisos(jwt), false);
    }

    /** Los permisos del JWT, separados por coma; "" si el token no los trae. */
    private String permisos(Jwt jwt) {
        Object claim = jwt.getClaim("permisos");
        if (!(claim instanceof List<?> lista)) {
            return "";
        }
        return lista.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    /** Devuelve la conexión al pool sin rastro del usuario anterior. */
    private Connection envolver(Connection conexion, boolean conContexto) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                new LimpiezaAlCerrar(conexion, conContexto, this::expulsar));
    }

    private record Identidad(Long usuarioId, String rol, String permisos, boolean sistema) {

        /** Sin usuario, sin rol, sin permisos y sin `omitir_rls`: lo mismo que una conexión recién creada. */
        boolean vacia() {
            return usuarioId == null && (rol == null || rol.isEmpty()) && permisos.isEmpty() && !sistema;
        }
    }

    private static final class LimpiezaAlCerrar implements InvocationHandler {

        private final Connection real;
        private final boolean conContexto;
        private final Consumer<Connection> expulsar;

        private LimpiezaAlCerrar(Connection real, boolean conContexto, Consumer<Connection> expulsar) {
            this.real = real;
            this.conContexto = conContexto;
            this.expulsar = expulsar;
        }

        @Override
        public Object invoke(Object proxy, Method metodo, Object[] argumentos) throws Throwable {
            if ("close".equals(metodo.getName())) {
                if (!conContexto || limpiar()) {
                    real.close();
                } else {
                    expulsar.accept(real);
                }
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
         * @return true si la conexión quedó limpia (o ya estaba cerrada); false si
         *         la limpieza falló y no debe volver al pool tal cual
         */
        private boolean limpiar() {
            if (real == null) {
                return true;
            }
            try {
                if (real.isClosed()) {
                    return true;
                }
                try (Statement sentencia = real.createStatement()) {
                    sentencia.execute("SELECT set_config('app.usuario_id', '', false),"
                            + " set_config('app.rol', '', false),"
                            + " set_config('app.permisos', '', false),"
                            + " set_config('app.omitir_rls', 'off', false)");
                }
                return true;
            } catch (SQLException ex) {
                log.warn("No se pudo limpiar el contexto RLS de la conexión; se expulsa del pool: {}",
                        ex.getMessage());
                return false;
            }
        }
    }
}
