package com.backend.usuarios.shared.seguridad;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Comprueba al arrancar que Row Level Security está realmente en vigor.
 *
 * <p>El motivo de existir de esta clase: RLS es un control que se desactiva EN
 * SILENCIO. Postgres ignora todas las políticas si el rol que se conecta es
 * superusuario o tiene el atributo BYPASSRLS, y no emite ningún aviso al
 * hacerlo. El resultado sería un sistema que parece protegido —las políticas
 * están ahí, se pueden leer en la migración— y que en realidad deja ver los
 * datos de todo el mundo.
 *
 * <p>En este servicio el daño sería mayor que en los otros: aquí viven las
 * fotos de los documentos de identidad.
 *
 * <p>Falla ruidosamente en el log en lugar de tumbar el arranque: dejar la
 * tienda sin registro ni login sería peor que servirla con un aviso muy visible
 * para quien opera el sistema.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VerificacionRls implements ApplicationRunner {

    /** Las que guardan datos de una persona concreta. */
    private static final String[] TABLAS = {
            "usuario", "empleado", "solicitud_colaborador", "documento_identidad", "token_revocado"
    };

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

            try (var sentencia = conexion.prepareStatement("""
                    SELECT count(*) FROM pg_tables
                    WHERE schemaname = 'usuarios'
                      AND tablename = ANY (?)
                      AND rowsecurity""")) {

                sentencia.setArray(1, conexion.createArrayOf("text", TABLAS));
                try (var fila = sentencia.executeQuery()) {
                    int conRls = fila.next() ? fila.getInt(1) : 0;
                    if (conRls < TABLAS.length) {
                        log.error("Solo {} de {} tablas con datos personales tienen RLS activo",
                                conRls, TABLAS.length);
                        return;
                    }
                }
            }

            if (!contextoSobrevive(conexion)) {
                return;
            }

            log.info("Row Level Security activo en las {} tablas con datos personales", TABLAS.length);

        } catch (Exception ex) {
            log.warn("No se pudo verificar el estado de Row Level Security: {}", ex.getMessage());
        }
    }

    /**
     * Comprueba que el contexto puesto en una sentencia sigue ahí en la
     * siguiente, sobre la misma conexión.
     *
     * <p>Parece una perogrullada y no lo es: si la cadena de conexión apunta a un
     * pooler en modo <i>transaction pooling</i> (el endpoint {@code -pooler} de
     * Neon, PgBouncer con {@code pool_mode = transaction}), cada sentencia puede
     * acabar en un backend distinto y el {@code SET} de sesión se evapora. El
     * resultado no es un error claro sino algo peor: RLS funciona a ratos.
     *
     * <p>Por eso no basta con mirar la configuración: hay que probar el mecanismo.
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
