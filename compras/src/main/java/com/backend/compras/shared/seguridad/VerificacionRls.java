package com.backend.compras.shared.seguridad;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Comprueba al arrancar que Row Level Security está realmente en vigor.
 *
 * El motivo de existir de esta clase: RLS es un control que se desactiva EN
 * SILENCIO. Postgres ignora todas las políticas si el rol que se conecta es
 * superusuario o tiene el atributo BYPASSRLS, y no emite ningún aviso al
 * hacerlo. El resultado sería un sistema que parece protegido —las políticas
 * están ahí, se pueden leer en la migración— y que en realidad deja ver los
 * pedidos de todo el mundo.
 *
 * Falla ruidosamente en el log en lugar de tumbar el arranque: dejar la tienda
 * fuera de servicio por esto sería peor que servirla con un aviso muy visible
 * para quien opera el sistema.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VerificacionRls implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ContextoRls.comoSistema(this::verificar);
        } catch (Exception ex) {
            log.warn("No se pudo verificar el estado de Row Level Security: {}", ex.getMessage());
        }
    }

    private void verificar() {
        try (var conexion = dataSource.getConnection()) {

            try (var sentencia = conexion.prepareStatement(
                    "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user");
                    var fila = sentencia.executeQuery()) {

                if (fila.next() && fila.getBoolean(1)) {
                    log.error("""
                            RLS INACTIVO: el rol '{}' es superusuario o tiene BYPASSRLS, así que \
                            Postgres ignora TODAS las políticas de aislamiento por usuario. \
                            Crea un rol sin esos atributos para la aplicación.""",
                            conexion.getMetaData().getUserName());
                    return;
                }
            }

            try (var sentencia = conexion.prepareStatement(
                    """
                            SELECT count(*) FROM pg_tables
                            WHERE schemaname = 'compras'
                              AND tablename IN ('carrito', 'carrito_item', 'pedido', 'detalle_pedido',
                                                'envios', 'saga_checkout', 'clave_idempotencia')
                              AND rowsecurity""");
                    var fila = sentencia.executeQuery()) {

                int conRls = fila.next() ? fila.getInt(1) : 0;
                if (conRls < 7) {
                    log.error("Solo {} de 7 tablas con datos de usuario tienen RLS activo", conRls);
                    return;
                }
            }

            if (!contextoSobrevive(conexion)) {
                return;
            }

            log.info("Row Level Security activo en las 7 tablas con datos de usuario");

        } catch (Exception ex) {
            log.warn("No se pudo verificar el estado de Row Level Security: {}", ex.getMessage());
        }
    }

    /**
     * Comprueba que el contexto puesto en una sentencia sigue ahí en la
     * siguiente, sobre la misma conexión.
     *
     * Parece una perogrullada y no lo es: si la cadena de conexión apunta a un
     * pooler en modo *transaction pooling* (el endpoint `-pooler` de Neon,
     * PgBouncer con `pool_mode = transaction`), cada sentencia puede acabar en
     * un backend distinto y el `SET` de sesión se evapora. El resultado no es un
     * error claro sino algo peor: RLS funciona a ratos. Una petición crea el
     * carrito y la siguiente, idéntica, revienta con "new row violates
     * row-level security policy".
     *
     * Por eso no basta con mirar la configuración: hay que probar el mecanismo.
     */
    private boolean contextoSobrevive(java.sql.Connection conexion) throws java.sql.SQLException {
        String marca = "424242";

        try (var puesta = conexion.prepareStatement("SELECT set_config('app.usuario_id', ?, false)")) {
            puesta.setString(1, marca);
            puesta.execute();
        }

        String leido;
        try (var lectura = conexion.prepareStatement("SELECT current_setting('app.usuario_id', true)");
                var fila = lectura.executeQuery()) {
            leido = fila.next() ? fila.getString(1) : null;
        }

        // Se deja como estaba para no contaminar la conexión que vuelve al pool.
        try (var limpieza = conexion.prepareStatement("SELECT set_config('app.usuario_id', '', false)")) {
            limpieza.execute();
        }

        if (!marca.equals(leido)) {
            log.error("""
                    RLS NO FIABLE: el contexto de usuario no sobrevive entre sentencias \
                    (se escribió '{}' y se leyó '{}'). Casi siempre significa que DB_URL apunta \
                    a un pooler en modo transaction (el endpoint '-pooler' de Neon): cada \
                    sentencia cae en un backend distinto y las políticas ven un usuario vacío. \
                    Usa el endpoint directo, sin '-pooler'.""", marca, leido);
            return false;
        }
        return true;
    }
}
