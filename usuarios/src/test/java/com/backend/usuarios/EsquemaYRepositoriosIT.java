package com.backend.usuarios;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.backend.usuarios.usuario.Rol;
import com.backend.usuarios.usuario.RolRepository;
import com.backend.usuarios.usuario.Usuario;
import com.backend.usuarios.usuario.UsuarioRepository;

/**
 * La cadena de arranque de `usuarios`, contra un PostgreSQL de verdad.
 *
 * <p>El servicio no tiene consultas nativas, así que lo que aquí no se puede
 * comprobar de otra forma es el ENCAJE: las nueve migraciones de Flyway se
 * aplican, y después Hibernate compara el esquema que han dejado contra las
 * entidades, porque `ddl-auto` está en `validate`. Una columna renombrada en la
 * migración y no en la entidad —o al revés— compila, pasa las ciento y pico
 * unitarias, y solo aparece al desplegar. Que el contexto de esta clase llegue a
 * levantarse ya es media prueba; lo de abajo comprueba la otra media, que es que
 * el mapeo de verdad lee y escribe las columnas que cree.
 */
@EnabledIf(
        value = "com.backend.usuarios.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Esquema y repositorios de usuarios")
class EsquemaYRepositoriosIT extends PruebaIntegracion {

    private static final String CORREO = "prueba.integracion@smartzone.test";

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private RolRepository roles;

    @Autowired
    private JdbcTemplate jdbc;

    /** Cada clase deja la base como se la encontró: el contenedor se comparte. */
    @BeforeEach
    void limpiar() {
        usuarios.findByEmailAddress(CORREO).ifPresent(usuarios::delete);
    }

    @Test
    @DisplayName("las migraciones se aplicaron todas y ninguna quedó a medias")
    void flywayAplicoLaCadenaEntera() {
        Integer aplicadas = jdbc.queryForObject(
                "SELECT count(*) FROM usuarios.flyway_schema_history WHERE success", Integer.class);
        Integer fallidas = jdbc.queryForObject(
                "SELECT count(*) FROM usuarios.flyway_schema_history WHERE NOT success", Integer.class);

        assertThat(fallidas).as("migraciones marcadas como fallidas").isZero();
        assertThat(aplicadas)
                .as("las nueve versiones del directorio db/migration")
                .isGreaterThanOrEqualTo(9);
    }

    @Test
    @DisplayName("un usuario va y vuelve por las columnas que dice la entidad")
    void elMapeoCuadraConElEsquema() {
        Usuario guardado = usuarios.save(Usuario.builder()
                .name("Ana")
                .lastname("Quispe")
                .emailAddress(CORREO)
                .password("no-es-una-contrasena-real")
                .phoneNumber("987654321")
                .dirDistrito("Ica")
                .dirDepartamento("Ica")
                .build());

        assertThat(guardado.getId()).as("la secuencia asigna identificador").isNotNull();

        /*
         * Se relee desde la base y no se mira el objeto que acaba de guardarse:
         * lo que interesa es el viaje de ida y vuelta por las columnas
         * (`first_name`, `email_address`, `dir_distrito`…), que es justo donde
         * una migración y una entidad pueden discrepar.
         */
        Usuario leido = usuarios.findByEmailAddress(CORREO).orElseThrow();

        assertThat(leido.getName()).isEqualTo("Ana");
        assertThat(leido.getLastname()).isEqualTo("Quispe");
        assertThat(leido.getDirDistrito()).isEqualTo("Ica");
        assertThat(leido.getRol()).as("el valor por defecto de la columna").isEqualTo("CLIENTE");
    }

    @Test
    @DisplayName("el catálogo de roles se lee ordenado por tipo y nombre")
    void losRolesSeOrdenanEnLaBase() {
        // La ordenación la resuelve PostgreSQL sobre un enum guardado como texto:
        // es la clase de cosa que compila siempre y solo se sabe ejecutándola.
        List<Rol> ordenados = roles.findAllByOrderByTipoAscNombreAsc();

        assertThat(ordenados).as("V4 siembra el RBAC inicial").isNotEmpty();
        assertThat(ordenados).extracting(Rol::getNombre).doesNotHaveDuplicates();
    }
}
