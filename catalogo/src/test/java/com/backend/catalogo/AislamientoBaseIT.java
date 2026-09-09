package com.backend.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * El seguro de que ninguna prueba toca algo que no sea suyo.
 *
 * <p>No es paranoia. `application.properties` importa el `.env` del repositorio
 * para el desarrollo diario, y ese fichero lleva la cadena de la base REAL
 * —Neon— y los secretos de las pasarelas. El import va dentro del documento y
 * no se puede cancelar desde fuera, asi que el `.env` ESTA en el contexto de
 * prueba: lo unico que separa una prueba de produccion son los valores que
 * `PruebaIntegracion` declara por encima. Esto comprueba que siguen ahi.
 *
 * <p>Ya avisó una vez de verdad: Flyway migraba el contenedor con el usuario de
 * Neon y fallaba con «password authentication failed for user "neondb_owner"».
 * Aquello dio la cara; una propiedad menos afortunada no la daria.
 *
 * <p>Ninguna comprobacion imprime el valor que mira. Un fallo de esta prueba
 * acabaria en el log de CI, y un log de CI es publico.
 */
@EnabledIf("com.backend.catalogo.Docker#disponible")
@DisplayName("Aislamiento del entorno de pruebas")
class AislamientoBaseIT extends PruebaIntegracion {

    @Autowired
    private DataSource fuente;

    @Test
    @DisplayName("se habla con el contenedor y con nadie mas")
    void laConexionEsLaDelContenedor() throws Exception {
        String url = fuente.getConnection().getMetaData().getURL();

        assertThat(url)
                .as("la URL que de verdad usa el pool")
                .isEqualTo(POSTGRES.getJdbcUrl())
                .contains("smartzone_test")
                .containsAnyOf("localhost", "127.0.0.1");

        assertThat(url)
                .as("nada que huela a la base gestionada")
                .doesNotContain("neon.tech")
                .doesNotContain("neondb");
    }
}
