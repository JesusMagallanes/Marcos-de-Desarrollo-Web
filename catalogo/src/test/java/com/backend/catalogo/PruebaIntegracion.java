package com.backend.catalogo;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base de las pruebas de integración: levanta un PostgreSQL real en contenedor.
 *
 * Verifica lo que las unitarias no alcanzan. Aquí eso es concreto: las consultas
 * del panel de descuentos llevan filtros opcionales (`:param IS NULL OR …`),
 * comparaciones de un parámetro contra literales y agregados con `CASE WHEN`.
 * Todo eso es una cadena de texto hasta que Hibernate la traduce, así que
 * compila igual esté bien o mal; la única forma de saberlo es ejecutarla.
 *
 * Si no hay Docker, las pruebas se SALTAN en vez de fallar: en un equipo no
 * todos lo tienen instalado y un build roto por eso solo enseña a ignorarlo.
 *
 *   mvn test     → solo unitarias (*Test), sin Docker
 *   mvn verify   → añade las de integración (*IT)
 *
 * OJO: cada clase concreta debe repetir la anotación
 * {@code @EnabledIf("com.backend.catalogo.Docker#disponible")}. JUnit 5 no
 * hereda las condiciones declaradas en una superclase, así que ponerla solo
 * aquí no evitaría que el contenedor intentara arrancar.
 *
 * <h4>Por qué el {@code properties} de abajo declara variables que no se usan</h4>
 *
 * <p>Por dos razones distintas.
 *
 * <p>La primera: son las que exige {@code ValidacionArranque}. La conexión de
 * verdad la fija {@code @DynamicPropertySource} con los datos del contenedor,
 * pero estas tienen que EXISTIR igualmente, porque esa validación se engancha a
 * {@code ApplicationEnvironmentPrepared} —antes de que haya contexto— y busca
 * {@code DB_URL}, {@code DB_USER}, {@code DB_PASSWORD} y {@code JWT_SECRET} por
 * su nombre de variable de entorno. Si falta una, corta el arranque y todas las
 * pruebas fallan con «Failed to load ApplicationContext», que no dice nada del
 * motivo real. En un portátil no se notaba, porque el {@code .env} del
 * repositorio las trae; en CI no hay {@code .env} y fallaban todas de golpe.
 *
 * <p>La segunda: neutralizan ese mismo {@code .env}. {@code application.properties}
 * lo importa para el desarrollo diario, y el import va DENTRO del documento, así
 * que no se puede cancelar desde fuera —ponerlo a vacío aquí no sirve de nada,
 * está comprobado—. El fichero entra en el contexto de prueba con la cadena de
 * la base real y los secretos de las pasarelas. Lo único que tiene precedencia
 * sobre él es declarar los valores aquí.
 *
 * <p>Van aquí y no en el flujo de trabajo para que la prueba se baste sola:
 * corre igual en CI, en un portátil sin {@code .env} y en uno que lo tenga.
 */
@SpringBootTest(properties = {
        "DB_URL=jdbc:postgresql://localhost:5432/no-se-usa",
        "DB_USER=prueba",
        "DB_PASSWORD=prueba",
        "JWT_SECRET=secreto-de-pruebas-suficientemente-largo-para-hs256",
})
@Tag("integracion")
public abstract class PruebaIntegracion {

    /**
     * UN contenedor para toda la ejecución, y que no lo pare nadie.
     *
     * <p>Antes lo gestionaba la extensión de Testcontainers con {@code @Container},
     * y eso lo paraba al terminar CADA clase de prueba y lo volvía a arrancar en
     * la siguiente, con un puerto nuevo. Spring, en cambio, cachea el contexto y
     * lo reutiliza entre clases con la misma configuración: el pool de conexiones
     * seguía apuntando al puerto del contenedor anterior, ya muerto, y la segunda
     * clase en adelante fallaba con «Connection to localhost:32769 refused»
     * mientras el log decía que el contenedor estaba arriba en otro puerto.
     *
     * <p>Se arranca a mano en {@link #propiedades} —{@code start()} es idempotente—
     * y no aquí en un bloque estático, para que siga ocurriendo DESPUÉS de que
     * {@code @EnabledIf} haya decidido si hay Docker. Lo para el reaper de
     * Testcontainers al terminar la JVM.
     */
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("smartzone_test")
            .withUsername("prueba")
            .withPassword("prueba");

    /**
     * Sustituye las variables que normalmente vienen del `.env`.
     * Sin esto el contexto fallaría al arrancar por falta de DB_URL.
     */
    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        // Idempotente: solo arranca la primera vez. Aquí y no en un bloque
        // estático para que ocurra después de comprobar que hay Docker.
        POSTGRES.start();

        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);

        /*
         * Flyway migra con SU propio usuario, y hay que apuntarlo al contenedor.
         *
         * `spring.flyway.user` sale de `${DB_MIGRACION_USER:${DB_USER}}`, así
         * que en un portátil con `.env` se quedaba con las credenciales de la
         * base REAL de Neon e intentaba entrar con ellas al contenedor:
         * «password authentication failed for user "neondb_owner"». La prueba
         * no debe depender de lo que cada uno tenga en su `.env`.
         */
        registro.add("spring.flyway.user", POSTGRES::getUsername);
        registro.add("spring.flyway.password", POSTGRES::getPassword);

        // El secreto debe superar los 32 bytes o el arranque se detiene.
        registro.add("seguridad.jwt.secreto",
                () -> "secreto-de-pruebas-suficientemente-largo-para-hs256");
    }
}
