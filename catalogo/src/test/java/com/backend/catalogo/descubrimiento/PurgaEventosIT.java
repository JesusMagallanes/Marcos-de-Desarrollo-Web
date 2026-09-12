package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.backend.catalogo.PruebaIntegracion;
import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * La retención de eventos, ejecutada por fin.
 *
 * <p>Lo que estas pruebas fijan no es que se borre —eso es lo fácil— sino las
 * tres cosas que hacen que borrar sea seguro: la frontera exacta, que repetirla
 * no haga nada nuevo, y que lo que Discovery aún necesita siga ahí después.
 *
 * <p>NO es transaccional: cada lote de la purga confirma la suya.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Purga de eventos")
class PurgaEventosIT extends PruebaIntegracion {

    @Autowired
    private PurgaEventosService purga;

    @Autowired
    private ColaborativoService colaborativo;

    @Autowired
    private PesosDescubrimiento pesos;

    @Autowired
    private MeterRegistry registro;

    @Autowired
    private JdbcTemplate jdbc;

    private Long categoria;
    private UUID sujeto;
    private Instant corte;
    private int loteOriginal;
    private int topeOriginal;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM catalogo.item_relacion");
        jdbc.update("DELETE FROM catalogo.evento_interaccion");
        jdbc.update("DELETE FROM catalogo.perfil_faceta");
        jdbc.update("DELETE FROM catalogo.sujeto");
        categoria = crearCategoria("purga-" + UUID.randomUUID());
        sujeto = crearSujeto();
        corte = Instant.now().minus(pesos.getRetencionDias(), ChronoUnit.DAYS);
        loteOriginal = pesos.getPurgaLote();
        topeOriginal = pesos.getPurgaMaximoPorEjecucion();
    }

    @AfterEach
    void devolverTodoComoEstaba() {
        pesos.setPurgaLote(loteOriginal);
        pesos.setPurgaMaximoPorEjecucion(topeOriginal);
        jdbc.update("DELETE FROM catalogo.item_relacion");
        jdbc.update("DELETE FROM catalogo.evento_interaccion WHERE item_id IN"
                + " (SELECT id FROM catalogo.producto WHERE categoria_id = ?)", categoria);
        jdbc.update("DELETE FROM catalogo.producto WHERE categoria_id = ?", categoria);
        jdbc.update("DELETE FROM catalogo.categoria WHERE id = ?", categoria);
    }

    /* ══════════════ La frontera ══════════════ */

    @Nested
    @DisplayName("Frontera de retención")
    class Frontera {

        @Test
        @DisplayName("lo de hace más de noventa días se va")
        void loVencidoSeVa() {
            Long producto = crearProducto("Antiguo");
            evento(producto, corte.minus(1, ChronoUnit.DAYS));

            PurgaEventosService.Resultado r = purga.purgar(corte);

            assertThat(r.purgadas()).isEqualTo(1);
            assertThat(eventos()).isZero();
        }

        @Test
        @DisplayName("lo de hace ochenta y nueve días se queda")
        void loVigenteSeQueda() {
            Long producto = crearProducto("Reciente");
            evento(producto, corte.plus(1, ChronoUnit.DAYS));

            purga.purgar(corte);

            assertThat(eventos()).isEqualTo(1);
        }

        @Test
        @DisplayName("la frontera exacta se queda: es estricta por abajo, como en servidas")
        void laFronteraExacta() {
            /*
             * `< corte` y no `<=`, que es el criterio que ya aplican
             * `recomendacion_servida` e `impresion`. Tres tablas de detalle con
             * la misma retencion tienen que cortar por el mismo sitio, o un
             * evento podria quedar sin la impresion que lo explica.
             */
            Long producto = crearProducto("En el borde");
            evento(producto, corte);
            evento(producto, corte.minusMillis(1));

            purga.purgar(corte);

            assertThat(eventos())
                    .as("el del instante exacto sobrevive; el de un milisegundo antes, no")
                    .isEqualTo(1);
        }
    }

    /* ══════════════ Repetición y lotes ══════════════ */

    @Nested
    @DisplayName("Repetición, lotes y tope")
    class RepeticionYLotes {

        @Test
        @DisplayName("repetirla no borra nada más")
        void esIdempotente() {
            Long producto = crearProducto("Antiguo");
            for (int i = 0; i < 5; i++) {
                evento(producto, corte.minus(i + 1, ChronoUnit.DAYS));
            }

            PurgaEventosService.Resultado primera = purga.purgar(corte);
            PurgaEventosService.Resultado segunda = purga.purgar(corte);

            assertThat(primera.purgadas()).isEqualTo(5);
            assertThat(segunda.purgadas())
                    .as("ya no queda nada vencido: exactamente cero")
                    .isZero();
            assertThat(eventos()).isZero();
        }

        @Test
        @DisplayName("borra en lotes, cada uno en su transacción")
        void porLotes() {
            pesos.setPurgaLote(2);
            Long producto = crearProducto("Antiguo");
            for (int i = 0; i < 7; i++) {
                evento(producto, corte.minus(i + 1, ChronoUnit.HOURS));
            }

            PurgaEventosService.Resultado r = purga.purgar(corte);

            assertThat(r.purgadas()).isEqualTo(7);
            assertThat(r.lotes())
                    .as("siete filas de dos en dos: tres lotes llenos y uno con la última")
                    .isEqualTo(4);
            assertThat(eventos()).isZero();
        }

        @Test
        @DisplayName("se detiene en el tope y la siguiente pasada continúa")
        void topePorEjecucion() {
            /*
             * La primera pasada tras desplegar la purga se encuentra con todo
             * el atraso desde la fase 1. El tope hace que no lo trague de una:
             * hace lo suyo, avisa de que queda, y la siguiente sigue.
             */
            pesos.setPurgaLote(3);
            pesos.setPurgaMaximoPorEjecucion(6);
            Long producto = crearProducto("Mucho atraso");
            for (int i = 0; i < 10; i++) {
                evento(producto, corte.minus(i + 1, ChronoUnit.HOURS));
            }

            PurgaEventosService.Resultado primera = purga.purgar(corte);

            assertThat(primera.purgadas())
                    .as("exactamente el tope, ni una más")
                    .isEqualTo(6);
            assertThat(primera.quedaPendiente()).isTrue();
            assertThat(eventos()).isEqualTo(4);

            PurgaEventosService.Resultado segunda = purga.purgar(corte);

            assertThat(segunda.purgadas()).isEqualTo(4);
            assertThat(segunda.quedaPendiente()).isFalse();
            assertThat(eventos()).isZero();
        }

        @Test
        @DisplayName("un tope que no divide al lote también se respeta")
        void elTopeRecortaElUltimoLote() {
            pesos.setPurgaLote(4);
            pesos.setPurgaMaximoPorEjecucion(5);
            Long producto = crearProducto("Antiguo");
            for (int i = 0; i < 9; i++) {
                evento(producto, corte.minus(i + 1, ChronoUnit.HOURS));
            }

            assertThat(purga.purgar(corte).purgadas())
                    .as("cuatro del primer lote y solo una del segundo")
                    .isEqualTo(5);
        }
    }

    /* ══════════════ Lo que Discovery aún necesita ══════════════ */

    @Nested
    @DisplayName("Lo necesario sobrevive")
    class LoNecesarioSobrevive {

        @Test
        @DisplayName("un evento de veintinueve días sigue alimentando al colaborativo")
        void elColaborativoNoPierdeNada() {
            /*
             * La prueba que justifica los noventa dias: el consumidor con la
             * ventana mas larga, colaborativo, mira treinta. Se construye una
             * co-visita con eventos de hace veintinueve dias, se purga, y la
             * relacion tiene que seguir saliendo del recalculo.
             */
            Long a = crearProducto("Webcam");
            Long b = crearProducto("Aro de luz");
            Instant hace29 = Instant.now().minus(29, ChronoUnit.DAYS);
            for (int i = 0; i < 4; i++) {
                UUID s = crearSujeto();
                eventoDe(s, a, hace29);
                eventoDe(s, b, hace29);
            }
            // Y un vencido de verdad, para que la purga tenga algo que hacer.
            evento(a, corte.minus(1, ChronoUnit.DAYS));

            PurgaEventosService.Resultado r = purga.purgar(corte);
            colaborativo.recalcular();

            assertThat(r.purgadas()).isEqualTo(1);
            assertThat(eventos()).isEqualTo(8);
            assertThat(relacionesEntre(a, b))
                    .as("la co-visita de hace veintinueve días sigue viva")
                    .isPositive();
        }
    }

    /* ══════════════ Observabilidad ══════════════ */

    @Nested
    @DisplayName("Observabilidad")
    class Observabilidad {

        @Test
        @DisplayName("lo purgado se cuenta por tabla, sin identificadores")
        void seCuentaSinDelatar() {
            Long producto = crearProducto("Antiguo");
            evento(producto, corte.minus(1, ChronoUnit.DAYS));
            evento(producto, corte.minus(2, ChronoUnit.DAYS));

            double antes = purgadasContadas();
            purga.purgar(corte);

            assertThat(purgadasContadas() - antes).isEqualTo(2.0);
            assertThat(registro.find(MetricasDescubrimiento.PURGADAS).counters())
                    .allSatisfy(c -> assertThat(c.getId().getTags())
                            .extracting(t -> t.getKey())
                            .containsOnly("tabla", "aplicacion"));
        }
    }

    /* ══════════════ Utilidades ══════════════ */

    private double purgadasContadas() {
        var contador = registro.find(MetricasDescubrimiento.PURGADAS)
                .tag("tabla", "evento_interaccion").counter();
        return contador == null ? 0.0 : contador.count();
    }

    private long eventos() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM catalogo.evento_interaccion",
                Long.class);
    }

    private long relacionesEntre(Long a, Long b) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM catalogo.item_relacion"
                + " WHERE (item_a = ? AND item_b = ?) OR (item_a = ? AND item_b = ?)",
                Long.class, a, b, b, a);
    }

    private void evento(Long producto, Instant cuando) {
        eventoDe(sujeto, producto, cuando);
    }

    private void eventoDe(UUID quien, Long producto, Instant cuando) {
        jdbc.update("INSERT INTO catalogo.evento_interaccion"
                + " (sujeto_id, tipo, item_tipo, item_id, ocurrido_en)"
                + " VALUES (?, 'ITEM_VIEW', 'PRODUCTO', ?, ?)",
                quien, producto, Timestamp.from(cuando));
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
}
