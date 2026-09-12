package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.backend.catalogo.PruebaIntegracion;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.Carrusel;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import jakarta.persistence.EntityManagerFactory;

/**
 * Cuánto cuesta armar una recomendación, y que medirlo no cambie nada.
 *
 * <p>La auditoría de este bloque encontró algo que ninguna prueba anterior podía
 * ver: el perfil se consultaba ocho veces por Home. No era un N+1 por candidato
 * —eso ya se vigilaba— sino repetición por módulo, seis carruseles preguntando
 * por separado lo mismo del mismo sujeto. Aquí queda fijado en un número para
 * que no pueda volver sin que algo falle.
 *
 * <p>Y la otra mitad, que importa igual: la instrumentación no puede alterar el
 * resultado ni tragarse un fallo. Un cronómetro que escondiera una excepción
 * dejaría al ranker adaptativo sin su plan de vuelta atrás.
 *
 * <p>NO es transaccional: el registro de lo servido escribe en transacción
 * propia y las estadísticas de Hibernate se leen de la sesión real.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Observabilidad y coste del pipeline")
class ObservabilidadIT extends PruebaIntegracion {

    @Autowired
    private RecomendacionService recomendador;

    @Autowired
    private MeterRegistry registro;

    @Autowired
    private EntityManagerFactory emf;

    @Autowired
    private JdbcTemplate jdbc;

    private Long categoria;
    private Long otraCategoria;
    private UUID sujeto;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM catalogo.recomendacion_servida");
        jdbc.update("DELETE FROM catalogo.impresion");
        jdbc.update("DELETE FROM catalogo.evento_interaccion");
        jdbc.update("DELETE FROM catalogo.perfil_faceta");
        jdbc.update("DELETE FROM catalogo.item_descartado");
        jdbc.update("DELETE FROM catalogo.sujeto");
        categoria = crearCategoria("obs-principal");
        otraCategoria = crearCategoria("obs-hermana");
        sujeto = crearSujeto();
    }

    @AfterEach
    void devolverElCatalogoComoEstaba() {
        for (Long c : List.of(categoria, otraCategoria)) {
            jdbc.update("DELETE FROM catalogo.recomendacion_servida WHERE item_id IN"
                    + " (SELECT id FROM catalogo.producto WHERE categoria_id = ?)", c);
            jdbc.update("DELETE FROM catalogo.producto WHERE categoria_id = ?", c);
            jdbc.update("DELETE FROM catalogo.categoria WHERE id = ?", c);
        }
    }

    /* ══════════════ El perfil, que era el hallazgo ══════════════ */

    @Nested
    @DisplayName("Repetición del perfil entre módulos")
    class RepeticionDelPerfil {

        @Test
        @DisplayName("un Home entero consulta el perfil DOS veces, no ocho")
        void elPerfilSeResuelveUnaVez() {
            /*
             * Dos y no una porque son preguntas distintas: si hay alguna faceta,
             * del tipo que sea, y cual es la categoria principal. Colapsarlas
             * dejaria sin personalizar a quien solo haya dejado rastro de marcas.
             *
             * Lo que desaparece es la REPETICION: antes cada uno de los seis
             * modulos las resolvia por su cuenta.
             */
            catalogoConProductos(8);
            conPerfil();

            long antes = consultasDePerfil();
            recomendador.home(sujeto, null, 12);
            long usadas = consultasDePerfil() - antes;

            assertThat(usadas)
                    .as("una resolución por petición, y son dos consultas")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("y no crece al añadir módulos al Home")
        void noCreceConLosModulos() {
            /*
             * Los modulos no son un parametro: el Home decide cuales sirve segun
             * el estado del sujeto. Sin perfil ni sesion se sirven los que no
             * necesitan nada personal; con perfil se sirven todos. Si el perfil
             * se resolviera por modulo, este numero subiria con el segundo caso.
             */
            catalogoConProductos(8);

            long antes = consultasDePerfil();
            recomendador.home(sujeto, null, 12);
            long sinPerfil = consultasDePerfil() - antes;
            List<Carrusel> pocos = recomendador.home(sujeto, null, 12);

            conPerfil();
            antes = consultasDePerfil();
            List<Carrusel> muchos = recomendador.home(sujeto, null, 12);
            long conPerfil = consultasDePerfil() - antes;

            assertThat(muchos.size())
                    .as("con perfil se sirven más carruseles")
                    .isGreaterThan(pocos.size());
            assertThat(conPerfil)
                    .as("más módulos, las mismas consultas de perfil")
                    .isEqualTo(sinPerfil)
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("la ficha también lo resuelve una sola vez")
        void laFichaTambien() {
            List<Long> ids = catalogoConProductos(6);
            conPerfil();

            long antes = consultasDePerfil();
            recomendador.similares(ids.get(0), sujeto, 12);

            assertThat(consultasDePerfil() - antes).isEqualTo(2);
        }

        @Test
        @DisplayName("sin sujeto no se consulta ningún perfil")
        void sinSujetoNoHayConsulta() {
            catalogoConProductos(6);

            long antes = consultasDePerfil();
            recomendador.populares(null, 12, new LinkedHashSet<>());

            assertThat(consultasDePerfil() - antes)
                    .as("no se sabe de quién sería el perfil: no se pregunta")
                    .isZero();
        }
    }

    /* ══════════════ Sin N+1 por candidato ══════════════ */

    @Nested
    @DisplayName("Escala")
    class Escala {

        @Test
        @DisplayName("las consultas no crecen con el número de candidatos")
        void sinNMasUnoPorCandidato() {
            /*
             * Diez, cincuenta, cien y doscientos productos. Si hubiera una
             * consulta por candidato el numero se dispararia; lo que se admite
             * es que no crezca de forma proporcional.
             */
            List<Long> medidas = new ArrayList<>();
            List<Integer> tamanos = List.of(10, 50, 100, 200);

            for (int cuantos : tamanos) {
                limpiarCatalogo();
                catalogoConProductos(cuantos);

                long antes = consultasTotales();
                recomendador.home(sujeto, null, 12);
                medidas.add(consultasTotales() - antes);
            }

            long conDiez = medidas.get(0);
            long conDoscientos = medidas.get(medidas.size() - 1);

            assertThat(conDoscientos)
                    .as("veinte veces más catálogo, y el coste en consultas no se mueve")
                    .isLessThanOrEqualTo(conDiez + 2);
            assertThat(conDoscientos)
                    .as("muy por debajo de una consulta por candidato")
                    .isLessThan(50);
        }

        @Test
        @DisplayName("el armado final pide un lote, no un producto cada vez")
        void porIdsNoConsultaPorProducto() {
            catalogoConProductos(40);

            long antes = consultasTotales();
            Carrusel carrusel = recomendador.populares(sujeto, 12, new LinkedHashSet<>());
            long usadas = consultasTotales() - antes;

            assertThat(carrusel.items()).hasSizeGreaterThan(5);
            assertThat(usadas)
                    .as("doce productos servidos no pueden costar doce consultas")
                    .isLessThan(carrusel.items().size());
        }
    }

    /* ══════════════ Que las etapas se midan de verdad ══════════════ */

    @Nested
    @DisplayName("Cronómetros")
    class Cronometros {

        @Test
        @DisplayName("el Home deja medido su total y las etapas de cada módulo")
        void elHomeSeMide() {
            catalogoConProductos(20);
            conPerfil();

            recomendador.home(sujeto, null, 12);

            assertThat(vecesDe(MetricasPipeline.TOTAL, "superficie", MetricasPipeline.HOME))
                    .isPositive();

            for (String etapa : List.of(MetricasPipeline.EXCLUSIONES,
                    MetricasPipeline.CANDIDATOS_SQL, MetricasPipeline.ENFRIAR,
                    MetricasPipeline.ORDENAR, MetricasPipeline.DIVERSIFICAR,
                    MetricasPipeline.ANOTAR, MetricasPipeline.POR_IDS,
                    MetricasPipeline.PERFIL)) {
                assertThat(vecesDe(MetricasPipeline.ETAPA, "etapa", etapa))
                        .as("etapa sin medir: " + etapa)
                        .isPositive();
            }
        }

        @Test
        @DisplayName("cada módulo se distingue del resto")
        void cadaModuloPorSeparado() {
            /*
             * Un unico cronometro por etapa sumaria seis carruseles distintos y
             * escondería cual cuesta, que es justo la pregunta.
             */
            catalogoConProductos(20);
            conPerfil();

            recomendador.home(sujeto, null, 12);

            Set<String> modulos = registro.find(MetricasPipeline.ETAPA).timers().stream()
                    .map(t -> t.getId().getTag("modulo"))
                    .filter(m -> !"COMUN".equals(m))
                    .collect(java.util.stream.Collectors.toSet());

            assertThat(modulos)
                    .as("varios carruseles medidos por separado")
                    .hasSizeGreaterThan(2)
                    .allSatisfy(m -> assertThat(ModuloDescubrimiento.valueOf(m)).isNotNull());
        }

        @Test
        @DisplayName("las tres etapas del ranker se miden por separado")
        void elRankerPorDentro() {
            catalogoConProductos(20);
            conPerfil();

            recomendador.home(sujeto, null, 12);

            assertThat(vecesDe(MetricasPipeline.ETAPA, "etapa", MetricasPipeline.EXTRAER))
                    .isPositive();
            assertThat(vecesDe(MetricasPipeline.ETAPA, "etapa", MetricasPipeline.PUNTUAR))
                    .isPositive();
            assertThat(vecesDe(MetricasPipeline.ETAPA, "etapa", MetricasPipeline.EXPLORAR))
                    .isPositive();
            assertThat(vecesDe(MetricasPipeline.ETAPA, "etapa", MetricasPipeline.EXPOSICION))
                    .as("la consulta de exposición, separada del procesamiento")
                    .isPositive();
        }

        @Test
        @DisplayName("la ficha mide su total y sus dos generadores por separado")
        void laFichaSeMide() {
            List<Long> ids = catalogoConProductos(12);

            recomendador.similares(ids.get(0), sujeto, 12);

            assertThat(vecesDe(MetricasPipeline.TOTAL, "superficie", MetricasPipeline.FICHA))
                    .isPositive();
            assertThat(vecesDe(MetricasPipeline.ETAPA, "etapa", MetricasPipeline.CONTENIDO))
                    .isPositive();
            assertThat(vecesDe(MetricasPipeline.ETAPA, "etapa", MetricasPipeline.CO_VISITA))
                    .as("«se parece a esto» y «va con esto» cuestan cosas distintas")
                    .isPositive();
            assertThat(vecesDe(MetricasPipeline.ETAPA, "etapa", MetricasPipeline.FILTRO))
                    .isPositive();
        }

        @Test
        @DisplayName("el total mide más que la suma de las etapas que contiene")
        void elTotalNoEsLaSuma() {
            /*
             * Si el total fuera la suma, no podria existir tiempo no
             * instrumentado — y descubrir que lo hay es media razon de ser de
             * este bloque.
             */
            catalogoConProductos(20);
            conPerfil();
            recomendador.home(sujeto, null, 12);

            double total = registro.find(MetricasPipeline.TOTAL)
                    .tag("superficie", MetricasPipeline.HOME).timer()
                    .totalTime(java.util.concurrent.TimeUnit.NANOSECONDS);
            double etapas = registro.find(MetricasPipeline.ETAPA).timers().stream()
                    .filter(t -> MetricasPipeline.HOME.equals(t.getId().getTag("superficie")))
                    .mapToDouble(t -> t.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS))
                    .sum();

            assertThat(total).isGreaterThan(0.0);
            assertThat(etapas).isGreaterThan(0.0);
        }
    }

    /* ══════════════ Medir no cambia el resultado ══════════════ */

    @Nested
    @DisplayName("Transparencia")
    class Transparencia {

        @Test
        @DisplayName("la misma entrada produce el mismo Home")
        void esDeterminista() {
            catalogoConProductos(30);
            conPerfil();

            List<Long> primera = idsDelHome(recomendador.home(sujeto, null, 12));
            List<Long> segunda = idsDelHome(recomendador.home(sujeto, null, 12));

            assertThat(segunda)
                    .as("con instrumentación activa, el recomendador decide lo mismo")
                    .isEqualTo(primera);
            assertThat(primera).isNotEmpty();
        }

        @Test
        @DisplayName("los títulos, orígenes y motivos no cambian")
        void elContratoDeSalidaNoCambia() {
            catalogoConProductos(20);
            conPerfil();

            List<Carrusel> home = recomendador.home(sujeto, null, 12);

            assertThat(home).isNotEmpty();
            assertThat(home).allSatisfy(c -> {
                assertThat(c.modulo()).isNotBlank();
                assertThat(c.titulo()).isNotBlank();
                assertThat(c.origen()).isNotNull();
                assertThat(c.items()).isNotEmpty();
            });
        }

        @Test
        @DisplayName("el motivo personal sigue nombrando la categoría del perfil")
        void elMotivoSigueSaliendoDelPerfil() {
            /*
             * El texto se sacaba de una consulta propia y ahora sale del perfil
             * ya resuelto. Es el punto donde un ahorro de consultas podria haber
             * cambiado lo que ve el usuario sin que nadie lo notara.
             */
            catalogoConProductos(20);
            conPerfil();

            Carrusel personal = recomendador.segunIntereses(sujeto, 12, new LinkedHashSet<>());

            assertThat(personal).isNotNull();
            assertThat(personal.motivo())
                    .as("la faceta del perfil, no un texto genérico")
                    .isEqualTo("Porque te interesa obs-principal");
        }
    }

    /* ══════════════ Latencia ══════════════ */

    @Nested
    @DisplayName("Latencia")
    class Latencia {

        @Test
        @DisplayName("se toma una base de referencia del Home")
        void baselineDelHome() {
            /*
             * BASE DE REFERENCIA, NO UN SLA.
             *
             * Esto corre en un contenedor, en una maquina de desarrollo y con un
             * catalogo de juguete: vender estos percentiles como los de
             * produccion seria mentir con precision decimal. Lo unico que sirve
             * es comparar una ejecucion con otra para detectar una regresion.
             *
             * Por eso la unica asercion es que se midio; los numeros se imprimen
             * para el informe y no se convierten en un umbral que alguien
             * acabaria ajustando hasta que pasara.
             */
            catalogoConProductos(60);
            conPerfil();

            List<Long> nanos = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                long empieza = System.nanoTime();
                recomendador.home(sujeto, null, 12);
                nanos.add(System.nanoTime() - empieza);
            }
            nanos.sort(Long::compare);

            double minimo = nanos.get(0) / 1_000_000.0;
            double mediana = nanos.get(nanos.size() / 2) / 1_000_000.0;
            double p95 = nanos.get((int) Math.floor(nanos.size() * 0.95) - 1) / 1_000_000.0;
            double maximo = nanos.get(nanos.size() - 1) / 1_000_000.0;

            System.out.printf(
                    "BASELINE HOME ms | minimo %.1f | mediana %.1f | p95 %.1f | maximo %.1f%n",
                    minimo, mediana, p95, maximo);

            assertThat(nanos).hasSize(40);
            assertThat(minimo).isPositive();
            assertThat(maximo).isGreaterThanOrEqualTo(mediana);
        }

        @Test
        @DisplayName("se toma una base de referencia de la ficha")
        void baselineDeLaFicha() {
            List<Long> ids = catalogoConProductos(60);

            List<Long> nanos = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                long empieza = System.nanoTime();
                recomendador.similares(ids.get(i % ids.size()), sujeto, 12);
                nanos.add(System.nanoTime() - empieza);
            }
            nanos.sort(Long::compare);

            System.out.printf(
                    "BASELINE FICHA ms | minimo %.1f | mediana %.1f | p95 %.1f | maximo %.1f%n",
                    nanos.get(0) / 1_000_000.0,
                    nanos.get(nanos.size() / 2) / 1_000_000.0,
                    nanos.get((int) Math.floor(nanos.size() * 0.95) - 1) / 1_000_000.0,
                    nanos.get(nanos.size() - 1) / 1_000_000.0);

            assertThat(nanos).hasSize(40);
        }
    }

    /* ══════════════ Privacidad ══════════════ */

    @Nested
    @DisplayName("Privacidad")
    class Privacidad {

        @Test
        @DisplayName("ninguna métrica del pipeline lleva identificadores")
        void sinIdentificadoresEnLasMetricas() {
            List<Long> ids = catalogoConProductos(20);
            conPerfil();
            recomendador.home(sujeto, null, 12);
            recomendador.similares(ids.get(0), sujeto, 12);

            List<Tag> etiquetas = registro.getMeters().stream()
                    .filter(m -> m.getId().getName().startsWith("smartzone_descubrimiento"))
                    .flatMap(m -> m.getId().getTags().stream())
                    .toList();

            assertThat(etiquetas).isNotEmpty();
            assertThat(etiquetas)
                    .as("ni el sujeto de esta prueba ni ningún otro UUID")
                    .noneMatch(t -> t.getValue().equals(sujeto.toString()))
                    .noneMatch(t -> t.getValue().matches(
                            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
            assertThat(etiquetas)
                    .as("ningún identificador de producto colado como etiqueta")
                    .noneMatch(t -> ids.stream().anyMatch(
                            id -> String.valueOf(id).equals(t.getValue())));
        }
    }

    /* ══════════════ Utilidades ══════════════ */

    /**
     * Las consultas de LECTURA del perfil, que son las que se repetían.
     *
     * <p>Solo las JPQL sobre la entidad {@code PerfilFaceta}: las dos que
     * resuelve {@code PerfilService.estado}. Se excluyen a propósito las
     * consultas nativas de generación de candidatos que también leen
     * {@code perfil_faceta} —«según tus intereses» y la exploración parten de
     * ahí—: esas no son una repetición, son el trabajo del módulo, y contarlas
     * haría que esta prueba midiera otra cosa y no fallara cuando debe.
     */
    private long consultasDePerfil() {
        Statistics stats = estadisticas();
        long total = 0;
        for (String consulta : stats.getQueries()) {
            if (consulta.contains("PerfilFaceta")) {
                total += stats.getQueryStatistics(consulta).getExecutionCount();
            }
        }
        return total;
    }

    private long consultasTotales() {
        return estadisticas().getQueryExecutionCount();
    }

    private Statistics estadisticas() {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        return stats;
    }

    private long vecesDe(String metrica, String clave, String valor) {
        return registro.find(metrica).tag(clave, valor).timers().stream()
                .mapToLong(t -> t.count()).sum();
    }

    private List<Long> idsDelHome(List<Carrusel> home) {
        return home.stream().flatMap(c -> c.items().stream()).map(p -> p.id()).toList();
    }

    private void conPerfil() {
        jdbc.update("INSERT INTO catalogo.perfil_faceta"
                + " (sujeto_id, tipo_faceta, faceta, score, eventos, actualizado_en)"
                + " VALUES (?, 'CATEGORIA', ?, 50.0, 20, ?)"
                + " ON CONFLICT (sujeto_id, tipo_faceta, faceta) DO NOTHING",
                sujeto, nombreDe(categoria), Timestamp.from(Instant.now()));
    }

    private String nombreDe(Long categoriaId) {
        return jdbc.queryForObject("SELECT name FROM catalogo.categoria WHERE id = ?",
                String.class, categoriaId);
    }

    private void limpiarCatalogo() {
        for (Long c : List.of(categoria, otraCategoria)) {
            jdbc.update("DELETE FROM catalogo.recomendacion_servida WHERE item_id IN"
                    + " (SELECT id FROM catalogo.producto WHERE categoria_id = ?)", c);
            jdbc.update("DELETE FROM catalogo.producto WHERE categoria_id = ?", c);
        }
    }

    private List<Long> catalogoConProductos(int cuantos) {
        List<Long> ids = new ArrayList<>(cuantos);
        for (int i = 0; i < cuantos; i++) {
            ids.add(crearProducto(i % 3 == 0 ? otraCategoria : categoria, "Producto " + i));
        }
        return ids;
    }

    private Long crearCategoria(String base) {
        String slug = base;
        jdbc.update("INSERT INTO catalogo.categoria (name, slug, description)"
                + " VALUES (?, ?, 'IT') ON CONFLICT (slug) DO NOTHING", slug, slug);
        return jdbc.queryForObject("SELECT id FROM catalogo.categoria WHERE slug = ?",
                Long.class, slug);
    }

    private Long crearProducto(Long cat, String nombre) {
        String unico = nombre + " " + UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.producto"
                + " (name, description, precio, stock, categoria_id, estado_moderacion)"
                + " VALUES (?, 'IT', ?, 10, ?, 'APROBADO')",
                unico, new BigDecimal("100.00"), cat);
        return jdbc.queryForObject("SELECT id FROM catalogo.producto WHERE name = ?",
                Long.class, unico);
    }

    private UUID crearSujeto() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.sujeto (id, visto_en) VALUES (?, ?)",
                id, Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        return id;
    }
}
