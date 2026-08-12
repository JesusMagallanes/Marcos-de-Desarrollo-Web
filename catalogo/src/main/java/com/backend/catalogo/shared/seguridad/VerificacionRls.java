package com.backend.catalogo.shared.seguridad;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Comprueba al arrancar que Row Level Security está realmente en vigor sobre
 * `valoracion`.
 *
 * RLS es un control que se desactiva EN SILENCIO: Postgres ignora todas las
 * políticas si el rol que se conecta es superusuario o tiene BYPASSRLS, y no
 * avisa. Quedaría un sistema que parece protegido —las políticas están escritas
 * en la migración y se pueden leer— pero en el que cualquiera podría editar la
 * reseña de otro. Mejor un error muy visible en el log que una falsa sensación
 * de seguridad.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VerificacionRls implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        ContextoRls.comoSistema(this::verificar);
    }

    private void verificar() {
        try (var conexion = dataSource.getConnection()) {

            try (var sentencia = conexion.prepareStatement(
                    "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user");
                    var fila = sentencia.executeQuery()) {

                if (fila.next() && fila.getBoolean(1)) {
                    log.error("""
                            RLS INACTIVO: el rol de la aplicación es superusuario o tiene BYPASSRLS, \
                            así que Postgres ignora las políticas de `valoracion`. Usa un rol sin \
                            esos atributos.""");
                    return;
                }
            }

            try (var sentencia = conexion.prepareStatement(
                    "SELECT rowsecurity FROM pg_tables WHERE schemaname = 'catalogo' AND tablename = 'valoracion'");
                    var fila = sentencia.executeQuery()) {

                if (fila.next() && fila.getBoolean(1)) {
                    if (!contextoSobrevive(conexion)) {
                        return;
                    }
                    log.info("Row Level Security activo sobre catalogo.valoracion");
                } else {
                    log.error("Row Level Security NO está activo sobre catalogo.valoracion");
                }
            }

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
