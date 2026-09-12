package com.backend.catalogo.descubrimiento.cobertura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
import com.backend.catalogo.descubrimiento.ModuloDescubrimiento;
import com.backend.catalogo.descubrimiento.cobertura.InformeCobertura.Cobertura;
import com.backend.catalogo.descubrimiento.cobertura.InformeCobertura.Faceta;

/**
 * Qué parte del catálogo existe para el recomendador, y qué parte no.
 *
 * <p>La propiedad que estas pruebas defienden por encima de todas es que las
 * preguntas se hacen desde el UNIVERSO y no desde lo expuesto. Una consulta que
 * parta de lo servido puede decir qué categorías salieron; no puede decir cuáles
 * llevan un mes sin salir, que es la pregunta cara. Por eso casi todas las
 * pruebas de aquí crean algo elegible que NO se enseña y exigen que aparezca.
 *
 * <p>NO es transaccional: el catálogo que crea tiene que verlo el SQL agregado.
 * Por eso la limpieza del {@code @AfterEach} es explícita y completa.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Cobertura y concentración")
class CoberturaIT extends PruebaIntegracion {

    @Autowired
    private CoberturaService cobertura;

    @Autowired
    private JdbcTemplate jdbc;

    private Long catMonitores;
    private Long catImpresoras;
    private Long catVacia;
    private Long marcaExpuesta;
    private Long marcaOlvidada;
    private Long marcaSinCatalogo;
    private UUID sujeto;
    private Instant desde;
    private Instant hasta;

    /** Los que esta prueba apartó, para devolver EXACTAMENTE esos y ninguno más. */
    private List<Long> apartados = List.of();

    @BeforeEach
    void prepararUnCatalogoPropio() {
        jdbc.update("DELETE FROM catalogo.recomendacion_servida");
        jdbc.update("DELETE FROM catalogo.impresion");
        jdbc.update("DELETE FROM catalogo.sujeto");
        sujeto = crearSujeto();

        /*
         * El catalogo sembrado se aparta durante la prueba y se devuelve al
         * terminar. La cobertura mide TODO el catalogo elegible por definicion,
         * asi que sin esto el denominador serian los sesenta y ocho productos de
         * la semilla y ninguna cifra de aqui seria comprobable.
         *
         * Se retira con `stock = 0`, que es uno de los dos criterios de
         * elegibilidad: no se borra nada ni se inventa un estado nuevo.
         *
         * Y se ANOTA CUALES. Restaurar «los que esten a cero» devolveria tambien
         * los que ya estaban agotados de antes, con lo que esta clase acabaria
         * agrandando el catalogo elegible para las que corren despues — que es
         * justo la forma de romper `EvaluacionOfflineIT` por un umbral sin haber
         * tocado el recomendador. Ya paso una vez, en el bloque A.
         */
        apartados = jdbc.queryForList(
                "SELECT id FROM catalogo.producto WHERE stock > 0", Long.class);
        if (!apartados.isEmpty()) {
            jdbc.update("UPDATE catalogo.producto SET stock = 0 WHERE id IN ("
                    + apartados.stream().map(String::valueOf)
                            .collect(java.util.stream.Collectors.joining(","))
                    + ")");
        }

        catMonitores = crearCategoria("cob-monitores");
        catImpresoras = crearCategoria("cob-impresoras");
        catVacia = crearCategoria("cob-sin-catalogo");

        marcaExpuesta = crearMarca("cob-marca-expuesta");
        marcaOlvidada = crearMarca("cob-marca-olvidada");
        marcaSinCatalogo = crearMarca("cob-marca-sin-catalogo");

        desde = Instant.now().minus(1, ChronoUnit.HOURS);
        hasta = Instant.now().plus(1, ChronoUnit.HOURS);
    }

    @AfterEach
    void devolverElCatalogoComoEstaba() {
        jdbc.update("DELETE FROM catalogo.recomendacion_servida WHERE item_id IN"
                + " (SELECT id FROM catalogo.producto WHERE categoria_id IN (?, ?, ?))",
                catMonitores, catImpresoras, catVacia);
        jdbc.update("DELETE FROM catalogo.producto WHERE categoria_id IN (?, ?, ?)",
                catMonitores, catImpresoras, catVacia);
        jdbc.update("DELETE FROM catalogo.categoria WHERE id IN (?, ?, ?)",
                catMonitores, catImpresoras, catVacia);
        jdbc.update("DELETE FROM catalogo.marca WHERE id IN (?, ?, ?)",
                marcaExpuesta, marcaOlvidada, marcaSinCatalogo);
        if (!apartados.isEmpty()) {
            jdbc.update("UPDATE catalogo.producto SET stock = 10 WHERE id IN ("
                    + apartados.stream().map(String::valueOf)
                            .collect(java.util.stream.Collectors.joining(","))
                    + ")");
        }
    }

    /* ══════════════ Productos ══════════════ */

    @Nested
    @DisplayName("Cobertura de productos")
    class Productos {

        @Test
        @DisplayName("lo elegible y expuesto cuenta; lo elegible y callado también, en el denominador")
        void elegibleExpuestoYElegibleSinExponer() {
            Long visible = crearProducto(catMonitores, null, "Monitor servido");
            crearProducto(catMonitores, null, "Monitor nunca servido");

            servir(visible, ModuloDescubrimiento.POPULARES, 0, ahora());

            Cobertura servido = cobertura.medir(desde, hasta, null).servido();

            assertThat(servido.productos().elegibles()).isEqualTo(2);
            assertThat(servido.productos().expuestos()).isEqualTo(1);
            assertThat(servido.productos().proporcion()).isEqualTo(0.5);
            assertThat(servido.productos().sinExposicion()).isEqualTo(1);
        }

        @Test
        @DisplayName("un producto no elegible no entra en el denominador ni aunque se sirviera")
        void loNoElegibleNoCuenta() {
            /*
             * El caso que puede maquillar la cifra en las dos direcciones. Un
             * producto agotado no puede recomendarse, asi que no puede reprochar
             * nada al recomendador; y si aparece en el historico de lo servido
             * —porque se agoto despues— tampoco puede sumar como acierto.
             */
            Long vivo = crearProducto(catMonitores, null, "Monitor vivo");
            Long agotado = crearProducto(catMonitores, null, "Monitor agotado");
            jdbc.update("UPDATE catalogo.producto SET stock = 0 WHERE id = ?", agotado);

            servir(vivo, ModuloDescubrimiento.POPULARES, 0, ahora());
            servir(agotado, ModuloDescubrimiento.POPULARES, 1, ahora());

            Cobertura servido = cobertura.medir(desde, hasta, null).servido();

            assertThat(servido.productos().elegibles())
                    .as("el agotado no está en el universo")
                    .isEqualTo(1);
            assertThat(servido.productos().expuestos())
                    .as("y tampoco suma como expuesto, aunque se sirviera")
                    .isEqualTo(1);
            assertThat(servido.productos().proporcion()).isEqualTo(1.0);
        }
    }

    /* ══════════════ Categorías ══════════════ */

    @Nested
    @DisplayName("Cobertura de categorías")
    class Categorias {

        @Test
        @DisplayName("una categoría elegible sin ninguna exposición aparece, que es el punto")
        void laCategoriaOlvidadaAparece() {
            /*
             * La prueba que justifica el LEFT JOIN desde el universo. Con un
             * `SELECT DISTINCT categoria FROM recomendacion_servida` esta
             * categoria seria invisible: no genera ninguna fila, y no generar
             * ninguna fila es precisamente el problema que hay que detectar.
             */
            Long monitor = crearProducto(catMonitores, null, "Monitor servido");
            crearProducto(catImpresoras, null, "Impresora nunca servida");

            servir(monitor, ModuloDescubrimiento.POPULARES, 0, ahora());

            Cobertura servido = cobertura.medir(desde, hasta, null).servido();

            assertThat(servido.categorias().elegibles()).isEqualTo(2);
            assertThat(servido.categorias().expuestos()).isEqualTo(1);
            assertThat(servido.categoriasSinExposicion())
                    .extracting(Faceta::id)
                    .containsExactly(catImpresoras);
        }

        @Test
        @DisplayName("una categoría sin catálogo vivo no es culpa del recomendador")
        void laCategoriaSinCatalogoNoCuenta() {
            /*
             * `catVacia` existe y no tiene ni un producto elegible. Contarla
             * como omitida seria cargarle al recomendador un problema de
             * catalogo: no puede enseñar lo que no hay.
             */
            Long monitor = crearProducto(catMonitores, null, "Monitor servido");
            servir(monitor, ModuloDescubrimiento.POPULARES, 0, ahora());

            Cobertura servido = cobertura.medir(desde, hasta, null).servido();

            assertThat(servido.facetasDeCategoria()).extracting(Faceta::id)
                    .as("no está en el universo porque no hay nada que recomendar")
                    .doesNotContain(catVacia);
            assertThat(servido.categorias().elegibles()).isEqualTo(1);
        }

        @Test
        @DisplayName("una categoría con varios productos mide su propia cobertura interna")
        void laCoberturaInternaDeUnaCategoria() {
            Long uno = crearProducto(catMonitores, null, "Monitor A");
            crearProducto(catMonitores, null, "Monitor B");
            crearProducto(catMonitores, null, "Monitor C");
            crearProducto(catMonitores, null, "Monitor D");

            servir(uno, ModuloDescubrimiento.POPULARES, 0, ahora());

            Faceta monitores = facetaDe(cobertura.medir(desde, hasta, null).servido()
                    .facetasDeCategoria(), catMonitores);

            assertThat(monitores.productosElegibles()).isEqualTo(4);
            assertThat(monitores.productosExpuestos()).isEqualTo(1);
            assertThat(monitores.coberturaInterna())
                    .as("la categoría sale, pero solo uno de sus cuatro productos")
                    .isEqualTo(0.25);
        }
    }

    /* ══════════════ Marcas ══════════════ */

    @Nested
    @DisplayName("Cobertura de marcas")
    class Marcas {

        @Test
        @DisplayName("marca expuesta, marca elegible callada y marca sin catálogo")
        void lasTresSituacionesDeUnaMarca() {
            Long conMarca = crearProducto(catMonitores, marcaExpuesta, "Monitor de marca");
            crearProducto(catMonitores, marcaOlvidada, "Monitor de marca olvidada");

            servir(conMarca, ModuloDescubrimiento.POPULARES, 0, ahora());

            Cobertura servido = cobertura.medir(desde, hasta, null).servido();

            assertThat(servido.marcas().elegibles())
                    .as("la marca sin un solo producto vivo no entra en el universo")
                    .isEqualTo(2);
            assertThat(servido.marcas().expuestos()).isEqualTo(1);
            assertThat(servido.marcasSinExposicion()).extracting(Faceta::id)
                    .containsExactly(marcaOlvidada)
                    .doesNotContain(marcaSinCatalogo);
        }

        @Test
        @DisplayName("un producto sin marca cuenta como producto pero no infla ninguna marca")
        void elProductoSinMarcaNoDistorsiona() {
            /*
             * `marca_id` admite nulo, asi que el total de productos NO se puede
             * sacar de la consulta de marcas: se saca de la de categorias, donde
             * la columna es obligatoria y cada producto cuenta una vez.
             */
            Long sinMarca = crearProducto(catMonitores, null, "Monitor genérico");
            Long conMarca = crearProducto(catMonitores, marcaExpuesta, "Monitor de marca");

            servir(sinMarca, ModuloDescubrimiento.POPULARES, 0, ahora());
            servir(conMarca, ModuloDescubrimiento.POPULARES, 1, ahora());

            Cobertura servido = cobertura.medir(desde, hasta, null).servido();

            assertThat(servido.productos().elegibles())
                    .as("los dos son productos elegibles")
                    .isEqualTo(2);
            assertThat(servido.facetasDeMarca())
                    .as("solo hay una marca con catálogo vivo; el genérico no crea ninguna")
                    .hasSize(1);
            assertThat(facetaDe(servido.facetasDeMarca(), marcaExpuesta).productosElegibles())
                    .as("el producto sin marca no se le atribuye a nadie")
                    .isEqualTo(1);
            assertThat(servido.marcas().elegibles())
                    .as("el universo de marcas es MENOR que el de productos, y está bien")
                    .isLessThan(servido.productos().elegibles());
        }
    }

    /* ══════════════ Concentración ══════════════ */

    @Nested
    @DisplayName("Concentración")
    class Concentracion {

        @Test
        @DisplayName("cuando una categoría se lo lleva casi todo, el top 1 lo dice")
        void unaCategoriaAcapara() {
            Long monitor = crearProducto(catMonitores, null, "Monitor");
            Long impresora = crearProducto(catImpresoras, null, "Impresora");

            servirVeces(monitor, ModuloDescubrimiento.POPULARES, 9);
            servirVeces(impresora, ModuloDescubrimiento.POPULARES, 1);

            InformeCobertura.Concentracion c =
                    cobertura.medir(desde, hasta, null).servido().concentracion();

            assertThat(c.exposiciones()).isEqualTo(10);
            assertThat(c.top1()).isCloseTo(0.9, within(0.001));
            assertThat(c.top5())
                    .as("con solo dos categorías, las cinco primeras son todo")
                    .isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("con reparto equilibrado el top 1 baja")
        void repartoEquilibrado() {
            Long monitor = crearProducto(catMonitores, null, "Monitor");
            Long impresora = crearProducto(catImpresoras, null, "Impresora");

            servirVeces(monitor, ModuloDescubrimiento.POPULARES, 5);
            servirVeces(impresora, ModuloDescubrimiento.POPULARES, 5);

            assertThat(cobertura.medir(desde, hasta, null).servido().concentracion().top1())
                    .isCloseTo(0.5, within(0.001));
        }

        @Test
        @DisplayName("se mide exposición, no tamaño de catálogo")
        void concentraPorExposicionNoPorTamano() {
            /*
             * Impresoras tiene el triple de catalogo y aun asi concentra menos:
             * lo que se reparte son exposiciones. Contar productos distintos
             * responderia «que categoria es mas grande», que no es la pregunta.
             */
            Long monitor = crearProducto(catMonitores, null, "Monitor único");
            Long i1 = crearProducto(catImpresoras, null, "Impresora A");
            Long i2 = crearProducto(catImpresoras, null, "Impresora B");
            Long i3 = crearProducto(catImpresoras, null, "Impresora C");

            servirVeces(monitor, ModuloDescubrimiento.POPULARES, 20);
            for (Long i : List.of(i1, i2, i3)) {
                servirVeces(i, ModuloDescubrimiento.POPULARES, 1);
            }

            Cobertura servido = cobertura.medir(desde, hasta, null).servido();

            assertThat(servido.concentracion().top1())
                    .as("una categoría de un solo producto puede acaparar el reparto")
                    .isGreaterThan(0.8);
            assertThat(facetaDe(servido.facetasDeCategoria(), catImpresoras)
                    .productosExpuestos()).isEqualTo(3);
        }

        @Test
        @DisplayName("una categoría dominada por dos productos se detecta por dentro")
        void concentracionInternaPorProducto() {
            /*
             * El caso del enunciado: mil exposiciones en una categoria no dicen
             * si la categoria funciona o si dos productos se la comen. Esto es
             * lo segundo, y se ve sin guardar en ningun sitio cual era.
             */
            Long acaparador = crearProducto(catMonitores, null, "Monitor estrella");
            Long secundario = crearProducto(catMonitores, null, "Monitor B");
            crearProducto(catMonitores, null, "Monitor C");

            servirVeces(acaparador, ModuloDescubrimiento.POPULARES, 18);
            servirVeces(secundario, ModuloDescubrimiento.POPULARES, 2);

            Cobertura servido = cobertura.medir(desde, hasta, null).servido();
            Faceta monitores = facetaDe(servido.facetasDeCategoria(), catMonitores);

            assertThat(monitores.exposiciones()).isEqualTo(20);
            assertThat(monitores.topProductoPct()).isCloseTo(0.9, within(0.001));
            assertThat(servido.categoriasDominadasPorPocosProductos(0.5))
                    .extracting(Faceta::id)
                    .contains(catMonitores);
        }

        @Test
        @DisplayName("una categoría repartida por dentro no se marca como dominada")
        void sinDominioInterno() {
            Long a = crearProducto(catMonitores, null, "Monitor A");
            Long b = crearProducto(catMonitores, null, "Monitor B");
            Long c = crearProducto(catMonitores, null, "Monitor C");

            for (Long p : List.of(a, b, c)) {
                servirVeces(p, ModuloDescubrimiento.POPULARES, 5);
            }

            assertThat(cobertura.medir(desde, hasta, null).servido()
                    .categoriasDominadasPorPocosProductos(0.5))
                    .isEmpty();
        }
    }

    /* ══════════════ Posición y módulos ══════════════ */

    @Nested
    @DisplayName("Bandas y módulos")
    class BandasYModulos {

        @Test
        @DisplayName("la banda es la de la fase 3, no otra clasificación")
        void conservaLasBandasExistentes() {
            /*
             * `LEAST(posicion / 3, 7)`: la 0 y la 2 caen en la banda 0, la 3 en
             * la 1 y la 11 en la 3. Es la misma expresion que la agregacion
             * diaria, repetida a proposito: dos criterios de posicion conviviendo
             * harian incomparables los numeros de un panel con los del otro.
             */
            Long producto = crearProducto(catMonitores, null, "Monitor");

            servir(producto, ModuloDescubrimiento.POPULARES, 0, ahora());
            servir(producto, ModuloDescubrimiento.POPULARES, 2, ahora());
            servir(producto, ModuloDescubrimiento.POPULARES, 3, ahora());
            servir(producto, ModuloDescubrimiento.POPULARES, 11, ahora());

            List<InformeCobertura.Banda> bandas =
                    cobertura.medir(desde, hasta, null).servido().bandas();

            assertThat(bandas).extracting(InformeCobertura.Banda::banda)
                    .containsExactly((short) 0, (short) 1, (short) 3);
            assertThat(bandas.get(0).exposiciones())
                    .as("las posiciones 0 y 2 caen en la misma banda")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("la concentración se puede mirar banda a banda")
        void concentracionPorBanda() {
            /*
             * El sistema puede parecer variado en total y estar poniendo siempre
             * la misma categoria en las tres primeras tarjetas, que es donde la
             * gente mira. Sin partir por banda, eso no se ve.
             */
            Long monitor = crearProducto(catMonitores, null, "Monitor");
            Long impresora = crearProducto(catImpresoras, null, "Impresora");

            servir(monitor, ModuloDescubrimiento.POPULARES, 0, ahora());
            servir(monitor, ModuloDescubrimiento.POPULARES, 1, ahora());
            servir(impresora, ModuloDescubrimiento.POPULARES, 6, ahora());
            servir(impresora, ModuloDescubrimiento.POPULARES, 7, ahora());

            List<InformeCobertura.Banda> bandas =
                    cobertura.medir(desde, hasta, null).servido().bandas();

            assertThat(bandas.get(0).top1())
                    .as("arriba manda una sola categoría")
                    .isEqualTo(1.0);
            assertThat(bandas.get(0).categorias()).isEqualTo(1);
        }

        @Test
        @DisplayName("se puede preguntar por un módulo concreto")
        void filtraPorModulo() {
            Long monitor = crearProducto(catMonitores, null, "Monitor");
            Long impresora = crearProducto(catImpresoras, null, "Impresora");

            servir(monitor, ModuloDescubrimiento.POPULARES, 0, ahora());
            servir(impresora, ModuloDescubrimiento.RELACIONADOS, 0, ahora());

            Cobertura soloPopulares = cobertura
                    .medir(desde, hasta, ModuloDescubrimiento.POPULARES.name()).servido();

            assertThat(soloPopulares.categorias().expuestos()).isEqualTo(1);
            assertThat(soloPopulares.categoriasSinExposicion()).extracting(Faceta::id)
                    .as("en «lo más popular» las impresoras no salen")
                    .containsExactly(catImpresoras);
            assertThat(cobertura.medir(desde, hasta, null).servido()
                    .categorias().expuestos())
                    .as("mirando toda la pantalla, sí salen")
                    .isEqualTo(2);
        }
    }

    /* ══════════════ Servido frente a visto ══════════════ */

    @Nested
    @DisplayName("Servido no es visto")
    class ServidoFrenteAVisto {

        @Test
        @DisplayName("lo servido y no visto cuenta en una columna y no en la otra")
        void servidoPeroNoVisto() {
            Long producto = crearProducto(catMonitores, null, "Monitor del final");

            servir(producto, ModuloDescubrimiento.POPULARES, 11, ahora());

            InformeCobertura informe = cobertura.medir(desde, hasta, null);

            assertThat(informe.servido().productos().expuestos()).isEqualTo(1);
            assertThat(informe.visto().productos().expuestos())
                    .as("el backend lo propuso; nadie se desplazó hasta ahí")
                    .isZero();
            assertThat(informe.visto().categoriasSinExposicion())
                    .extracting(Faceta::id).contains(catMonitores);
        }

        @Test
        @DisplayName("lo servido y visto cuenta en las dos")
        void servidoYVisto() {
            Long producto = crearProducto(catMonitores, null, "Monitor de arriba");

            servir(producto, ModuloDescubrimiento.POPULARES, 0, ahora());
            mostrar(producto, ModuloDescubrimiento.POPULARES, 0, ahora());

            InformeCobertura informe = cobertura.medir(desde, hasta, null);

            assertThat(informe.servido().productos().expuestos()).isEqualTo(1);
            assertThat(informe.visto().productos().expuestos()).isEqualTo(1);
        }

        @Test
        @DisplayName("una categoría servida entera sin una sola vista se distingue")
        void categoriaServidaSinVistas() {
            Long monitor = crearProducto(catMonitores, null, "Monitor");
            Long impresora = crearProducto(catImpresoras, null, "Impresora");

            servir(monitor, ModuloDescubrimiento.POPULARES, 0, ahora());
            servir(impresora, ModuloDescubrimiento.POPULARES, 1, ahora());
            mostrar(monitor, ModuloDescubrimiento.POPULARES, 0, ahora());

            InformeCobertura informe = cobertura.medir(desde, hasta, null);

            assertThat(informe.servido().categorias().expuestos()).isEqualTo(2);
            assertThat(informe.visto().categorias().expuestos()).isEqualTo(1);
            assertThat(informe.visto().categoriasSinExposicion())
                    .extracting(Faceta::id)
                    .as("se sirvió y no se vio: son dos cifras distintas a propósito")
                    .containsExactly(catImpresoras);
        }

        @Test
        @DisplayName("las dos fuentes nunca se suman en la misma cifra")
        void nuncaSeSuman() {
            Long producto = crearProducto(catMonitores, null, "Monitor");

            servirVeces(producto, ModuloDescubrimiento.POPULARES, 3);
            for (int i = 0; i < 5; i++) {
                mostrar(producto, ModuloDescubrimiento.POPULARES, 0, ahora());
            }

            InformeCobertura informe = cobertura.medir(desde, hasta, null);

            assertThat(informe.servido().concentracion().exposiciones()).isEqualTo(3);
            assertThat(informe.visto().concentracion().exposiciones()).isEqualTo(5);
        }
    }

    /* ══════════════ Temporalidad ══════════════ */

    @Nested
    @DisplayName("Temporalidad")
    class Temporalidad {

        @Test
        @DisplayName("dentro del periodo cuenta; antes y después, no")
        void soloLoDelPeriodo() {
            Long dentro = crearProducto(catMonitores, null, "Monitor de hoy");
            Long antes = crearProducto(catMonitores, null, "Monitor de anteayer");
            Long despues = crearProducto(catMonitores, null, "Monitor de mañana");

            servir(dentro, ModuloDescubrimiento.POPULARES, 0, ahora());
            servir(antes, ModuloDescubrimiento.POPULARES, 0, desde.minusSeconds(60));
            servir(despues, ModuloDescubrimiento.POPULARES, 0, hasta.plusSeconds(60));

            Cobertura servido = cobertura.medir(desde, hasta, null).servido();

            assertThat(servido.productos().elegibles()).isEqualTo(3);
            assertThat(servido.productos().expuestos())
                    .as("el universo no depende del periodo; la exposición sí")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("la frontera: el inicio entra y el final no")
        void lasFronterasExactas() {
            /*
             * Medio abierto por arriba, que es la semantica que ya usa la
             * agregacion diaria. Con las dos fronteras cerradas, dos periodos
             * consecutivos contarian dos veces la misma exposicion.
             */
            Long enElBorde = crearProducto(catMonitores, null, "Monitor del inicio exacto");
            Long fuera = crearProducto(catMonitores, null, "Monitor del final exacto");

            servir(enElBorde, ModuloDescubrimiento.POPULARES, 0, desde);
            servir(fuera, ModuloDescubrimiento.POPULARES, 0, hasta);

            Cobertura servido = cobertura.medir(desde, hasta, null).servido();

            assertThat(servido.productos().expuestos()).isEqualTo(1);
            assertThat(facetaDe(servido.facetasDeCategoria(), catMonitores).exposiciones())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("un periodo del revés es un error, no un cero silencioso")
        void elPeriodoDelReves() {
            assertThatThrownBy(() -> cobertura.medir(hasta, desde, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /* ══════════════ Diversidad no es cobertura ══════════════ */

    @Nested
    @DisplayName("Diversidad local alta, cobertura global baja")
    class DiversidadFrenteACobertura {

        @Test
        @DisplayName("carruseles perfectamente variados sobre un catálogo casi entero sin tocar")
        void elCasoQueLasConfunde() {
            /*
             * EL caso que justifica todo el bloque.
             *
             * Cada carrusel servido tiene dos categorias distintas en dos
             * huecos: diversidad 1,0, la maxima posible — la evaluacion offline
             * diria que el recomendador es variadisimo. Y sin embargo hay ocho
             * categorias con catalogo vivo a las que no manda a nadie nunca.
             *
             * Las dos cosas son ciertas a la vez y miden cosas distintas: la
             * diversidad mira DENTRO de una lista, la cobertura mira el catalogo
             * ENTERO. Quien solo tenga la primera concluira que va bien.
             */
            Long monitor = crearProducto(catMonitores, null, "Monitor");
            Long impresora = crearProducto(catImpresoras, null, "Impresora");

            List<Long> olvidadas = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                Long otra = crearCategoria("cob-olvidada-" + i);
                olvidadas.add(otra);
                crearProducto(otra, null, "Producto de olvidada " + i);
            }
            try {
                for (int vuelta = 0; vuelta < 5; vuelta++) {
                    servir(monitor, ModuloDescubrimiento.POPULARES, 0, ahora());
                    servir(impresora, ModuloDescubrimiento.POPULARES, 1, ahora());
                }

                Cobertura servido = cobertura.medir(desde, hasta, null).servido();

                assertThat(servido.categorias().elegibles()).isEqualTo(10);
                assertThat(servido.categorias().expuestos()).isEqualTo(2);
                assertThat(servido.categorias().proporcion())
                        .as("diversidad impecable dentro de cada lista, y 4 de cada 5"
                                + " categorías a oscuras")
                        .isCloseTo(0.2, within(0.001));
                assertThat(servido.categoriasSinExposicion()).hasSize(8);

                // Y dentro de cada carrusel no se repite ni una categoria.
                assertThat(servido.bandas().get(0).categorias())
                        .as("la banda de cabecera tiene dos categorías en dos huecos")
                        .isEqualTo(2);
            } finally {
                for (Long otra : olvidadas) {
                    jdbc.update("DELETE FROM catalogo.recomendacion_servida WHERE item_id IN"
                            + " (SELECT id FROM catalogo.producto WHERE categoria_id = ?)",
                            otra);
                    jdbc.update("DELETE FROM catalogo.producto WHERE categoria_id = ?", otra);
                    jdbc.update("DELETE FROM catalogo.categoria WHERE id = ?", otra);
                }
            }
        }
    }

    /* ══════════════ Privacidad ══════════════ */

    @Nested
    @DisplayName("Privacidad")
    class Privacidad {

        @Test
        @DisplayName("el informe no lleva sujetos, sesiones ni identificadores de persona")
        void elInformeNoDelataANadie() {
            /*
             * Se comprueba sobre la FORMA del resultado y no sobre su contenido.
             * Que hoy no salga un UUID podria ser suerte del dato; que ningun
             * campo del informe sea capaz de contener uno es una propiedad.
             */
            Long producto = crearProducto(catMonitores, null, "Monitor");
            servirVeces(producto, ModuloDescubrimiento.POPULARES, 3);
            mostrar(producto, ModuloDescubrimiento.POPULARES, 0, ahora());

            InformeCobertura informe = cobertura.medir(desde, hasta, null);

            assertThat(informe.toString())
                    .as("el sujeto que produjo estas exposiciones no viaja en el informe")
                    .doesNotContain(sujeto.toString());

            /*
             * Y la parte estructural, que es la que de verdad sostiene la
             * afirmacion. Buscar UUIDs en el texto no serviria: los nombres de
             * categoria y marca de esta prueba llevan uno para no chocar, y son
             * datos legitimos del informe. Lo que hay que demostrar es que
             * NINGUN campo del arbol puede contener una identidad.
             */
            assertThat(camposDe(InformeCobertura.class))
                    .noneMatch(c -> c.getType() == UUID.class)
                    .extracting(java.lang.reflect.RecordComponent::getName)
                    .map(String::toLowerCase)
                    .noneMatch(n -> n.contains("sujeto") || n.contains("sesion")
                            || n.contains("usuario") || n.contains("ip"));

            assertThat(InformeCobertura.Faceta.class.getRecordComponents())
                    .extracting(java.lang.reflect.RecordComponent::getName)
                    .as("la faceta guarda un MÁXIMO, no de qué producto era")
                    .containsExactly("id", "nombre", "productosElegibles",
                            "productosExpuestos", "exposiciones", "topProducto");
        }

        @Test
        @DisplayName("nada de esto se guarda: se calcula y se tira")
        void noSePersisteTelemetria() {
            /*
             * `metrica_descubrimiento` sigue exactamente como estaba. La
             * cobertura se calcula sobre el detalle vivo porque su denominador
             * —el catalogo elegible— cambia cada dia: congelarlo en una fila
             * historica daria una cifra que envejece mal y que nadie podria
             * reproducir despues.
             */
            long antes = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM catalogo.metrica_descubrimiento", Long.class);

            Long producto = crearProducto(catMonitores, null, "Monitor");
            servirVeces(producto, ModuloDescubrimiento.POPULARES, 3);
            cobertura.medir(desde, hasta, null);

            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM catalogo.metrica_descubrimiento", Long.class))
                    .isEqualTo(antes);
        }
    }

    /* ══════════════ Utilidades ══════════════ */

    /** Todos los componentes del informe y de los récords que cuelgan de él. */
    private List<java.lang.reflect.RecordComponent> camposDe(Class<?> raiz) {
        List<java.lang.reflect.RecordComponent> campos = new java.util.ArrayList<>();
        java.util.Deque<Class<?>> pendientes = new java.util.ArrayDeque<>(List.of(raiz));
        java.util.Set<Class<?>> vistos = new java.util.HashSet<>();

        while (!pendientes.isEmpty()) {
            Class<?> actual = pendientes.pop();
            if (!actual.isRecord() || !vistos.add(actual)) {
                continue;
            }
            for (java.lang.reflect.RecordComponent c : actual.getRecordComponents()) {
                campos.add(c);
                pendientes.push(c.getType());
                if (c.getGenericType() instanceof java.lang.reflect.ParameterizedType p) {
                    for (java.lang.reflect.Type arg : p.getActualTypeArguments()) {
                        if (arg instanceof Class<?> clase) {
                            pendientes.push(clase);
                        }
                    }
                }
            }
        }
        return campos;
    }

    private Faceta facetaDe(List<Faceta> facetas, Long id) {
        return facetas.stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow(
                () -> new AssertionError("no está la faceta " + id));
    }

    private Instant ahora() {
        return Instant.now();
    }

    private Long crearCategoria(String base) {
        String slug = base + "-" + UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.categoria (name, slug, description)"
                + " VALUES (?, ?, 'IT')", slug, slug);
        return jdbc.queryForObject("SELECT id FROM catalogo.categoria WHERE slug = ?",
                Long.class, slug);
    }

    private Long crearMarca(String base) {
        String nombre = base + "-" + UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.marca (name, descripcion) VALUES (?, 'IT')", nombre);
        return jdbc.queryForObject("SELECT id FROM catalogo.marca WHERE name = ?",
                Long.class, nombre);
    }

    private Long crearProducto(Long categoria, Long marca, String nombre) {
        String unico = nombre + " " + UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.producto"
                + " (name, description, precio, stock, categoria_id, marca_id,"
                + " estado_moderacion) VALUES (?, 'IT', ?, 10, ?, ?, 'APROBADO')",
                unico, new BigDecimal("100.00"), categoria, marca);
        return jdbc.queryForObject("SELECT id FROM catalogo.producto WHERE name = ?",
                Long.class, unico);
    }

    private UUID crearSujeto() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.sujeto (id) VALUES (?)", id);
        return id;
    }

    private void servirVeces(Long producto, ModuloDescubrimiento modulo, int veces) {
        for (int i = 0; i < veces; i++) {
            servir(producto, modulo, 0, ahora());
        }
    }

    private void servir(Long producto, ModuloDescubrimiento modulo, int posicion,
            Instant cuando) {
        jdbc.update("INSERT INTO catalogo.recomendacion_servida"
                + " (sujeto_id, item_tipo, item_id, modulo, razon, posicion, score,"
                + " ranker_version, con_perfil, servido_en)"
                + " VALUES (?, 'PRODUCTO', ?, ?, ?, ?, 1.0, 'prueba', false, ?)",
                sujeto, producto, modulo.name(), modulo.razonPorDefecto().name(),
                posicion, Timestamp.from(cuando));
    }

    private void mostrar(Long producto, ModuloDescubrimiento modulo, int posicion,
            Instant cuando) {
        jdbc.update("INSERT INTO catalogo.impresion"
                + " (sujeto_id, item_tipo, item_id, modulo, posicion, con_clic, mostrado_en)"
                + " VALUES (?, 'PRODUCTO', ?, ?, ?, false, ?)",
                sujeto, producto, modulo.name(), posicion, Timestamp.from(cuando));
    }
}
