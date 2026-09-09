package com.backend.compras;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

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
@EnabledIf("com.backend.compras.Docker#disponible")
@DisplayName("Aislamiento del entorno de pruebas")
class AislamientoBaseIT extends PruebaIntegracion {

    @Autowired
    private DataSource fuente;

    @Autowired
    private Environment entorno;

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

    @Test
    @DisplayName("no hay credenciales vivas de MercadoPago")
    void laPasarelaNoLlevaCredencialesDeVerdad() {
        /*
         * Un token de produccion empieza por APP_USR-. Se comprueba la FORMA, no
         * el valor, y se afirma sobre un booleano: si esto falla, el informe dice
         * «true» y no el token.
         */
        boolean pareceReal = entorno.getProperty("mercadopago.access-token", "")
                .startsWith("APP_USR-");

        assertThat(pareceReal)
                .as("credencial real de MercadoPago dentro del contexto de pruebas")
                .isFalse();

        /*
         * `PruebaIntegracion` fija este a un valor de pruebas conocido. Se
         * compara contra el, y otra vez sobre un booleano: si algun dia gana el
         * del `.env`, el informe dice «false», no el secreto.
         */
        boolean esElDePruebas = "secreto-webhook-de-pruebas"
                .equals(entorno.getProperty("mercadopago.webhook-secret"));

        assertThat(esElDePruebas)
                .as("el secreto de webhook es el de pruebas, no el que trae el .env")
                .isTrue();
    }
}
