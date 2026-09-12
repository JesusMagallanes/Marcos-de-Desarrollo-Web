package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.backend.catalogo.PruebaIntegracion;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Que ejecutar un proceso dos veces —seguidas o a la vez— deje lo mismo que
 * ejecutarlo una.
 *
 * <p>Idempotencia y concurrencia son la misma pregunta con dos relojes. La
 * primera se contesta con un run, una foto, otro run y otra foto. La segunda,
 * con dos transacciones REALES disputándose el mismo cerrojo de PostgreSQL: no
 * dos llamadas seguidas disfrazadas de concurrencia.
 *
 * <p>NO es transaccional: los procesos abren las suyas y el cerrojo es de
 * transacción.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Procesos programados: idempotencia y concurrencia")
class ProcesosProgramadosIT extends PruebaIntegracion {

    @Autowired
    private ColaborativoService colaborativo;

    @Autowired
    private TendenciaService tendencias;

    @Autowired
    private MedicionService medicion;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Autowired
    private MeterRegistry registro;

    @Autowired
    private JdbcTemplate jdbc;

    private Long categoria;
    private final ExecutorService hilos = Executors.newFixedThreadPool(2);

    @BeforeEach
    void limpiar() {
        for (String tabla : List.of("recomendacion_servida", "impresion", "evento_interaccion",
                "item_relacion", "sujeto_similitud", "tendencia_item", "metrica_descubrimiento",
                "perfil_faceta", "sujeto")) {
            jdbc.update("DELETE FROM catalogo." + tabla);
        }
        categoria = crearCategoria("proc-" + UUID.randomUUID());
    }

    @AfterEach
    void devolverTodoComoEstaba() {
        hilos.shutdownNow();
        jdbc.update("DELETE FROM catalogo.recomendacion_servida");
        jdbc.update("DELETE FROM catalogo.evento_interaccion");
        jdbc.update("DELETE FROM catalogo.item_relacion");
        jdbc.update("DELETE FROM catalogo.tendencia_item");
        jdbc.update("DELETE FROM catalogo.producto WHERE categoria_id = ?", categoria);
        jdbc.update("DELETE FROM catalogo.categoria WHERE id = ?", categoria);
    }

    /* ══════════════ Idempotencia, por la puerta programada ══════════════ */

    @Nested
    @DisplayName("Idempotencia")
    class Idempotencia {

        @Test
        @DisplayName("colaborativo: dos pasadas dejan exactamente lo mismo")
        void colaborativoEsIdempotente() {
            montarCoVisita();

            colaborativo.programado();
            List<Map<String, Object>> primera = foto("item_relacion", "item_a, item_b");

            colaborativo.programado();
            List<Map<String, Object>> segunda = foto("item_relacion", "item_a, item_b");

            assertThat(primera).isNotEmpty();
            assertThat(segunda).as("ni una fila más, ni un score distinto").isEqualTo(primera);
        }

        @Test
        @DisplayName("tendencias: dos pasadas dejan exactamente lo mismo")
        void tendenciasEsIdempotente() {
            /*
             * Este era el unico de los tres sin prueba. Los otros dos la tenian
             * desde las fases 2 y 3; la ausencia de esta es lo que dejo pasar
             * el fallo de los dos relojes.
             */
            montarTendencia();

            tendencias.recalcular();
            List<Map<String, Object>> primera = foto("tendencia_item", "nivel, zona, item_id");

            tendencias.recalcular();
            List<Map<String, Object>> segunda = foto("tendencia_item", "nivel, zona, item_id");

            assertThat(primera).isNotEmpty();
            assertThat(segunda).isEqualTo(primera);
        }

        @Test
        @DisplayName("medición: dos pasadas dejan exactamente lo mismo")
        void medicionEsIdempotente() {
            Long producto = crearProducto("Servido");
            UUID visitante = crearSujeto();
            Instant hace = Instant.now().minus(2, ChronoUnit.HOURS);
            servir(visitante, producto, hace);
            mostrar(visitante, producto, hace.plusSeconds(60));

            medicion.programado();
            List<Map<String, Object>> primera = foto("metrica_descubrimiento",
                    "dia, modulo, razon, banda_posicion");

            medicion.programado();
            List<Map<String, Object>> segunda = foto("metrica_descubrimiento",
                    "dia, modulo, razon, banda_posicion");

            assertThat(primera).isNotEmpty();
            assertThat(segunda).isEqualTo(primera);
        }
    }

    /* ══════════════ Tendencias: un solo reloj ══════════════ */

    @Nested
    @DisplayName("Tendencias con un solo instante")
    class UnSoloReloj {

        @Test
        @DisplayName("todas las filas de una pasada llevan el mismo instante")
        void unInstantePorPasada() {
            /*
             * Antes el upsert estampaba `now()` de PostgreSQL —el inicio de la
             * transaccion, segun el reloj de la BASE— y la purga cortaba por
             * `Instant.now()` de la JVM. Dos relojes para decidir que sobrevive:
             * con el de la base atrasado, la pasada borraria lo que acababa de
             * escribir. Ahora el servicio manda UN instante y lo usa para las
             * dos cosas, como ya hacia el colaborativo.
             *
             * Lo que esta prueba puede demostrar: que la marca es unica y cae
             * entre dos lecturas del reloj de la JVM. Lo que NO puede: el
             * desfase en si, porque en Testcontainers la base y la JVM comparten
             * reloj. La correccion es por construccion; esto fija el invariante.
             */
            montarTendencia();

            Instant antes = Instant.now();
            tendencias.recalcular();
            Instant despues = Instant.now();

            List<Timestamp> marcas = jdbc.queryForList(
                    "SELECT DISTINCT calculado_en FROM catalogo.tendencia_item", Timestamp.class);

            assertThat(marcas).as("un solo instante para toda la pasada").hasSize(1);
            assertThat(marcas.get(0).toInstant())
                    .as("acotada por el reloj de la JVM que manda el servicio")
                    .isBetween(antes, despues);
        }

        @Test
        @DisplayName("la purga de obsoletas no se lleva lo que la misma pasada escribió")
        void laPasadaNoSePurgaASiMisma() {
            montarTendencia();

            tendencias.recalcular();
            long tras = jdbc.queryForObject("SELECT COUNT(*) FROM catalogo.tendencia_item",
                    Long.class);

            assertThat(tras)
                    .as("upsert y purga con el mismo corte: lo escrito sobrevive")
                    .isPositive();
        }
    }

    /* ══════════════ Concurrencia real ══════════════ */

    @Nested
    @DisplayName("Concurrencia")
    class Concurrencia {

        @Test
        @DisplayName("colaborativo: si otra transacción tiene el cerrojo, se salta y no trabaja")
        void colaborativoSeSaltaSiEstaTomado() throws Exception {
            montarCoVisita();
            double saltadasAntes = saltadas("colaborativo");

            conElCerrojoTomado("colaborativo", () -> colaborativo.programado());

            assertThat(saltadas("colaborativo") - saltadasAntes)
                    .as("la pasada se contó como saltada")
                    .isEqualTo(1.0);
            assertThat(filas("item_relacion"))
                    .as("y no tocó nada")
                    .isZero();

            // Soltado el cerrojo, la siguiente pasada trabaja con normalidad.
            colaborativo.programado();
            assertThat(filas("item_relacion")).isPositive();
        }

        @Test
        @DisplayName("tendencias: mismo comportamiento con su propio cerrojo")
        void tendenciasSeSaltaSiEstaTomado() throws Exception {
            montarTendencia();
            double saltadasAntes = saltadas("tendencias");

            conElCerrojoTomado("tendencias", () -> tendencias.recalcular());

            assertThat(saltadas("tendencias") - saltadasAntes).isEqualTo(1.0);
            assertThat(filas("tendencia_item")).isZero();

            tendencias.recalcular();
            assertThat(filas("tendencia_item")).isPositive();
        }

        @Test
        @DisplayName("medición: mismo comportamiento con su propio cerrojo")
        void medicionSeSaltaSiEstaTomado() throws Exception {
            Long producto = crearProducto("Servido");
            servir(crearSujeto(), producto, Instant.now().minus(1, ChronoUnit.HOURS));
            double saltadasAntes = saltadas("medicion");

            conElCerrojoTomado("medicion", () -> medicion.programado());

            assertThat(saltadas("medicion") - saltadasAntes).isEqualTo(1.0);
            assertThat(filas("metrica_descubrimiento")).isZero();

            medicion.programado();
            assertThat(filas("metrica_descubrimiento")).isPositive();
        }

        @Test
        @DisplayName("los cerrojos son por proceso: tendencias no espera al colaborativo")
        void losCerrojosNoSeEstorban() throws Exception {
            montarTendencia();
            double saltadasAntes = saltadas("tendencias");

            conElCerrojoTomado("colaborativo", () -> tendencias.recalcular());

            assertThat(saltadas("tendencias") - saltadasAntes)
                    .as("el cerrojo del colaborativo no le afecta")
                    .isZero();
            assertThat(filas("tendencia_item")).isPositive();
        }

        @Test
        @DisplayName("dos pasadas del colaborativo a la vez: una trabaja, el estado es el de una")
        void dosColaborativosALaVez() throws Exception {
            /*
             * Carrera de verdad: dos hilos arrancan contra una barrera y llaman
             * al MISMO proceso. Lo que se exige no depende de quien gane: el
             * estado final tiene que ser identico al de una sola pasada, y
             * entre las dos no puede haber mas de una que trabaje.
             */
            montarCoVisita();
            colaborativo.programado();
            List<Map<String, Object>> esperado = foto("item_relacion", "item_a, item_b");
            jdbc.update("DELETE FROM catalogo.item_relacion");
            double saltadasAntes = saltadas("colaborativo");

            CyclicBarrier salida = new CyclicBarrier(2);
            Future<?> a = hilos.submit(() -> { salida.await(); colaborativo.programado(); return null; });
            Future<?> b = hilos.submit(() -> { salida.await(); colaborativo.programado(); return null; });
            a.get(60, TimeUnit.SECONDS);
            b.get(60, TimeUnit.SECONDS);

            double saltadasEnLaCarrera = saltadas("colaborativo") - saltadasAntes;
            List<Map<String, Object>> real = foto("item_relacion", "item_a, item_b");

            assertThat(saltadasEnLaCarrera)
                    .as("como mucho una se salta; si no se solaparon, ninguna")
                    .isBetween(0.0, 1.0);
            assertThat(real)
                    .as("gane quien gane, el resultado es el de una sola pasada")
                    .isEqualTo(esperado);
        }
    }

    /* ══════════════ Observabilidad ══════════════ */

    @Nested
    @DisplayName("Observabilidad")
    class Observabilidad {

        @Test
        @DisplayName("las métricas de mantenimiento solo llevan el nombre del proceso")
        void etiquetasDeConjuntoCerrado() {
            montarTendencia();
            tendencias.recalcular();
            colaborativo.programado();
            medicion.programado();

            List<String> claves = registro.getMeters().stream()
                    .filter(m -> m.getId().getName().equals(MetricasDescubrimiento.MANTENIMIENTO)
                            || m.getId().getName().equals(MetricasDescubrimiento.SALTADAS))
                    .flatMap(m -> m.getId().getTags().stream())
                    .map(t -> t.getKey() + "=" + t.getValue())
                    .distinct()
                    .toList();

            assertThat(claves).isNotEmpty();
            assertThat(claves)
                    .as("proceso y la etiqueta comun del servicio; nada mas")
                    .allMatch(c -> c.startsWith("proceso=") || c.startsWith("aplicacion="));
            assertThat(claves)
                    .filteredOn(c -> c.startsWith("proceso="))
                    .allMatch(c -> List.of("proceso=colaborativo", "proceso=tendencias",
                            "proceso=medicion").contains(c));
        }
    }

    /* ══════════════ Utilidades ══════════════ */

    /**
     * Ejecuta {@code accion} mientras OTRA transacción, en otro hilo y con otra
     * conexión, tiene tomado el cerrojo de {@code proceso}.
     *
     * <p>Es concurrencia real a nivel de PostgreSQL: dos transacciones vivas a
     * la vez disputándose la misma clave. Se sincroniza con dos pestillos para
     * que la acción no arranque hasta que el cerrojo esté de verdad tomado, y
     * el otro hilo no lo suelte hasta que la acción haya terminado.
     */
    private void conElCerrojoTomado(String proceso, Runnable accion) throws Exception {
        CountDownLatch tomado = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);

        Future<Boolean> dueno = hilos.submit(() -> new TransactionTemplate(transacciones)
                .execute(estado -> {
                    Boolean ok = jdbc.queryForObject(
                            "SELECT pg_try_advisory_xact_lock(hashtext(?))", Boolean.class,
                            "smartzone:descubrimiento:" + proceso);
                    tomado.countDown();
                    try {
                        soltar.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return ok;
                }));

        assertThat(tomado.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            accion.run();
        } finally {
            soltar.countDown();
        }
        assertThat(dueno.get(30, TimeUnit.SECONDS))
                .as("el otro hilo sí tenía el cerrojo")
                .isTrue();
    }

    private double saltadas(String proceso) {
        var c = registro.find(MetricasDescubrimiento.SALTADAS).tag("proceso", proceso).counter();
        return c == null ? 0.0 : c.count();
    }

    private long filas(String tabla) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM catalogo." + tabla, Long.class);
    }

    /** Las filas de una tabla derivada, sin la marca de tiempo, para comparar pasadas. */
    private List<Map<String, Object>> foto(String tabla, String orden) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT * FROM catalogo." + tabla + " ORDER BY " + orden);
        filas.forEach(f -> f.remove("calculado_en"));
        return filas;
    }

    private void montarCoVisita() {
        Long a = crearProducto("Webcam");
        Long b = crearProducto("Aro de luz");
        Instant hace = Instant.now().minus(1, ChronoUnit.DAYS);
        for (int i = 0; i < 4; i++) {
            UUID s = crearSujeto();
            evento(s, a, hace);
            evento(s, b, hace);
        }
    }

    private void montarTendencia() {
        Long producto = crearProducto("De moda");
        Instant hace = Instant.now().minus(1, ChronoUnit.DAYS);
        for (int i = 0; i < 60; i++) {
            evento(crearSujeto(), producto, hace);
        }
    }

    private void evento(UUID quien, Long producto, Instant cuando) {
        jdbc.update("INSERT INTO catalogo.evento_interaccion"
                + " (sujeto_id, tipo, item_tipo, item_id, ubigeo, ocurrido_en)"
                + " VALUES (?, 'ITEM_VIEW', 'PRODUCTO', ?, '110101', ?)",
                quien, producto, Timestamp.from(cuando));
    }

    private void servir(UUID quien, Long producto, Instant cuando) {
        jdbc.update("INSERT INTO catalogo.recomendacion_servida"
                + " (sujeto_id, item_tipo, item_id, modulo, razon, posicion, score,"
                + " ranker_version, con_perfil, servido_en)"
                + " VALUES (?, 'PRODUCTO', ?, 'POPULARES', 'POPULAR', 0, 1.0,"
                + " 'prueba', false, ?)",
                quien, producto, Timestamp.from(cuando));
    }

    private void mostrar(UUID quien, Long producto, Instant cuando) {
        jdbc.update("INSERT INTO catalogo.impresion"
                + " (sujeto_id, item_tipo, item_id, modulo, posicion, con_clic, mostrado_en)"
                + " VALUES (?, 'PRODUCTO', ?, 'POPULARES', 0, false, ?)",
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

    @SuppressWarnings("unused")
    private LocalDate hoy() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
