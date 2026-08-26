package com.backend.catalogo;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
 */
@SpringBootTest
@Testcontainers
@Tag("integracion")
public abstract class PruebaIntegracion {

    /**
     * Lo gestiona la extensión de Testcontainers, que lo arranca en un callback
     * posterior a la evaluación de @EnabledIf. Es estático para compartirlo
     * entre todas las clases que hereden de aquí y arrancarlo una sola vez.
     */
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("smartzone_test")
            .withUsername("prueba")
            .withPassword("prueba")
            .withReuse(true);

    /**
     * Sustituye las variables que normalmente vienen del `.env`.
     * Sin esto el contexto fallaría al arrancar por falta de DB_URL.
     */
    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);

        // El secreto debe superar los 32 bytes o el arranque se detiene.
        registro.add("seguridad.jwt.secreto",
                () -> "secreto-de-pruebas-suficientemente-largo-para-hs256");
    }
}
