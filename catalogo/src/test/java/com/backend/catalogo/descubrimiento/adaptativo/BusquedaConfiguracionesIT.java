package com.backend.catalogo.descubrimiento.adaptativo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.PruebaIntegracion;

/**
 * La búsqueda de calibración, y sobre todo que no se mida a sí misma.
 *
 * <p>El fallo que acecha aquí no produce un error: produce un número bonito.
 * Probar cuatro configuraciones sobre una ventana y quedarse con la mejor da un
 * resultado optimista aunque las cuatro sean idénticas, porque se está
 * informando del máximo de cuatro intentos y no de una medida. Por eso la
 * ventana con la que se ELIGE y la ventana con la que se INFORMA tienen que ser
 * distintas, y por eso esto se comprueba.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Búsqueda de configuraciones")
@Transactional
class BusquedaConfiguracionesIT extends PruebaIntegracion {

    private static final Duration ENTRENAMIENTO = Duration.ofDays(30);
    private static final Duration VALIDACION = Duration.ofDays(5);
    private static final Duration PRUEBA = Duration.ofDays(5);

    @Autowired
    private BusquedaConfiguraciones busqueda;

    @Autowired
    private JdbcTemplate jdbc;

    private Long categoria;
    private Instant corte;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM catalogo.evento_interaccion");
        jdbc.update("DELETE FROM catalogo.sujeto");
        categoria = crearCategoria("busqueda-it-" + UUID.randomUUID());
        corte = Instant.now().minus(12, ChronoUnit.DAYS);
    }

    @Test
    @DisplayName("se comparan varias candidatas y gana una")
    void seEligeUnaEntreVarias() {
        montarTrafico();

        BusquedaConfiguraciones.Resultado r = busqueda.buscar(
                corte, ENTRENAMIENTO, VALIDACION, PRUEBA);

        assertThat(r.enValidacion())
                .as("las cuatro hipótesis, no solo la ganadora")
                .hasSize(busqueda.candidatas().size());
        assertThat(r.elegida()).isNotNull();
        assertThat(r.enValidacion())
                .extracting(e -> e.configuracion())
                .contains(r.elegida().nombre());
    }

    @Test
    @DisplayName("la cifra final sale de datos que no eligieron nada")
    void laVentanaDePruebaEsPosteriorALaDeValidacion() {
        /*
         * El montaje separa a propósito lo que ocurre en cada ventana. Si la
         * medida final se calculara sobre la misma ventana con la que se eligió,
         * heredaría el sesgo de haber probado cuatro veces.
         */
        montarTrafico();

        BusquedaConfiguraciones.Resultado r = busqueda.buscar(
                corte, ENTRENAMIENTO, VALIDACION, PRUEBA);

        assertThat(r.enPrueba().configuracion())
                .as("se mide la elegida, no otra")
                .isEqualTo(r.elegida().nombre());
        assertThat(r.enPrueba().recall10()).isBetween(0.0, 1.0);
        assertThat(r.enPrueba().cobertura()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("las candidatas son pocas y todas tienen nombre")
    void elEspacioDeBusquedaEsPequeno() {
        /*
         * Con búsqueda masiva sobre el tráfico que hay hoy se encontraría el
         * ruido de la ventana, no una calibración mejor. Y un nombre por
         * candidata es lo que permite explicar después por qué ganó.
         */
        assertThat(busqueda.candidatas())
                .hasSizeBetween(2, 6)
                .allSatisfy(c -> assertThat(c.nombre()).isNotBlank())
                .extracting(c -> c.nombre())
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("sin tráfico no inventa una ganadora con cifras")
    void sinDatosNoSeInventaNada() {
        BusquedaConfiguraciones.Resultado r = busqueda.buscar(
                corte, ENTRENAMIENTO, VALIDACION, PRUEBA);

        assertThat(r.enPrueba().sujetosEvaluados())
                .as("cero sujetos: cualquier métrica de aquí sería una cifra falsa")
                .isZero();
        assertThat(r.enPrueba().ndcg10()).isZero();
    }

    /** Un poco de conducta a los dos lados del corte y dentro de las dos ventanas. */
    private void montarTrafico() {
        Long semilla = crearProducto("Semilla");
        Long destino = crearProducto("Destino");
        for (int i = 0; i < 6; i++) {
            UUID vecino = crearSujeto();
            ver(vecino, semilla, corte.minus(5, ChronoUnit.DAYS));
            ver(vecino, destino, corte.minus(5, ChronoUnit.DAYS));
        }
        for (int i = 0; i < 10; i++) {
            UUID s = crearSujeto();
            ver(s, semilla, corte.minus(3, ChronoUnit.DAYS));
            // Uno en la ventana de validacion y otro en la de prueba.
            ver(s, destino, corte.plus(1, ChronoUnit.DAYS));
            ver(s, crearProducto("Tardio " + i), corte.plus(VALIDACION).plus(1, ChronoUnit.DAYS));
        }
    }

    private Long crearCategoria(String slug) {
        jdbc.update("INSERT INTO catalogo.categoria (name, slug, description)"
                + " VALUES (?, ?, 'IT')", slug, slug);
        return jdbc.queryForObject("SELECT id FROM catalogo.categoria WHERE slug = ?",
                Long.class, slug);
    }

    private Long crearProducto(String nombre) {
        String unico = nombre + " " + UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.producto"
                + " (name, description, precio, stock, categoria_id, estado_moderacion)"
                + " VALUES (?, 'IT', ?, 10, ?, 'APROBADO')",
                unico, new BigDecimal("100.00"), categoria);
        return jdbc.queryForObject("SELECT id FROM catalogo.producto WHERE name = ?",
                Long.class, unico);
    }

    private UUID crearSujeto() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.sujeto (id) VALUES (?)", id);
        return id;
    }

    private void ver(UUID quien, Long producto, Instant cuando) {
        jdbc.update("INSERT INTO catalogo.evento_interaccion"
                + " (sujeto_id, tipo, item_tipo, item_id, ocurrido_en)"
                + " VALUES (?, 'ITEM_VIEW', 'PRODUCTO', ?, ?)",
                quien, producto, Timestamp.from(cuando));
    }
}
