package com.backend.catalogo.descubrimiento.negocio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
import com.backend.catalogo.descubrimiento.CooldownService;
import com.backend.catalogo.descubrimiento.ElegibilidadService;
import com.backend.catalogo.descubrimiento.ModuloDescubrimiento;
import com.backend.catalogo.descubrimiento.RecomendacionService;
import com.backend.catalogo.descubrimiento.SesionService;
import com.backend.catalogo.descubrimiento.cobertura.CoberturaService;
import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;
import com.backend.catalogo.descubrimiento.negocio.InformeNegocio.Categoria;
import com.backend.catalogo.descubrimiento.negocio.InformeNegocio.Evidencia;
import com.backend.catalogo.descubrimiento.negocio.InformeNegocio.Tasas;

/**
 * Qué ocurre después de exponer una categoría, y qué no se puede afirmar.
 *
 * <p>Estas pruebas defienden tres cosas por encima del resto. Que las etapas del
 * embudo no se colapsen: mil vistas con cero carritos tiene que seguir viéndose
 * como lo que es. Que un denominador cero NO se presente como un cero medido,
 * porque «no se sabe» y «cero por ciento» llevan a decisiones opuestas. Y que
 * nada de esto se lea como causalidad, que es la tentación de todo panel de
 * negocio.
 *
 * <p>NO es transaccional: el catálogo que crea tiene que verlo el SQL agregado.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Impacto de negocio por categoría")
class NegocioIT extends PruebaIntegracion {

    @Autowired
    private NegocioService negocio;

    @Autowired
    private CoberturaService cobertura;

    @Autowired
    private RecomendacionService recomendador;

    @Autowired
    private ElegibilidadService elegibilidad;

    @Autowired
    private CooldownService cooldowns;

    @Autowired
    private SesionService sesiones;

    @Autowired
    private PesosDescubrimiento pesos;

    @Autowired
    private JdbcTemplate jdbc;

    private Long catMonitores;
    private Long catImpresoras;
    private Long catVacia;
    private Long marca;
    private UUID sujeto;
    private Instant desde;
    private Instant hasta;
    private List<Long> apartados = List.of();

    @BeforeEach
    void prepararUnCatalogoPropio() {
        jdbc.update("DELETE FROM catalogo.recomendacion_servida");
        jdbc.update("DELETE FROM catalogo.impresion");
        jdbc.update("DELETE FROM catalogo.evento_interaccion");
        jdbc.update("DELETE FROM catalogo.perfil_faceta");
        jdbc.update("DELETE FROM catalogo.item_descartado");
        jdbc.update("DELETE FROM catalogo.sujeto");
        sujeto = crearSujeto();

        /*
         * El catalogo sembrado se aparta y se devuelve al terminar, anotando
         * CUALES. Restaurar «los que esten a cero» devolveria tambien los que ya
         * estaban agotados y esta clase acabaria agrandando el catalogo elegible
         * para las que corren despues.
         */
        apartados = jdbc.queryForList(
                "SELECT id FROM catalogo.producto WHERE stock > 0", Long.class);
        cambiarStock(apartados, 0);

        catMonitores = crearCategoria("neg-monitores");
        catImpresoras = crearCategoria("neg-impresoras");
        catVacia = crearCategoria("neg-sin-catalogo");
        marca = crearMarca("neg-marca");

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
        jdbc.update("DELETE FROM catalogo.marca WHERE id = ?", marca);
        cambiarStock(apartados, 10);
    }

    /* ══════════════ Categorías ══════════════ */

    @Nested
    @DisplayName("Categorías")
    class Categorias {

        @Test
        @DisplayName("una categoría con exposición trae su embudo")
        void categoriaConExposicion() {
            Long monitor = crearProducto(catMonitores, null, "Monitor");
            servir(monitor, ModuloDescubrimiento.POPULARES, 0, ahora());

            Categoria c = categoriaDe(negocio.medir(desde, hasta, null), catMonitores);

            assertThat(c.embudo().servidas()).isEqualTo(1);
            assertThat(c.productosElegibles()).isEqualTo(1);
        }

        @Test
        @DisplayName("una categoría elegible sin exposición sigue apareciendo")
        void categoriaSinExposicion() {
            /*
             * La propiedad heredada del bloque E. Si el universo fuera
             * `recomendacion_servida`, esta categoria no produciria ninguna fila
             * — y no producir ninguna fila es exactamente el problema que hay
             * que detectar.
             */
            Long monitor = crearProducto(catMonitores, null, "Monitor");
            crearProducto(catImpresoras, null, "Impresora");
            servir(monitor, ModuloDescubrimiento.POPULARES, 0, ahora());

            InformeNegocio informe = negocio.medir(desde, hasta, null);

            assertThat(informe.categoriasSinExposicion())
                    .extracting(Categoria::id)
                    .containsExactly(catImpresoras);
            assertThat(categoriaDe(informe, catImpresoras).evidencia(informe.minimoSujetos()))
                    .isEqualTo(Evidencia.SIN_EXPOSICION);
        }

        @Test
        @DisplayName("una categoría sin catálogo vivo no entra en el informe")
        void categoriaSinProductosElegibles() {
            crearProducto(catMonitores, null, "Monitor");

            assertThat(negocio.medir(desde, hasta, null).categorias())
                    .extracting(Categoria::id)
                    .as("no es un olvido del recomendador: no hay nada que recomendar")
                    .doesNotContain(catVacia);
        }

        @Test
        @DisplayName("un producto sin marca no rompe nada")
        void productoSinMarca() {
            Long sinMarca = crearProducto(catMonitores, null, "Monitor genérico");
            Long conMarca = crearProducto(catMonitores, marca, "Monitor de marca");

            servir(sinMarca, ModuloDescubrimiento.POPULARES, 0, ahora());
            servir(conMarca, ModuloDescubrimiento.POPULARES, 1, ahora());

            assertThat(categoriaDe(negocio.medir(desde, hasta, null), catMonitores)
                    .embudo().servidas()).isEqualTo(2);
        }

        @Test
        @DisplayName("varios productos de una categoría suman en la misma fila")
        void variosProductosDeUnaCategoria() {
            for (int i = 0; i < 4; i++) {
                servir(crearProducto(catMonitores, null, "Monitor " + i),
                        ModuloDescubrimiento.POPULARES, i, ahora());
            }
            Categoria c = categoriaDe(negocio.medir(desde, hasta, null), catMonitores);

            assertThat(c.embudo().servidas()).isEqualTo(4);
            assertThat(c.productosElegibles()).isEqualTo(4);
        }
    }

    /* ══════════════ Las cinco etapas ══════════════ */

    @Nested
    @DisplayName("Etapas del embudo")
    class Etapas {

        @Test
        @DisplayName("cada etapa cuenta lo suyo y ninguna contamina a la siguiente")
        void lasCincoEtapas() {
            Long producto = crearProducto(catMonitores, null, "Monitor");
            Instant cuando = ahora();

            servir(producto, ModuloDescubrimiento.POPULARES, 0, cuando);
            mostrar(producto, ModuloDescubrimiento.POPULARES, 0, cuando.plusSeconds(1));
            evento(producto, "ITEM_VIEW_DEEP", cuando.plusSeconds(2));
            evento(producto, "ADD_TO_CART", cuando.plusSeconds(3));
            evento(producto, "PURCHASE", cuando.plusSeconds(4));

            InformeNegocio.Embudo e =
                    categoriaDe(negocio.medir(desde, hasta, null), catMonitores).embudo();

            assertThat(e.servidas()).isEqualTo(1);
            assertThat(e.vistas()).isEqualTo(1);
            assertThat(e.clics())
                    .as("ITEM_VIEW_DEEP es también un clic, por definición de la fase 3")
                    .isEqualTo(1);
            assertThat(e.profundas()).isEqualTo(1);
            assertThat(e.carritos()).isEqualTo(1);
            assertThat(e.compras()).isEqualTo(1);
            assertThat(e.sujetos()).isEqualTo(1);
        }

        @Test
        @DisplayName("servido no se convierte en visto")
        void servidoNoEsVisto() {
            /*
             * Cien servidas y cinco vistas tiene que seguir diciendo eso. El
             * problema de una categoria asi esta en el carrusel —queda donde
             * nadie llega— y no en el producto; colapsarlas lo escondería.
             */
            Long producto = crearProducto(catMonitores, null, "Monitor del final");
            for (int i = 0; i < 10; i++) {
                servir(producto, ModuloDescubrimiento.POPULARES, 11, ahora());
            }

            InformeNegocio.Embudo e =
                    categoriaDe(negocio.medir(desde, hasta, null), catMonitores).embudo();

            assertThat(e.servidas()).isEqualTo(10);
            assertThat(e.vistas())
                    .as("el backend lo propuso diez veces; nadie llegó a desplazarse")
                    .isZero();
            assertThat(e.pctVisto())
                    .as("hay denominador, así que el cero es un cero medido")
                    .isZero();
        }

        @Test
        @DisplayName("una impresión confirma TODAS las servidas que la preceden en la ventana")
        void unaImpresionConfirmaVariasServidas() {
            /*
             * HALLAZGO, no capricho de la prueba: se hereda de la atribucion de
             * la fase 3 y aqui queda escrito para que nadie lo descubra mirando
             * un panel raro.
             *
             * `impresion` no guarda a que fila de `recomendacion_servida`
             * corresponde —la ingesta no lo manda— asi que el cruce es por
             * (sujeto, item, modulo, ventana). Si el mismo producto se sirve tres
             * veces al mismo sujeto en el mismo carrusel y este lo ve UNA, las
             * tres servidas quedan confirmadas.
             *
             * Consecuencia: `pctVisto` se infla cuando alguien recarga el Home.
             * Inventar aqui una identidad de impresion seria fabricar un dato que
             * la ingesta no garantiza, que es justo lo prohibido.
             */
            Long producto = crearProducto(catMonitores, null, "Monitor repetido");
            Instant cuando = ahora();

            servir(producto, ModuloDescubrimiento.POPULARES, 0, cuando);
            servir(producto, ModuloDescubrimiento.POPULARES, 0, cuando.plusSeconds(10));
            servir(producto, ModuloDescubrimiento.POPULARES, 0, cuando.plusSeconds(20));
            mostrar(producto, ModuloDescubrimiento.POPULARES, 0, cuando.plusSeconds(30));

            InformeNegocio.Embudo e =
                    categoriaDe(negocio.medir(desde, hasta, null), catMonitores).embudo();

            assertThat(e.servidas()).isEqualTo(3);
            assertThat(e.vistas())
                    .as("una sola impresión, y las tres servidas cuentan como vistas")
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("una categoría muy vista y sin negocio no parece un éxito")
        void muchasVistasYCeroNegocio() {
            /*
             * La vanity metric que este bloque existe para desmontar. La
             * categoria tiene vistas y clics de sobra; el embudo se corta en
             * seco al llegar al carrito, y eso queda a la vista en vez de
             * disolverse en un score.
             */
            List<UUID> gente = crearSujetos(pesos.getMinimoSujetos());
            Long producto = crearProducto(catMonitores, null, "Monitor llamativo");
            Instant cuando = ahora();

            for (UUID quien : gente) {
                servirA(quien, producto, ModuloDescubrimiento.POPULARES, 0, cuando);
                mostrarA(quien, producto, ModuloDescubrimiento.POPULARES, 0,
                        cuando.plusSeconds(1));
                eventoDe(quien, producto, "ITEM_VIEW", cuando.plusSeconds(2));
            }

            InformeNegocio informe = negocio.medir(desde, hasta, null);
            Categoria c = categoriaDe(informe, catMonitores);
            Tasas t = c.tasas(informe.minimoSujetos()).orElseThrow();

            assertThat(t.ctr()).isEqualTo(1.0);
            assertThat(t.carrito()).isZero();
            assertThat(t.compra()).isZero();
            assertThat(informe.conExposicionYSinNegocio())
                    .extracting(Categoria::id)
                    .as("mucha atención y ninguna acción de negocio: hay que verlo")
                    .containsExactly(catMonitores);
        }
    }

    /* ══════════════ Tasas y denominadores ══════════════ */

    @Nested
    @DisplayName("Tasas")
    class TasasYDenominadores {

        @Test
        @DisplayName("el CTR se divide por vistas, nunca por servidas")
        void elCtrSeDivideEntreVistas() {
            /*
             * La regla de la fase 3, y la diferencia se ve en el numero: con
             * cuatro servidas, dos vistas y un clic, dividir por servidas daria
             * 0,25 y dividir por vistas da 0,5. La primera mezcla «era buena la
             * recomendacion» con «llego el usuario a verla».
             */
            List<UUID> gente = crearSujetos(pesos.getMinimoSujetos());
            Long producto = crearProducto(catMonitores, null, "Monitor");
            Instant cuando = ahora();

            /*
             * A todos se les sirve; solo la MITAD llega a verlo. Asi los dos
             * denominadores difieren de verdad y la prueba puede distinguirlos —
             * con una impresion por cada servida coincidirian y no demostraria
             * nada, que es como estaba escrita al principio.
             */
            for (UUID quien : gente) {
                servirA(quien, producto, ModuloDescubrimiento.POPULARES, 0, cuando);
            }
            List<UUID> losQueVen = gente.subList(0, gente.size() / 2);
            for (UUID quien : losQueVen) {
                mostrarA(quien, producto, ModuloDescubrimiento.POPULARES, 0,
                        cuando.plusSeconds(1));
                eventoDe(quien, producto, "ITEM_VIEW", cuando.plusSeconds(2));
            }

            InformeNegocio informe = negocio.medir(desde, hasta, null);
            InformeNegocio.Embudo e = categoriaDe(informe, catMonitores).embudo();
            Tasas t = categoriaDe(informe, catMonitores)
                    .tasas(informe.minimoSujetos()).orElseThrow();

            assertThat(e.servidas()).isEqualTo(gente.size());
            assertThat(e.vistas()).isEqualTo(losQueVen.size());
            assertThat(t.ctr())
                    .as("clics sobre VISTAS: todo el que lo vio, lo pulsó")
                    .isEqualTo(1.0)
                    .isNotEqualTo((double) e.clics() / e.servidas());
            assertThat((double) e.clics() / e.servidas())
                    .as("dividir por servidas daría la mitad y mezclaría dos preguntas")
                    .isCloseTo(0.5, within(0.0001));
        }

        @Test
        @DisplayName("sin vistas no hay tasa: es NULL, no cero")
        void sinVistasLaTasaEsNula() {
            /*
             * La distincion que mas decisiones tuerce. Un 0 % dice «se midio y
             * nadie pulso»; un nulo dice «no hay denominador». Con lo primero se
             * retira una categoria del Home; con lo segundo se investiga por que
             * no llega a verse.
             */
            Long producto = crearProducto(catMonitores, null, "Monitor invisible");
            servir(producto, ModuloDescubrimiento.POPULARES, 11, ahora());

            InformeNegocio.Embudo e =
                    categoriaDe(negocio.medir(desde, hasta, null), catMonitores).embudo();

            assertThat(e.vistas()).isZero();
            assertThat(e.tasas().ctr()).isNull();
            assertThat(e.tasas().carrito()).isNull();
            assertThat(e.tasas().compra()).isNull();
            assertThat(e.pctVisto())
                    .as("esta sí tiene denominador: se sirvió una vez")
                    .isZero();
        }

        @Test
        @DisplayName("sin servidas tampoco hay porcentaje de visto")
        void sinServidasNoHayPorcentaje() {
            crearProducto(catImpresoras, null, "Impresora");

            InformeNegocio.Embudo e =
                    categoriaDe(negocio.medir(desde, hasta, null), catImpresoras).embudo();

            assertThat(e.servidas()).isZero();
            assertThat(e.pctVisto()).isNull();
        }

        @Test
        @DisplayName("la tasa de compra usa el mismo denominador que las demás")
        void laTasaDeCompraUsaVistas() {
            List<UUID> gente = crearSujetos(pesos.getMinimoSujetos());
            Long producto = crearProducto(catMonitores, null, "Monitor");
            Instant cuando = ahora();

            for (UUID quien : gente) {
                servirA(quien, producto, ModuloDescubrimiento.POPULARES, 0, cuando);
                mostrarA(quien, producto, ModuloDescubrimiento.POPULARES, 0,
                        cuando.plusSeconds(1));
            }
            for (UUID quien : gente.subList(0, 10)) {
                eventoDe(quien, producto, "PURCHASE", cuando.plusSeconds(2));
            }

            InformeNegocio informe = negocio.medir(desde, hasta, null);
            InformeNegocio.Embudo e = categoriaDe(informe, catMonitores).embudo();

            assertThat(e.compras()).isEqualTo(10);
            assertThat(e.tasas().compra())
                    .isCloseTo(10.0 / e.vistas(), within(0.0001));
        }
    }

    /* ══════════════ Evidencia mínima ══════════════ */

    @Nested
    @DisplayName("Mínimo de evidencia")
    class MinimoDeEvidencia {

        @Test
        @DisplayName("con poca gente detrás, la fila se marca insuficiente y no da tasas")
        void muestraInsuficiente() {
            /*
             * Una tasa sostenida por tres personas no describe un patron,
             * describe a esas tres personas. El minimo es el de la fase 3 y no
             * se rebaja: se reutiliza.
             */
            Long producto = crearProducto(catMonitores, null, "Monitor");
            Instant cuando = ahora();

            servir(producto, ModuloDescubrimiento.POPULARES, 0, cuando);
            mostrar(producto, ModuloDescubrimiento.POPULARES, 0, cuando.plusSeconds(1));
            evento(producto, "ITEM_VIEW", cuando.plusSeconds(2));

            InformeNegocio informe = negocio.medir(desde, hasta, null);
            Categoria c = categoriaDe(informe, catMonitores);

            assertThat(c.embudo().sujetos()).isEqualTo(1);
            assertThat(c.evidencia(informe.minimoSujetos())).isEqualTo(Evidencia.INSUFICIENTE);
            assertThat(c.tasas(informe.minimoSujetos()))
                    .as("las cuentas se ven; la tasa no se publica")
                    .isEmpty();
            assertThat(c.embudo().clics())
                    .as("pero el dato crudo sigue ahí, no se oculta")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("con gente suficiente, la tasa se puede calcular")
        void muestraSuficiente() {
            List<UUID> gente = crearSujetos(pesos.getMinimoSujetos());
            Long producto = crearProducto(catMonitores, null, "Monitor");
            Instant cuando = ahora();

            for (UUID quien : gente) {
                servirA(quien, producto, ModuloDescubrimiento.POPULARES, 0, cuando);
                mostrarA(quien, producto, ModuloDescubrimiento.POPULARES, 0,
                        cuando.plusSeconds(1));
            }

            InformeNegocio informe = negocio.medir(desde, hasta, null);
            Categoria c = categoriaDe(informe, catMonitores);

            assertThat(c.embudo().sujetos()).isEqualTo(pesos.getMinimoSujetos());
            assertThat(c.evidencia(informe.minimoSujetos())).isEqualTo(Evidencia.SUFICIENTE);
            assertThat(c.tasas(informe.minimoSujetos())).isPresent();
        }
    }

    /* ══════════════ Temporalidad ══════════════ */

    @Nested
    @DisplayName("Temporalidad")
    class Temporalidad {

        @Test
        @DisplayName("el inicio entra y el final no")
        void fronterasDelPeriodo() {
            Long dentro = crearProducto(catMonitores, null, "Monitor del inicio");
            Long fuera = crearProducto(catMonitores, null, "Monitor del final");

            servir(dentro, ModuloDescubrimiento.POPULARES, 0, desde);
            servir(fuera, ModuloDescubrimiento.POPULARES, 0, hasta);

            assertThat(categoriaDe(negocio.medir(desde, hasta, null), catMonitores)
                    .embudo().servidas()).isEqualTo(1);
        }

        @Test
        @DisplayName("dos ventanas consecutivas no cuentan dos veces lo mismo")
        void ventanasConsecutivasNoDuplican() {
            /*
             * Es la consecuencia de cerrar por arriba. Con las dos fronteras
             * cerradas, la exposicion del limite se contaria en los dos periodos
             * y la suma de los trozos seria mayor que el total.
             */
            Long producto = crearProducto(catMonitores, null, "Monitor");
            Instant medio = desde.plus(30, ChronoUnit.MINUTES);

            servir(producto, ModuloDescubrimiento.POPULARES, 0, desde);
            servir(producto, ModuloDescubrimiento.POPULARES, 0, medio);

            long primera = categoriaDe(negocio.medir(desde, medio, null), catMonitores)
                    .embudo().servidas();
            long segunda = categoriaDe(negocio.medir(medio, hasta, null), catMonitores)
                    .embudo().servidas();
            long entera = categoriaDe(negocio.medir(desde, hasta, null), catMonitores)
                    .embudo().servidas();

            assertThat(primera + segunda).isEqualTo(entera).isEqualTo(2);
        }

        @Test
        @DisplayName("un periodo del revés es un error, no un cero silencioso")
        void periodoInvalido() {
            assertThatThrownBy(() -> negocio.medir(hasta, desde, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> negocio.medir(desde, desde, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /* ══════════════ Atribución ══════════════ */

    @Nested
    @DisplayName("Atribución")
    class Atribucion {

        @Test
        @DisplayName("una acción dentro de la ventana se asocia")
        void dentroDeLaVentanaSeAsocia() {
            Long producto = crearProducto(catMonitores, null, "Monitor");
            Instant cuando = ahora();

            servir(producto, ModuloDescubrimiento.POPULARES, 0, cuando);
            mostrar(producto, ModuloDescubrimiento.POPULARES, 0, cuando.plusSeconds(1));
            evento(producto, "ADD_TO_CART", cuando.plusSeconds(60));

            assertThat(categoriaDe(negocio.medir(desde, hasta, null), catMonitores)
                    .embudo().carritos()).isEqualTo(1);
        }

        @Test
        @DisplayName("una acción ANTERIOR a la recomendación no se le atribuye")
        void loAnteriorNoSeAtribuye() {
            /*
             * Lo mas facil de colar en una atribucion por ventana y lo mas
             * enganoso: alguien que ya habia puesto el producto en el carrito
             * antes de que nadie se lo recomendara. El recomendador no puede
             * apuntarse eso.
             */
            Long producto = crearProducto(catMonitores, null, "Monitor");
            Instant cuando = ahora();

            evento(producto, "ADD_TO_CART", cuando.minusSeconds(600));
            servir(producto, ModuloDescubrimiento.POPULARES, 0, cuando);
            mostrar(producto, ModuloDescubrimiento.POPULARES, 0, cuando.plusSeconds(1));

            assertThat(categoriaDe(negocio.medir(desde, hasta, null), catMonitores)
                    .embudo().carritos()).isZero();
        }

        @Test
        @DisplayName("una acción pasada la ventana tampoco")
        void fueraDeLaVentanaNoSeAtribuye() {
            Long producto = crearProducto(catMonitores, null, "Monitor");
            Instant cuando = Instant.now().minus(50, ChronoUnit.HOURS);

            servir(producto, ModuloDescubrimiento.POPULARES, 0, cuando);
            evento(producto, "PURCHASE", cuando.plusSeconds(3600L * 25));

            InformeNegocio.Embudo e = categoriaDe(
                    negocio.medir(cuando.minusSeconds(60), Instant.now(), null),
                    catMonitores).embudo();

            assertThat(e.servidas()).isEqualTo(1);
            assertThat(e.compras())
                    .as("veinticinco horas después: fuera de la ventana de atribución")
                    .isZero();
        }

        @Test
        @DisplayName("una acción sobre OTRO producto no se atribuye a esta recomendación")
        void otroProductoNoCuenta() {
            /*
             * El limite honesto del modelo, dicho en una prueba. Ver una
             * recomendacion de A y comprar B no es un acierto de A. Lo que el
             * modelo NO puede distinguir —ver A, buscar A por el buscador y
             * comprarlo— se cuenta como asociado, y por eso todo esto se llama
             * asociacion y no causa.
             */
            Long recomendado = crearProducto(catMonitores, null, "Monitor recomendado");
            Long otro = crearProducto(catImpresoras, null, "Impresora buscada");
            Instant cuando = ahora();

            servir(recomendado, ModuloDescubrimiento.POPULARES, 0, cuando);
            mostrar(recomendado, ModuloDescubrimiento.POPULARES, 0, cuando.plusSeconds(1));
            evento(otro, "PURCHASE", cuando.plusSeconds(60));

            InformeNegocio informe = negocio.medir(desde, hasta, null);

            assertThat(categoriaDe(informe, catMonitores).embudo().compras()).isZero();
            assertThat(categoriaDe(informe, catImpresoras).embudo().servidas())
                    .as("la categoría del comprado nunca se sirvió")
                    .isZero();
        }

        @Test
        @DisplayName("la acción de otra persona no cuenta")
        void otroSujetoNoCuenta() {
            Long producto = crearProducto(catMonitores, null, "Monitor");
            UUID otraPersona = crearSujeto();
            Instant cuando = ahora();

            servir(producto, ModuloDescubrimiento.POPULARES, 0, cuando);
            eventoDe(otraPersona, producto, "ADD_TO_CART", cuando.plusSeconds(60));

            assertThat(categoriaDe(negocio.medir(desde, hasta, null), catMonitores)
                    .embudo().carritos()).isZero();
        }

        @Test
        @DisplayName("la ventana de atribución es la que ya existía, y viaja en el informe")
        void laVentanaEsLaDeSiempre() {
            /*
             * Va dentro del resultado a proposito: dos informes con ventanas
             * distintas no son comparables, y si la ventana no viaja con la
             * cifra nadie se entera de que estaba comparando peras con manzanas.
             */
            assertThat(negocio.medir(desde, hasta, null).horasAtribucion()).isEqualTo(24);
        }
    }

    /* ══════════════ Módulo, razón y banda ══════════════ */

    @Nested
    @DisplayName("Módulo, razón y banda")
    class DimensionesExistentes {

        @Test
        @DisplayName("la razón no se aplasta: cada generador conserva la suya")
        void lasRazonesNoSeMezclan() {
            Long uno = crearProducto(catMonitores, null, "Monitor por contenido");
            Long dos = crearProducto(catMonitores, null, "Monitor por co-visita");

            servirConRazon(uno, ModuloDescubrimiento.RELACIONADOS, "CONTENT_SIMILAR", 0);
            servirConRazon(dos, ModuloDescubrimiento.RELACIONADOS, "CO_VIEWED", 1);

            assertThat(negocio.medir(desde, hasta, null).razones())
                    .filteredOn(r -> r.categoriaId().equals(catMonitores))
                    .extracting(r -> r.razon().name())
                    .as("la ficha mezcla dos generadores y hay que poder separarlos")
                    .containsExactlyInAnyOrder("CONTENT_SIMILAR", "CO_VIEWED");
        }

        @Test
        @DisplayName("la banda es la de la fase 3")
        void laBandaEsLaDeSiempre() {
            Long producto = crearProducto(catMonitores, null, "Monitor");

            servir(producto, ModuloDescubrimiento.POPULARES, 0, ahora());
            servir(producto, ModuloDescubrimiento.POPULARES, 2, ahora());
            servir(producto, ModuloDescubrimiento.POPULARES, 3, ahora());

            assertThat(negocio.medir(desde, hasta, null).bandas())
                    .filteredOn(b -> b.categoriaId().equals(catMonitores))
                    .extracting(InformeNegocio.PorBanda::banda)
                    .containsExactly((short) 0, (short) 1);
        }

        @Test
        @DisplayName("una categoría que solo rinde arriba se delata por banda")
        void soloRindeArriba() {
            Long producto = crearProducto(catMonitores, null, "Monitor");
            Instant cuando = ahora();

            servir(producto, ModuloDescubrimiento.POPULARES, 0, cuando);
            mostrar(producto, ModuloDescubrimiento.POPULARES, 0, cuando.plusSeconds(1));
            evento(producto, "ITEM_VIEW", cuando.plusSeconds(2));
            servir(producto, ModuloDescubrimiento.POPULARES, 9, cuando.plusSeconds(10));

            List<InformeNegocio.PorBanda> bandas = negocio.medir(desde, hasta, null).bandas()
                    .stream().filter(b -> b.categoriaId().equals(catMonitores)).toList();

            assertThat(bandas.get(0).embudo().clics()).isEqualTo(1);
            assertThat(bandas.get(1).embudo().vistas())
                    .as("abajo no llega a verse siquiera")
                    .isZero();
        }

        @Test
        @DisplayName("se puede acotar a un módulo")
        void filtraPorModulo() {
            Long monitor = crearProducto(catMonitores, null, "Monitor");
            Long impresora = crearProducto(catImpresoras, null, "Impresora");

            servir(monitor, ModuloDescubrimiento.POPULARES, 0, ahora());
            servir(impresora, ModuloDescubrimiento.RELACIONADOS, 0, ahora());

            InformeNegocio soloPopulares =
                    negocio.medir(desde, hasta, ModuloDescubrimiento.POPULARES.name());

            assertThat(categoriaDe(soloPopulares, catMonitores).embudo().servidas())
                    .isEqualTo(1);
            assertThat(categoriaDe(soloPopulares, catImpresoras).embudo().servidas())
                    .isZero();
        }
    }

    /* ══════════════ Concentración ══════════════ */

    @Nested
    @DisplayName("Concentración de resultados")
    class ConcentracionDeResultados {

        @Test
        @DisplayName("dice dónde se acumulan los carritos, no quién los causó")
        void dondeSeAcumulan() {
            Long monitor = crearProducto(catMonitores, null, "Monitor");
            Long impresora = crearProducto(catImpresoras, null, "Impresora");
            Instant cuando = ahora();

            for (int i = 0; i < 9; i++) {
                UUID quien = crearSujeto();
                servirA(quien, monitor, ModuloDescubrimiento.POPULARES, 0, cuando);
                eventoDe(quien, monitor, "ADD_TO_CART", cuando.plusSeconds(5));
            }
            UUID unica = crearSujeto();
            servirA(unica, impresora, ModuloDescubrimiento.POPULARES, 0, cuando);
            eventoDe(unica, impresora, "ADD_TO_CART", cuando.plusSeconds(5));

            InformeNegocio.Reparto carritos =
                    negocio.medir(desde, hasta, null).concentracion().carritos();

            assertThat(carritos.total()).isEqualTo(10);
            assertThat(carritos.top1()).isCloseTo(0.9, within(0.001));
        }

        @Test
        @DisplayName("sin nada que repartir, la concentración es NULL y no cero")
        void sinNadaQueRepartir() {
            Long producto = crearProducto(catMonitores, null, "Monitor");
            servir(producto, ModuloDescubrimiento.POPULARES, 0, ahora());

            InformeNegocio.Concentracion c =
                    negocio.medir(desde, hasta, null).concentracion();

            assertThat(c.compras().total()).isZero();
            assertThat(c.compras().top1())
                    .as("no se midió una concentración nula: no hubo compras")
                    .isNull();
        }

        @Test
        @DisplayName("clics y carritos pueden concentrarse en categorías distintas")
        void etapasSeparadas() {
            /*
             * Por eso no hay un unico reparto. La categoria que se lleva la
             * atencion y la que se lleva el negocio pueden no ser la misma, y esa
             * diferencia es justo la que interesa.
             */
            Long monitor = crearProducto(catMonitores, null, "Monitor llamativo");
            Long impresora = crearProducto(catImpresoras, null, "Impresora útil");
            Instant cuando = ahora();

            for (int i = 0; i < 5; i++) {
                UUID quien = crearSujeto();
                servirA(quien, monitor, ModuloDescubrimiento.POPULARES, 0, cuando);
                eventoDe(quien, monitor, "ITEM_VIEW", cuando.plusSeconds(5));
            }
            UUID compradora = crearSujeto();
            servirA(compradora, impresora, ModuloDescubrimiento.POPULARES, 0, cuando);
            eventoDe(compradora, impresora, "ADD_TO_CART", cuando.plusSeconds(5));

            InformeNegocio informe = negocio.medir(desde, hasta, null);

            assertThat(categoriaDe(informe, catMonitores).embudo().clics()).isEqualTo(5);
            assertThat(categoriaDe(informe, catMonitores).embudo().carritos()).isZero();
            assertThat(categoriaDe(informe, catImpresoras).embudo().carritos()).isEqualTo(1);
            assertThat(informe.concentracion().carritos().top1()).isEqualTo(1.0);
        }
    }

    /* ══════════════ Privacidad ══════════════ */

    @Nested
    @DisplayName("Privacidad")
    class Privacidad {

        @Test
        @DisplayName("el informe no lleva sujeto, sesión ni ningún identificador de persona")
        void elInformeNoDelataANadie() {
            Long producto = crearProducto(catMonitores, null, "Monitor");
            Instant cuando = ahora();
            servir(producto, ModuloDescubrimiento.POPULARES, 0, cuando);
            mostrar(producto, ModuloDescubrimiento.POPULARES, 0, cuando.plusSeconds(1));
            evento(producto, "ADD_TO_CART", cuando.plusSeconds(2));

            InformeNegocio informe = negocio.medir(desde, hasta, null);

            assertThat(informe.toString()).doesNotContain(sujeto.toString());

            /*
             * Y la parte estructural, que es la que sostiene la afirmacion.
             * Que hoy no salga un UUID podria ser suerte del dato; que ningun
             * campo del arbol pueda contener uno es una propiedad.
             */
            assertThat(camposDe(InformeNegocio.class))
                    .as("ningún campo puede contener una identidad")
                    .noneMatch(c -> c.getType() == UUID.class)
                    .noneMatch(c -> {
                        String n = c.getName().toLowerCase();
                        return n.contains("sesion") || n.contains("usuario");
                    })
                    /*
                     * «sujetos» y «minimoSujetos» SI existen, y tienen que
                     * existir: son el piso de evidencia. Lo que hay que
                     * demostrar no es que la palabra no aparezca, sino que
                     * siempre es un NUMERO — de un long no se saca de quien era.
                     */
                    .filteredOn(c -> c.getName().toLowerCase().contains("sujeto"))
                    .isNotEmpty()
                    .allMatch(c -> c.getType() == long.class || c.getType() == int.class);
        }

        @Test
        @DisplayName("`sujetos` es una cuenta, no una lista")
        void sujetosEsUnaCuenta() {
            /*
             * Esta ahi para lo CONTRARIO de identificar: es el piso que permite
             * descartar una fila sostenida por cuatro personas. Es un `long`, y
             * de un long no se saca de quien era.
             */
            assertThat(InformeNegocio.Embudo.class.getRecordComponents())
                    .filteredOn(c -> c.getName().equals("sujetos"))
                    .singleElement()
                    .satisfies(c -> assertThat(c.getType()).isEqualTo(long.class));
        }

        @Test
        @DisplayName("ninguna consulta del informe lee sujeto para devolverlo")
        void lasConsultasNoProyectanSujeto() throws Exception {
            String fuente = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/backend/catalogo/descubrimiento/negocio",
                    "NegocioRepository.java"));

            assertThat(fuente)
                    .as("se usa para casar y para contar distintos, nunca se proyecta")
                    .doesNotContain("SELECT s.sujeto_id")
                    .doesNotContain("SELECT r.sujeto_id");
            assertThat(fuente).contains("COUNT(DISTINCT s.sujeto_id)");
        }
    }

    /* ══════════════ Rendimiento ══════════════ */

    @Nested
    @DisplayName("Rendimiento")
    class Rendimiento {

        @Test
        @DisplayName("el coste no crece con el número de categorías")
        void costeIndependienteDelCatalogo() {
            /*
             * Se mide contando consultas de verdad, no confiando en la lectura
             * del codigo. Con una categoria y con quince el numero tiene que ser
             * el mismo: tres. Si alguien mete un bucle con una consulta dentro,
             * esto falla.
             */
            Long producto = crearProducto(catMonitores, null, "Monitor");
            servir(producto, ModuloDescubrimiento.POPULARES, 0, ahora());

            long conUna = consultasDe(() -> negocio.medir(desde, hasta, null));

            List<Long> extra = new ArrayList<>();
            for (int i = 0; i < 14; i++) {
                Long otra = crearCategoria("neg-extra-" + i);
                extra.add(otra);
                Long suyo = crearProducto(otra, null, "Producto " + i);
                servir(suyo, ModuloDescubrimiento.POPULARES, i, ahora());
            }
            try {
                InformeNegocio informe = negocio.medir(desde, hasta, null);
                long conQuince = consultasDe(() -> negocio.medir(desde, hasta, null));

                assertThat(informe.categorias()).hasSize(15);
                assertThat(conQuince)
                        .as("tres agregaciones fijas, haya una categoría o quince")
                        .isEqualTo(conUna)
                        .isEqualTo(3);
            } finally {
                for (Long otra : extra) {
                    jdbc.update("DELETE FROM catalogo.recomendacion_servida WHERE item_id IN"
                            + " (SELECT id FROM catalogo.producto WHERE categoria_id = ?)",
                            otra);
                    jdbc.update("DELETE FROM catalogo.producto WHERE categoria_id = ?", otra);
                    jdbc.update("DELETE FROM catalogo.categoria WHERE id = ?", otra);
                }
            }
        }
    }

    /* ══════════════ Los bloques anteriores siguen en pie ══════════════ */

    @Nested
    @DisplayName("Regresión de A a E")
    class RegresionDeBloquesAnteriores {

        @Test
        @DisplayName("A · la elegibilidad sigue filtrando antes del ranker")
        void bloqueA() {
            Long vivo = crearProducto(catMonitores, null, "Monitor vivo");
            Long agotado = crearProducto(catMonitores, null, "Monitor agotado");
            jdbc.update("UPDATE catalogo.producto SET stock = 0 WHERE id = ?", agotado);

            assertThat(elegibilidad.filtrar(List.of(vivo, agotado)))
                    .containsExactly(vivo);
        }

        @Test
        @DisplayName("B · el catálogo nuevo sigue teniendo su puerta")
        void bloqueB() {
            Long nuevo = crearProducto(catMonitores, null, "Monitor recién llegado");
            jdbc.update("UPDATE catalogo.producto SET creado_en = now() WHERE id = ?", nuevo);
            crearProducto(catImpresoras, null, "Impresora vieja");

            assertThat(idsDe(recomendador.populares(sujeto, 12, new LinkedHashSet<>())))
                    .contains(nuevo);
        }

        @Test
        @DisplayName("C · la intención de sesión sigue deduciéndose")
        void bloqueC() {
            Long uno = crearProducto(catMonitores, null, "Monitor A");
            Long dos = crearProducto(catMonitores, null, "Monitor B");
            verEnSesion(uno);
            verEnSesion(dos);

            assertThat(sesiones.intencionDe(sujeto).categoriasDeSesion(3))
                    .containsExactly(catMonitores);
        }

        @Test
        @DisplayName("D · el cooldown sigue graduando y cortando")
        void bloqueD() {
            Long producto = crearProducto(catMonitores, null, "Monitor muy visto");
            for (int i = 0; i < pesos.getCooldownMaximo(); i++) {
                mostrar(producto, ModuloDescubrimiento.POPULARES, 0, ahora());
            }
            assertThat(cooldowns.de(sujeto).bloqueadosEn(ModuloDescubrimiento.POPULARES))
                    .contains(producto);
        }

        @Test
        @DisplayName("E · la cobertura sigue partiendo del catálogo elegible")
        void bloqueE() {
            Long monitor = crearProducto(catMonitores, null, "Monitor");
            crearProducto(catImpresoras, null, "Impresora");
            servir(monitor, ModuloDescubrimiento.POPULARES, 0, ahora());

            assertThat(cobertura.medir(desde, hasta, null).servido().categoriasSinExposicion())
                    .extracting(com.backend.catalogo.descubrimiento.cobertura
                            .InformeCobertura.Faceta::id)
                    .containsExactly(catImpresoras);
        }
    }

    /* ══════════════ Utilidades ══════════════ */

    private Categoria categoriaDe(InformeNegocio informe, Long id) {
        return informe.categorias().stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("no está la categoría " + id));
    }

    private List<Long> idsDe(
            com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.Carrusel carrusel) {
        return carrusel == null ? List.of()
                : carrusel.items().stream().map(p -> p.id()).toList();
    }

    /** Cuántas consultas dispara una operación, según las estadísticas de Hibernate. */
    private long consultasDe(Runnable operacion) {
        var sesion = entityManagerFactory().unwrap(
                org.hibernate.SessionFactory.class).getStatistics();
        sesion.setStatisticsEnabled(true);
        long antes = sesion.getQueryExecutionCount();
        operacion.run();
        return sesion.getQueryExecutionCount() - antes;
    }

    @Autowired
    private jakarta.persistence.EntityManagerFactory emf;

    private jakarta.persistence.EntityManagerFactory entityManagerFactory() {
        return emf;
    }

    private List<java.lang.reflect.RecordComponent> camposDe(Class<?> raiz) {
        List<java.lang.reflect.RecordComponent> campos = new ArrayList<>();
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

    private Instant ahora() {
        return Instant.now().minus(5, ChronoUnit.MINUTES);
    }

    private void cambiarStock(List<Long> ids, int stock) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.update("UPDATE catalogo.producto SET stock = " + stock + " WHERE id IN ("
                + ids.stream().map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(","))
                + ")");
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

    private Long crearProducto(Long categoria, Long marcaId, String nombre) {
        String unico = nombre + " " + UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.producto"
                + " (name, description, precio, stock, categoria_id, marca_id,"
                + " estado_moderacion) VALUES (?, 'IT', ?, 10, ?, ?, 'APROBADO')",
                unico, new BigDecimal("100.00"), categoria, marcaId);
        return jdbc.queryForObject("SELECT id FROM catalogo.producto WHERE name = ?",
                Long.class, unico);
    }

    private UUID crearSujeto() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.sujeto (id) VALUES (?)", id);
        return id;
    }

    private List<UUID> crearSujetos(int cuantos) {
        List<UUID> gente = new ArrayList<>(cuantos);
        for (int i = 0; i < cuantos; i++) {
            gente.add(crearSujeto());
        }
        return gente;
    }

    private void servir(Long producto, ModuloDescubrimiento modulo, int posicion,
            Instant cuando) {
        servirA(sujeto, producto, modulo, posicion, cuando);
    }

    private void servirA(UUID quien, Long producto, ModuloDescubrimiento modulo,
            int posicion, Instant cuando) {
        jdbc.update("INSERT INTO catalogo.recomendacion_servida"
                + " (sujeto_id, item_tipo, item_id, modulo, razon, posicion, score,"
                + " ranker_version, con_perfil, servido_en)"
                + " VALUES (?, 'PRODUCTO', ?, ?, ?, ?, 1.0, 'prueba', false, ?)",
                quien, producto, modulo.name(), modulo.razonPorDefecto().name(),
                posicion, Timestamp.from(cuando));
    }

    private void servirConRazon(Long producto, ModuloDescubrimiento modulo, String razon,
            int posicion) {
        jdbc.update("INSERT INTO catalogo.recomendacion_servida"
                + " (sujeto_id, item_tipo, item_id, modulo, razon, posicion, score,"
                + " ranker_version, con_perfil, servido_en)"
                + " VALUES (?, 'PRODUCTO', ?, ?, ?, ?, 1.0, 'prueba', false, ?)",
                sujeto, producto, modulo.name(), razon, posicion,
                Timestamp.from(ahora()));
    }

    private void mostrar(Long producto, ModuloDescubrimiento modulo, int posicion,
            Instant cuando) {
        mostrarA(sujeto, producto, modulo, posicion, cuando);
    }

    private void mostrarA(UUID quien, Long producto, ModuloDescubrimiento modulo,
            int posicion, Instant cuando) {
        jdbc.update("INSERT INTO catalogo.impresion"
                + " (sujeto_id, item_tipo, item_id, modulo, posicion, con_clic, mostrado_en)"
                + " VALUES (?, 'PRODUCTO', ?, ?, ?, false, ?)",
                quien, producto, modulo.name(), posicion, Timestamp.from(cuando));
    }

    private void evento(Long producto, String tipo, Instant cuando) {
        eventoDe(sujeto, producto, tipo, cuando);
    }

    private void eventoDe(UUID quien, Long producto, String tipo, Instant cuando) {
        jdbc.update("INSERT INTO catalogo.evento_interaccion"
                + " (sujeto_id, tipo, item_tipo, item_id, ocurrido_en)"
                + " VALUES (?, ?, 'PRODUCTO', ?, ?)",
                quien, tipo, producto, Timestamp.from(cuando));
    }

    private void verEnSesion(Long producto) {
        jdbc.update("INSERT INTO catalogo.evento_interaccion"
                + " (sujeto_id, sesion_id, tipo, item_tipo, item_id, ocurrido_en)"
                + " VALUES (?, ?, 'ITEM_VIEW', 'PRODUCTO', ?, ?)",
                sujeto, sujeto, producto, Timestamp.from(Instant.now()));
    }
}
