package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import com.backend.catalogo.descubrimiento.CooldownService.Cooldown;
import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.Carrusel;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Dejar de insistir poco a poco, y volver solo.
 *
 * <p>Lo que se comprueba aquí no es una fórmula —eso está en {@code
 * CooldownTest}— sino las consecuencias visibles: que la exposición descuenta en
 * vez de borrar, que el corte existe y se nota, que lo que se enseñó en un
 * carrusel no castiga igual en otro, que un clic perdona, y que el producto
 * vuelve solo cuando la ventana corre sin que nadie ejecute nada.
 *
 * <p>Y la parte que importa más que todas: que lo enfriado no llega al ranker.
 * Un candidato excluido que reaparece por la puerta del ranking sería peor que
 * no tener enfriamiento, porque parecería que funciona.
 *
 * <p>NO es transaccional: el registro de lo servido escribe en transacción
 * propia. Por eso la limpieza es explícita.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Enfriamiento graduado")
class CooldownIT extends PruebaIntegracion {

    @Autowired
    private CooldownService enfriamientos;

    @Autowired
    private RecomendacionService recomendador;

    @Autowired
    private RecomendacionServidaRepository servidas;

    @Autowired
    private PesosDescubrimiento pesos;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MeterRegistry registro;

    private Long categoria;
    private UUID sujeto;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM catalogo.recomendacion_servida");
        jdbc.update("DELETE FROM catalogo.item_descartado");
        jdbc.update("DELETE FROM catalogo.impresion");
        jdbc.update("DELETE FROM catalogo.evento_interaccion");
        jdbc.update("DELETE FROM catalogo.perfil_faceta");
        jdbc.update("DELETE FROM catalogo.sujeto");
        categoria = crearCategoria("cooldown-" + UUID.randomUUID());
        sujeto = crearSujeto();
    }

    /*
     * Los productos de esta clase se borran al terminar.
     *
     * No es orden por gusto: estas pruebas no son transaccionales, y un producto
     * que se queda vivo engorda el catalogo elegible, que es el denominador de
     * la cobertura en `EvaluacionOfflineIT`. Dejarlos hacia fallar a otra clase
     * por un umbral, sin que el recomendador hubiera cambiado nada.
     */
    @AfterEach
    void devolverElCatalogoComoEstaba() {
        jdbc.update("DELETE FROM catalogo.recomendacion_servida WHERE item_id IN"
                + " (SELECT id FROM catalogo.producto WHERE categoria_id = ?)", categoria);
        jdbc.update("DELETE FROM catalogo.producto WHERE categoria_id = ?", categoria);
        jdbc.update("DELETE FROM catalogo.categoria WHERE id = ?", categoria);
    }

    /* ══════════════ Las tres fronteras ══════════════ */

    @Nested
    @DisplayName("Fronteras")
    class Fronteras {

        @Test
        @DisplayName("sin impresiones el candidato conserva su score")
        void sinImpresionesNoSeToca() {
            Long producto = crearProducto("Nunca mostrado");

            Cooldown enfriamiento = enfriamientos.de(sujeto);

            assertThat(enfriamiento.factor(ModuloDescubrimiento.POPULARES, producto))
                    .as("no se le ha enseñado nada: no hay nada que descontar")
                    .isEqualTo(1.0);
            assertThat(enfriamiento.bloqueadosEn(ModuloDescubrimiento.POPULARES)).isEmpty();
        }

        @Test
        @DisplayName("más exposición reciente, más castigo, y siempre en ese orden")
        void masExposicionMasCastigo() {
            Long uno = crearProducto("Visto una vez");
            Long dos = crearProducto("Visto dos veces");
            Long tres = crearProducto("Visto tres veces");

            mostrar(uno, ModuloDescubrimiento.POPULARES, 1);
            mostrar(dos, ModuloDescubrimiento.POPULARES, 2);
            mostrar(tres, ModuloDescubrimiento.POPULARES, 3);

            Cooldown enfriamiento = enfriamientos.de(sujeto);
            double sinNada = enfriamiento.factor(ModuloDescubrimiento.POPULARES, -1L);

            assertThat(enfriamiento.factor(ModuloDescubrimiento.POPULARES, uno))
                    .isLessThan(sinNada);
            assertThat(enfriamiento.factor(ModuloDescubrimiento.POPULARES, dos))
                    .isLessThan(enfriamiento.factor(ModuloDescubrimiento.POPULARES, uno));
            assertThat(enfriamiento.factor(ModuloDescubrimiento.POPULARES, tres))
                    .isLessThan(enfriamiento.factor(ModuloDescubrimiento.POPULARES, dos));
        }

        @Test
        @DisplayName("tres impresiones penalizan pero todavía no borran")
        void tresTodaviaNoBorran() {
            /*
             * Es la diferencia con la regla anterior, dicha en una prueba: con
             * tres impresiones el producto desaparecia dos semanas. Ahora sigue
             * ahi, valiendo la mitad, y le toca al ranking decidir.
             */
            Long producto = crearProducto("Visto tres veces");
            mostrar(producto, ModuloDescubrimiento.POPULARES, 3);

            Cooldown enfriamiento = enfriamientos.de(sujeto);

            assertThat(enfriamiento.bloqueadosEn(ModuloDescubrimiento.POPULARES))
                    .doesNotContain(producto);
            assertThat(enfriamiento.factor(ModuloDescubrimiento.POPULARES, producto))
                    .isLessThan(1.0);
        }

        @Test
        @DisplayName("alcanzado el corte, el producto sale de ese carrusel")
        void alcanzadoElCorteSeVa() {
            Long producto = crearProducto("Visto hasta el hartazgo");
            mostrar(producto, ModuloDescubrimiento.POPULARES, (int) pesos.getCooldownMaximo());

            Cooldown enfriamiento = enfriamientos.de(sujeto);

            assertThat(enfriamiento.bloqueadosEn(ModuloDescubrimiento.POPULARES))
                    .as("se le enseñó de sobra y nunca lo tocó")
                    .contains(producto);
            /*
             * Excluido, no penalizado: son listas distintas y esto lo dice. Un
             * producto que estuviera en las dos seria un producto al que el
             * ranker todavia podria mirar, y el corte dejaria de ser un corte.
             *
             * La comprobacion de que ademas no sale en el carrusel esta en
             * `laFichaTieneSuCorte` y en `noLlegaAlRegistro`, que la hacen
             * contra un control. Afirmarla aqui sobre «lo mas popular» seria
             * una ausencia que tambien se explicaria por no haber ganado un
             * hueco entre setenta productos.
             */
            assertThat(enfriamiento.factor(ModuloDescubrimiento.POPULARES, producto))
                    .isEqualTo(1.0);
        }
    }

    /* ══════════════ Temporalidad ══════════════ */

    @Nested
    @DisplayName("Temporalidad")
    class Temporalidad {

        @Test
        @DisplayName("dentro de la ventana, cuenta")
        void dentroDeLaVentanaCuenta() {
            Long producto = crearProducto("Visto anteayer");
            mostrarEn(producto, ModuloDescubrimiento.POPULARES,
                    (int) pesos.getCooldownMaximo(), haceDias(2));

            assertThat(enfriamientos.de(sujeto).bloqueadosEn(ModuloDescubrimiento.POPULARES))
                    .contains(producto);
        }

        @Test
        @DisplayName("justo fuera de la ventana, ya no cuenta")
        void fueraDeLaVentanaNoCuenta() {
            Long producto = crearProducto("Visto hace demasiado");
            mostrarEn(producto, ModuloDescubrimiento.POPULARES,
                    (int) pesos.getCooldownMaximo(),
                    haceDias(pesos.getDiasSupresionPorFatiga() + 1));

            Cooldown enfriamiento = enfriamientos.de(sujeto);

            assertThat(enfriamiento.bloqueadosEn(ModuloDescubrimiento.POPULARES))
                    .as("la memoria del enfriamiento dura lo que dura la ventana")
                    .doesNotContain(producto);
            assertThat(enfriamiento.factor(ModuloDescubrimiento.POPULARES, producto))
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("una impresión con fecha futura no enfría nada")
        void elFuturoNoCuenta() {
            /*
             * Las fechas las pone el servidor, asi que esto no deberia pasar. Se
             * comprueba igual: una importacion, una restauracion o un reloj
             * torcido no pueden fabricar un castigo que nadie se ha ganado, y
             * el corte del futuro es una linea de SQL que es facil perder en un
             * refactor.
             */
            Long producto = crearProducto("Mostrado mañana");
            mostrarEn(producto, ModuloDescubrimiento.POPULARES,
                    (int) pesos.getCooldownMaximo(),
                    Instant.now().plus(1, ChronoUnit.DAYS));

            Cooldown enfriamiento = enfriamientos.de(sujeto);

            assertThat(enfriamiento.bloqueadosEn(ModuloDescubrimiento.POPULARES))
                    .doesNotContain(producto);
            assertThat(enfriamiento.factor(ModuloDescubrimiento.POPULARES, producto))
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("la recuperación ocurre sola: la ventana se desliza")
        void laRecuperacionEsAutomatica() {
            /*
             * La secuencia completa, que es el argumento de que no haga falta
             * ninguna tarea de «desfatigado»: primero dentro de la ventana y
             * bloqueado, despues las mismas impresiones envejecidas y de vuelta
             * a competir. Entre las dos no se ejecuta NADA.
             */
            /*
             * La vuelta se comprueba en la ficha de producto y no en «lo mas
             * popular», y la razon es de diseno de la prueba, no del sistema.
             * «Lo mas popular» compite contra el catalogo entero —setenta y pico
             * productos sembrados— y quedarse fuera de doce huecos no demuestra
             * nada sobre el enfriamiento. La ficha solo propone hermanos de
             * categoria, que aqui son los tres de esta prueba: si el producto no
             * vuelve ahi, es porque sigue enfriado.
             */
            Long visto = crearProducto("El que se mira");
            Long producto = crearProducto("Se recupera solo");
            crearProducto("Hermano cualquiera");
            mostrarEn(producto, ModuloDescubrimiento.RELACIONADOS,
                    (int) pesos.getCooldownMaximo(), haceDias(1));

            assertThat(enfriamientos.de(sujeto).bloqueadosEn(ModuloDescubrimiento.RELACIONADOS))
                    .as("recién enseñado de sobra: fuera")
                    .contains(producto);
            assertThat(idsDe(recomendador.similares(visto, sujeto, 12)))
                    .doesNotContain(producto);

            // Lo unico que cambia es el paso del tiempo. No se ejecuta nada.
            jdbc.update("UPDATE catalogo.impresion SET mostrado_en = ? WHERE item_id = ?",
                    Timestamp.from(haceDias(pesos.getDiasSupresionPorFatiga() + 2)), producto);

            Cooldown despues = enfriamientos.de(sujeto);

            assertThat(despues.bloqueadosEn(ModuloDescubrimiento.RELACIONADOS))
                    .as("la impresión salió de la ventana y el contador bajó solo")
                    .doesNotContain(producto);
            assertThat(despues.factor(ModuloDescubrimiento.RELACIONADOS, producto))
                    .as("y vuelve con su score intacto, no a medias")
                    .isEqualTo(1.0);
            assertThat(idsDe(recomendador.similares(visto, sujeto, 12)))
                    .as("de vuelta en el carrusel sin que nadie haya ejecutado nada")
                    .contains(producto);
        }

        @Test
        @DisplayName("impresiones antiguas no suman con las recientes")
        void lasAntiguasNoSuman() {
            Long producto = crearProducto("Mitad viejo, mitad nuevo");
            mostrarEn(producto, ModuloDescubrimiento.POPULARES, 5,
                    haceDias(pesos.getDiasSupresionPorFatiga() + 3));
            mostrarEn(producto, ModuloDescubrimiento.POPULARES, 2, haceDias(1));

            assertThat(enfriamientos.de(sujeto).bloqueadosEn(ModuloDescubrimiento.POPULARES))
                    .as("cinco viejas más dos nuevas no son siete")
                    .doesNotContain(producto);
        }
    }

    /* ══════════════ Módulos ══════════════ */

    @Nested
    @DisplayName("Módulos")
    class Modulos {

        @Test
        @DisplayName("lo que cansa en un carrusel no se borra del resto de la pantalla")
        void elCorteEsPorModulo() {
            Long producto = crearProducto("Muy visto en relacionados");
            mostrar(producto, ModuloDescubrimiento.RELACIONADOS,
                    (int) pesos.getCooldownMaximo());

            Cooldown enfriamiento = enfriamientos.de(sujeto);

            assertThat(enfriamiento.bloqueadosEn(ModuloDescubrimiento.RELACIONADOS))
                    .contains(producto);
            assertThat(enfriamiento.bloqueadosEn(ModuloDescubrimiento.POPULARES))
                    .as("en «lo más popular» no ha salido nunca: ahí no está gastado")
                    .doesNotContain(producto);
        }

        @Test
        @DisplayName("pero la exposición ajena sí pesa un poco")
        void laExposicionAjenaPesaUnPoco() {
            /*
             * La mitad que suele olvidarse. Si los modulos se ignorasen del
             * todo, un mismo producto podria perseguir a alguien por toda la
             * pantalla sin que ningun contador se enterase.
             */
            Long producto = crearProducto("Muy visto en otro sitio");
            mostrar(producto, ModuloDescubrimiento.RELACIONADOS,
                    (int) pesos.getCooldownMaximo());

            assertThat(enfriamientos.de(sujeto)
                    .factor(ModuloDescubrimiento.POPULARES, producto))
                    .isLessThan(1.0);
        }

        @Test
        @DisplayName("«no me interesa» sí borra de todos los carruseles")
        void elDescarteSiEsGlobal() {
            Long producto = crearProducto("Descartado a mano");
            jdbc.update("INSERT INTO catalogo.item_descartado"
                    + " (sujeto_id, item_tipo, item_id, motivo) VALUES (?, 'PRODUCTO', ?, ?)",
                    sujeto, producto, "NOT_INTERESTED");

            assertThat(idsDe(recomendador.populares(sujeto, 12, new LinkedHashSet<>())))
                    .doesNotContain(producto);
            assertThat(idsDe(recomendador.similares(crearProducto("Otro"), sujeto, 12)))
                    .as("graduar la fatiga no puede ablandar el descarte")
                    .doesNotContain(producto);
        }

        @Test
        @DisplayName("la ficha de producto respeta su propio corte")
        void laFichaTieneSuCorte() {
            Long visto = crearProducto("El que se mira");
            Long cansado = crearProducto("Hermano muy visto");
            crearProducto("Hermano fresco");

            /*
             * CONTROL PRIMERO, y no es ceremonia.
             *
             * Sin el, «no aparece» tambien se explicaria porque el producto
             * nunca habria aparecido. Es el fallo que costo una vuelta en el
             * bloque B: dos pruebas que pasaban sobre listas vacias.
             */
            assertThat(idsDe(recomendador.similares(visto, sujeto, 12)))
                    .as("antes de enfriarlo, este producto sí sale en la ficha")
                    .contains(cansado);

            mostrar(cansado, ModuloDescubrimiento.RELACIONADOS,
                    (int) pesos.getCooldownMaximo());

            assertThat(idsDe(recomendador.similares(visto, sujeto, 12)))
                    .as("las dos superficies comparten la lógica de exclusión")
                    .doesNotContain(cansado);
        }
    }

    /* ══════════════ Acción positiva ══════════════ */

    @Nested
    @DisplayName("Acción positiva")
    class AccionPositiva {

        @Test
        @DisplayName("si lo tocó, no se le castiga por habérselo enseñado")
        void elClicPerdona() {
            /*
             * `con_clic` no es semantica nueva: lo pone la ingesta cuando llega
             * cualquier evento que no sea un descarte desde una superficie de
             * recomendacion, y la regla anterior ya exigia cero clics para
             * bloquear. Se conserva tal cual.
             */
            Long producto = crearProducto("Visto mucho y pulsado");
            mostrar(producto, ModuloDescubrimiento.POPULARES,
                    (int) pesos.getCooldownMaximo());
            jdbc.update("UPDATE catalogo.impresion SET con_clic = TRUE"
                    + " WHERE item_id = ? AND id = (SELECT MIN(id) FROM catalogo.impresion"
                    + " WHERE item_id = ?)", producto, producto);

            Cooldown enfriamiento = enfriamientos.de(sujeto);

            assertThat(enfriamiento.bloqueadosEn(ModuloDescubrimiento.POPULARES))
                    .as("lo abrió: enseñárselo otra vez no es insistir, es acertar")
                    .doesNotContain(producto);
            assertThat(enfriamiento.factor(ModuloDescubrimiento.POPULARES, producto))
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("el clic perdona el producto en todos sus carruseles")
        void elClicPerdonaEnTodaLaPantalla() {
            /*
             * Es consecuencia de como se marca un clic hoy: `marcarClic` cierra
             * la impresion mas reciente sin clic del item, sin mirar el modulo
             * —el evento de entrada trae un `origen` de texto libre, no el
             * modulo—. Inventar aqui una atribucion por modulo que la ingesta no
             * garantiza habria sido construir sobre un dato que no existe.
             */
            Long producto = crearProducto("Pulsado en un sitio, visto en dos");
            mostrar(producto, ModuloDescubrimiento.POPULARES,
                    (int) pesos.getCooldownMaximo());
            mostrar(producto, ModuloDescubrimiento.RELACIONADOS,
                    (int) pesos.getCooldownMaximo());
            jdbc.update("UPDATE catalogo.impresion SET con_clic = TRUE"
                    + " WHERE id = (SELECT MIN(id) FROM catalogo.impresion WHERE item_id = ?"
                    + " AND modulo = 'POPULARES')", producto);

            Cooldown enfriamiento = enfriamientos.de(sujeto);

            assertThat(enfriamiento.bloqueadosEn(ModuloDescubrimiento.POPULARES))
                    .doesNotContain(producto);
            assertThat(enfriamiento.bloqueadosEn(ModuloDescubrimiento.RELACIONADOS))
                    .doesNotContain(producto);
        }

        @Test
        @DisplayName("sin tocarlo, la repetición sí acaba apagándolo")
        void sinAccionPositivaSeApaga() {
            Long producto = crearProducto("Visto mucho y nunca pulsado");
            mostrar(producto, ModuloDescubrimiento.POPULARES,
                    (int) pesos.getCooldownMaximo());

            assertThat(enfriamientos.de(sujeto).bloqueadosEn(ModuloDescubrimiento.POPULARES))
                    .contains(producto);
        }
    }

    /* ══════════════ Seguridad del pipeline ══════════════ */

    @Nested
    @DisplayName("El ranker no puede deshacerlo")
    class SeguridadDelPipeline {

        @Test
        @DisplayName("lo enfriado del todo ni siquiera se anota como servido")
        void noLlegaAlRegistro() {
            /*
             * La prueba que de verdad cierra la puerta. Que no aparezca en el
             * carrusel podria explicarse por el recorte o por la diversidad;
             * que no exista en `recomendacion_servida` solo puede explicarse
             * porque nunca fue candidato — es decir, porque la exclusion se
             * aplico ANTES y no despues del ranking.
             */
            Long visto = crearProducto("El que se mira");
            Long producto = crearProducto("Fuera antes de empezar");
            crearProducto("Hermano fresco");

            // Control: sin enfriar, este producto se sirve y queda anotado.
            recomendador.similares(visto, sujeto, 12);
            assertThat(servidas.findAll()).extracting(RecomendacionServida::getItemId)
                    .contains(producto);

            jdbc.update("DELETE FROM catalogo.recomendacion_servida");
            mostrar(producto, ModuloDescubrimiento.RELACIONADOS,
                    (int) pesos.getCooldownMaximo());

            recomendador.similares(visto, sujeto, 12);

            assertThat(servidas.findAll()).extracting(RecomendacionServida::getItemId)
                    .as("no llegó a ser candidato, así que el ranker no pudo devolverlo")
                    .doesNotContain(producto);
        }

        @Test
        @DisplayName("un producto propuesto por dos generadores se enfría una vez")
        void sinDobleCastigoPorDuplicado() {
            /*
             * En el Home un mismo producto puede salir de varios generadores. El
             * enfriamiento se calcula una vez por peticion y se consulta por
             * item, asi que proponerlo dos veces no lo castiga dos veces. Lo que
             * se comprueba es la propiedad: dos lecturas del mismo objeto dan lo
             * mismo, y el Home no lo repite.
             */
            Long producto = crearProducto("Propuesto por todos");
            mostrar(producto, ModuloDescubrimiento.POPULARES, 2);

            Cooldown enfriamiento = enfriamientos.de(sujeto);
            double primera = enfriamiento.factor(ModuloDescubrimiento.POPULARES, producto);
            double segunda = enfriamiento.factor(ModuloDescubrimiento.POPULARES, producto);

            assertThat(segunda).isEqualTo(primera);

            List<Long> home = recomendador.home(sujeto, null, 12).stream()
                    .flatMap(c -> c.items().stream())
                    .map(p -> p.id())
                    .toList();
            assertThat(home).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("la misma entrada produce el mismo enfriamiento")
        void esIdempotente() {
            Long producto = crearProducto("Estable");
            mostrar(producto, ModuloDescubrimiento.POPULARES, 3);

            for (ModuloDescubrimiento modulo : ModuloDescubrimiento.values()) {
                assertThat(enfriamientos.de(sujeto).factor(modulo, producto))
                        .isEqualTo(enfriamientos.de(sujeto).factor(modulo, producto));
            }
        }

        @Test
        @DisplayName("un Home entero cuesta UN cálculo de enfriamiento, no seis")
        void unaSolaConsultaPorPantalla() {
            /*
             * La afirmacion de rendimiento, medida en vez de prometida. El Home
             * arma seis carruseles; si cada uno calculara lo suyo serian seis
             * consultas, y preguntarlo por candidato serian cientos. El
             * cronometro cuenta ejecuciones, asi que esto es exactamente «una
             * consulta por peticion» y falla el dia que alguien mueva el calculo
             * dentro de un bucle.
             */
            crearProducto("Uno cualquiera");
            Timer cronometro = registro.find(CooldownService.TIEMPO).timer();
            long antes = cronometro == null ? 0 : cronometro.count();

            recomendador.home(sujeto, null, 12);

            assertThat(registro.find(CooldownService.TIEMPO).timer().count() - antes)
                    .as("uno por pantalla: ni por carrusel ni por candidato")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("las métricas no llevan ni un identificador")
        void lasMetricasNoDelatanANadie() {
            /*
             * Se comprueba aqui y no contra `/actuator/prometheus` porque ese
             * endpoint no esta expuesto en la imagen. Da igual: lo que hay que
             * demostrar es que las etiquetas son un conjunto cerrado y pequeno,
             * y eso se ve mejor en el registro que en el texto que produce.
             *
             * Una metrica etiquetada por producto o por sujeto hace dos danos a
             * la vez: delata conducta de una persona concreta y revienta la
             * cardinalidad de Prometheus, que es como se tumba el sistema de
             * monitorizacion sin querer.
             */
            Long producto = crearProducto("Enfriado y contado");
            mostrar(producto, ModuloDescubrimiento.POPULARES,
                    (int) pesos.getCooldownMaximo());
            enfriamientos.de(sujeto);

            List<String> etiquetas = registro.getMeters().stream()
                    .filter(m -> m.getId().getName().startsWith(
                            "smartzone_descubrimiento_cooldown")
                            || m.getId().getName().startsWith(
                                    "smartzone_descubrimiento_enfriados"))
                    .flatMap(m -> m.getId().getTags().stream())
                    .map(t -> t.getKey() + "=" + t.getValue())
                    .toList();

            assertThat(etiquetas)
                    .as("la única etiqueta propia es el módulo; «aplicacion» la pone"
                            + " el registro a todas las métricas del servicio")
                    .isNotEmpty()
                    .allMatch(e -> e.startsWith("modulo=") || e.startsWith("aplicacion="))
                    .noneMatch(e -> e.contains(sujeto.toString()))
                    .noneMatch(e -> e.contains(String.valueOf(producto)));
        }

        @Test
        @DisplayName("sin sujeto no hay enfriamiento que calcular")
        void sinSujetoNoHayNada() {
            Cooldown enfriamiento = enfriamientos.de(null);

            assertThat(enfriamiento.factor(ModuloDescubrimiento.POPULARES, 1L)).isEqualTo(1.0);
            assertThat(enfriamiento.bloqueadosEn(ModuloDescubrimiento.POPULARES)).isEmpty();
        }
    }

    /* ══════════════ Utilidades ══════════════ */

    private List<Long> idsDe(Carrusel carrusel) {
        return carrusel == null ? List.of() : carrusel.items().stream().map(p -> p.id()).toList();
    }

    private Instant haceDias(int dias) {
        return Instant.now().minus(dias, ChronoUnit.DAYS);
    }

    private void mostrar(Long producto, ModuloDescubrimiento modulo, int veces) {
        mostrarEn(producto, modulo, veces, Instant.now().minus(1, ChronoUnit.HOURS));
    }

    private void mostrarEn(Long producto, ModuloDescubrimiento modulo, int veces,
            Instant cuando) {
        for (int i = 0; i < veces; i++) {
            jdbc.update("INSERT INTO catalogo.impresion"
                    + " (sujeto_id, item_tipo, item_id, modulo, con_clic, mostrado_en)"
                    + " VALUES (?, 'PRODUCTO', ?, ?, false, ?)",
                    sujeto, producto, modulo.name(), Timestamp.from(cuando));
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
}
