package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.PruebaIntegracion;
import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

/**
 * La capa colaborativa, contra PostgreSQL de verdad.
 *
 * <p>Todo lo que se comprueba aquí vive dentro de dos consultas nativas con
 * CTEs, funciones de ventana y {@code ON CONFLICT}. No hay forma de probarlo con
 * simulacros: el comportamiento —incluido el que NO se quiere, como que un
 * superventas se empareje con medio catálogo— es de la base de datos.
 *
 * <p>Los eventos se fabrican con fecha explícita, que es lo que permite empujar
 * evidencia fuera de la ventana sin esperar treinta días.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Descubrimiento colaborativo")
/*
 * Transaccional por dos motivos, y el segundo importa mas que el primero.
 *
 * El primero: `acumular` es una consulta `@Modifying` y exige transaccion
 * activa; en produccion la pone `IngestaService`, que es quien la llama.
 *
 * El segundo: el `@BeforeEach` de aqui BORRA los eventos y los sujetos, porque
 * estas consultas agregan sobre la tabla entera y cualquier resto ajeno
 * cambiaria los numeros. El contenedor es UNO para toda la ejecucion, asi que
 * sin la vuelta atras esos DELETE se llevarian por delante lo que otra clase
 * necesitara despues. Con ella, la limpieza solo existe dentro de la prueba.
 */
@Transactional
class ColaborativoIT extends PruebaIntegracion {

    @Autowired
    private ColaborativoService colaborativo;

    @Autowired
    private ItemRelacionRepository relaciones;

    @Autowired
    private SujetoSimilitudRepository similitudes;

    @Autowired
    private PerfilFacetaRepository perfiles;

    @Autowired
    private PesosDescubrimiento pesos;

    @Autowired
    private JdbcTemplate jdbc;

    private Long categoria;

    /**
     * Tierra quemada antes de cada prueba.
     *
     * <p>El contenedor es UNO para toda la ejecución y estas consultas agregan
     * sobre TODA la tabla de eventos: lo que dejara otra clase cambiaría los
     * números sin que se notara por qué.
     */
    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM catalogo.item_relacion");
        jdbc.update("DELETE FROM catalogo.sujeto_similitud");
        jdbc.update("DELETE FROM catalogo.evento_interaccion");
        jdbc.update("DELETE FROM catalogo.perfil_faceta");
        jdbc.update("DELETE FROM catalogo.sujeto");
        categoria = crearCategoria("colaborativo-it-" + UUID.randomUUID());
    }

    /* ══════════════ Ítem a ítem ══════════════ */

    @Test
    @DisplayName("varios sujetos que comparten dos productos crean la relación")
    void seCreaLaRelacionConSoporteSuficiente() {
        Long monitor = crearProducto("Monitor");
        Long teclado = crearProducto("Teclado");

        for (int i = 0; i < pesos.getColaborativoMinSoporte(); i++) {
            UUID s = crearSujeto();
            verProducto(s, monitor, hace(1));
            verProducto(s, teclado, hace(1));
        }

        colaborativo.recalcular();

        List<ItemRelacion> desdeMonitor = relaciones.desde(TipoItem.PRODUCTO, monitor);
        assertThat(desdeMonitor).extracting(r -> r.getId().getItemB())
                .containsExactly(teclado);
        assertThat(desdeMonitor.get(0).getSoporte())
                .isEqualTo(pesos.getColaborativoMinSoporte());
        assertThat(desdeMonitor.get(0).getScore().doubleValue())
                .as("todos los que vieron uno vieron el otro: coincidencia perfecta")
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("una sola coincidencia no basta")
    void unaCoincidenciaNoLlegaAlSoporteMinimo() {
        Long a = crearProducto("Silla");
        Long b = crearProducto("Lampara");

        UUID uno = crearSujeto();
        verProducto(uno, a, hace(1));
        verProducto(uno, b, hace(1));

        colaborativo.recalcular();

        assertThat(relaciones.findAll())
                .as("una persona con dos pestañas abiertas no es un patrón")
                .isEmpty();
    }

    @Test
    @DisplayName("un mismo sujeto mirando cien veces no fabrica soporte")
    void lasVisitasRepetidasNoInflanElSoporte() {
        /*
         * Es el fraude más barato contra un recomendador, y también el accidente
         * más común: una pestaña que se recarga sola. Si el soporte contara
         * clics en vez de personas, un solo sujeto decidiría qué se recomienda
         * en toda la tienda.
         */
        Long a = crearProducto("Mouse");
        Long b = crearProducto("Alfombrilla");

        UUID insistente = crearSujeto();
        for (int i = 0; i < 100; i++) {
            verProducto(insistente, a, hace(1));
            verProducto(insistente, b, hace(1));
        }

        colaborativo.recalcular();

        assertThat(relaciones.findAll())
                .as("cien visitas siguen siendo una persona")
                .isEmpty();
    }

    @Test
    @DisplayName("el superventas no acaba emparejado con todo")
    void laPopularidadNoDominaLasRelaciones() {
        /*
         * El fallo clásico del recuento crudo de co-visitas. Al superventas lo
         * ve todo el mundo, así que coincide con medio catálogo; el accesorio de
         * nicho solo lo ven quienes compran cierta impresora, y justamente por
         * eso es mucho mejor recomendación.
         *
         * Con soporte bruto ganaría el superventas 6 a 4. El coseno pregunta
         * otra cosa: qué PROPORCIÓN de quienes vieron la impresora vieron
         * también lo otro.
         */
        Long impresora = crearProducto("Impresora");
        Long superventas = crearProducto("Superventas");
        Long nicho = crearProducto("Toner especifico");

        for (int i = 0; i < 6; i++) {
            UUID s = crearSujeto();
            verProducto(s, impresora, hace(1));
            verProducto(s, superventas, hace(1));
            if (i < 4) {
                verProducto(s, nicho, hace(1));
            }
        }
        // Y otras veinte personas que no tienen nada que ver ven el superventas.
        for (int i = 0; i < 20; i++) {
            verProducto(crearSujeto(), superventas, hace(1));
        }

        colaborativo.recalcular();

        Map<Long, Double> desdeImpresora = relaciones.desde(TipoItem.PRODUCTO, impresora)
                .stream()
                .collect(Collectors.toMap(r -> r.getId().getItemB(),
                        r -> r.getScore().doubleValue()));

        assertThat(desdeImpresora).containsKeys(nicho, superventas);
        assertThat(desdeImpresora.get(nicho))
                .as("el nicho gana pese a tener MENOS coincidencias absolutas")
                .isGreaterThan(desdeImpresora.get(superventas));
    }

    @Test
    @DisplayName("lo que pasó fuera de la ventana no cuenta")
    void laVentanaTemporalAcota() {
        Long a = crearProducto("Ventilador");
        Long b = crearProducto("Rejilla");

        int fuera = pesos.getColaborativoVentanaDias() + 5;
        for (int i = 0; i < 5; i++) {
            UUID s = crearSujeto();
            verProducto(s, a, hace(fuera));
            verProducto(s, b, hace(fuera));
        }

        colaborativo.recalcular();

        assertThat(relaciones.findAll())
                .as("lo que se miraba junto hace dos meses ya no describe hoy")
                .isEmpty();
    }

    @Test
    @DisplayName("ejecutarlo dos veces deja exactamente lo mismo")
    void elLoteEsIdempotente() {
        Long a = crearProducto("Webcam");
        Long b = crearProducto("Aro de luz");
        for (int i = 0; i < 4; i++) {
            UUID s = crearSujeto();
            verProducto(s, a, hace(1));
            verProducto(s, b, hace(1));
        }

        colaborativo.recalcular();
        List<Double> primera = scoresOrdenados();

        colaborativo.recalcular();
        List<Double> segunda = scoresOrdenados();

        assertThat(segunda).as("ni se duplican filas ni cambian los valores")
                .isEqualTo(primera);
    }

    @Test
    @DisplayName("una relación que deja de sostenerse desaparece")
    void lasRelacionesObsoletasSePurgan() {
        Long a = crearProducto("Tablet");
        Long b = crearProducto("Funda");
        for (int i = 0; i < 4; i++) {
            UUID s = crearSujeto();
            verProducto(s, a, hace(1));
            verProducto(s, b, hace(1));
        }
        colaborativo.recalcular();
        assertThat(relaciones.findAll()).isNotEmpty();

        // Desaparece la evidencia; la relación tiene que morir con ella.
        jdbc.update("DELETE FROM catalogo.evento_interaccion");
        colaborativo.recalcular();

        assertThat(relaciones.findAll())
                .as("sin evidencia no hay relación: si no, esto sería un archivo")
                .isEmpty();
    }

    /* ══════════════ Sujeto a sujeto ══════════════ */

    @Test
    @DisplayName("dos perfiles con el mismo gusto salen parecidos")
    void perfilesParecidosProducenScoreAlto() {
        UUID ana = crearSujetoActivo();
        UUID beto = crearSujetoActivo();

        facetar(ana, "CATEGORIA", "monitores", 8.0);
        facetar(ana, "MARCA", "LG", 4.0);
        facetar(ana, "ATRIBUTO", "refresco_hz=144", 3.0);

        facetar(beto, "CATEGORIA", "monitores", 7.5);
        facetar(beto, "MARCA", "LG", 3.8);
        facetar(beto, "ATRIBUTO", "refresco_hz=144", 2.7);

        colaborativo.recalcular();

        List<SujetoSimilitud> vecinos = similitudes.vecinosDe(ana);
        assertThat(vecinos).extracting(v -> v.getId().getSujetoB()).containsExactly(beto);
        assertThat(vecinos.get(0).getScore().doubleValue())
                .as("misma dirección aunque distinta magnitud: eso mide el coseno")
                .isGreaterThan(0.98);
        assertThat(vecinos.get(0).getSoporte()).isEqualTo(3);
    }

    @Test
    @DisplayName("la relación se guarda en las dos direcciones")
    void elParecidoEsMutuo() {
        UUID ana = crearSujetoActivo();
        UUID beto = crearSujetoActivo();
        for (String[] f : new String[][] { { "CATEGORIA", "monitores" },
                { "MARCA", "LG" }, { "ATRIBUTO", "pulgadas=27" } }) {
            facetar(ana, f[0], f[1], 5.0);
            facetar(beto, f[0], f[1], 4.0);
        }

        colaborativo.recalcular();

        assertThat(similitudes.vecinosDe(ana)).hasSize(1);
        assertThat(similitudes.vecinosDe(beto))
                .as("leer «mis vecinos» tiene que ser un recorrido de índice desde cualquiera")
                .hasSize(1);
    }

    @Test
    @DisplayName("coincidir en una sola cosa no es parecerse")
    void unaFacetaCompartidaNoBasta() {
        /*
         * Sin el mínimo esto daría 1,0: el coseno de dos vectores de dimensión
         * uno vale siempre 1. Dos desconocidos que miraron el mismo teclado
         * saldrían como almas gemelas.
         */
        UUID ana = crearSujetoActivo();
        UUID beto = crearSujetoActivo();

        facetar(ana, "CATEGORIA", "teclados", 5.0);
        facetar(ana, "MARCA", "Logitech", 4.0);
        facetar(beto, "CATEGORIA", "teclados", 5.0);
        facetar(beto, "MARCA", "Razer", 4.0);

        colaborativo.recalcular();

        assertThat(similitudes.findAll()).isEmpty();
    }

    @Test
    @DisplayName("perfiles sin nada en común no se emparejan")
    void perfilesDistintosNoSeEmparejan() {
        UUID ana = crearSujetoActivo();
        UUID beto = crearSujetoActivo();

        facetar(ana, "CATEGORIA", "monitores", 8.0);
        facetar(ana, "MARCA", "LG", 4.0);
        facetar(ana, "ATRIBUTO", "pulgadas=27", 3.0);

        facetar(beto, "CATEGORIA", "impresoras", 8.0);
        facetar(beto, "MARCA", "Epson", 4.0);
        facetar(beto, "ATRIBUTO", "ppm=20", 3.0);

        colaborativo.recalcular();

        assertThat(similitudes.findAll()).isEmpty();
    }

    @Test
    @DisplayName("un sujeto sin actividad en la ventana no se compara")
    void losInactivosNoSeComparan() {
        UUID ana = crearSujetoActivo();
        UUID dormido = crearSujeto();
        verProducto(dormido, crearProducto("Algo"),
                hace(pesos.getColaborativoVentanaDias() + 10));

        for (String[] f : new String[][] { { "CATEGORIA", "monitores" },
                { "MARCA", "LG" }, { "ATRIBUTO", "pulgadas=27" } }) {
            facetar(ana, f[0], f[1], 5.0);
            facetar(dormido, f[0], f[1], 5.0);
        }

        colaborativo.recalcular();

        assertThat(similitudes.findAll())
                .as("perfiles idénticos, pero uno lleva meses sin aparecer")
                .isEmpty();
    }

    /* ══════════════ Utilidades ══════════════ */

    private List<Double> scoresOrdenados() {
        return relaciones.findAll().stream()
                .map(r -> r.getScore().doubleValue())
                .sorted()
                .toList();
    }

    private Instant hace(int dias) {
        return Instant.now().minus(dias, ChronoUnit.DAYS);
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

    /** Un sujeto con actividad reciente, que es lo que le da derecho a compararse. */
    private UUID crearSujetoActivo() {
        UUID id = crearSujeto();
        verProducto(id, crearProducto("Cualquiera"), hace(1));
        return id;
    }

    private void verProducto(UUID sujeto, Long producto, Instant cuando) {
        jdbc.update("INSERT INTO catalogo.evento_interaccion"
                + " (sujeto_id, tipo, item_tipo, item_id, ocurrido_en)"
                + " VALUES (?, 'ITEM_VIEW', 'PRODUCTO', ?, ?)",
                sujeto, producto, Timestamp.from(cuando));
    }

    private void facetar(UUID sujeto, String tipo, String faceta, double score) {
        perfiles.acumular(sujeto, tipo, faceta, score, 86_400L);
    }
}
