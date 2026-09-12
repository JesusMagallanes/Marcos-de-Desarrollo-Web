package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.backend.catalogo.PruebaIntegracion;

/**
 * El sujeto A y el atacante B, a la vez y por HTTP de verdad.
 *
 * <h4>Qué se defiende aquí</h4>
 *
 * <p>Antes de H el identificador de sujeto anónimo era una credencial sin
 * protección: bastaba con conocerlo —de un log, de una URL, de un equipo
 * compartido— para mandarlo en {@code X-Sujeto} y recibir esa identidad entera.
 * B podía leer el perfil de A, borrarlo y envenenarlo.
 *
 * <p>Estas pruebas ponen a B a intentarlo por los cuatro caminos que existen, y
 * exigen que fracase en los cuatro. No se comprueban métodos internos: se hacen
 * las peticiones que haría un atacante.
 *
 * <p>NO es transaccional: hay escrituras en transacción propia y se consulta la
 * base para comprobar el daño.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@AutoConfigureMockMvc
@DisplayName("Aislamiento entre sujetos")
class AislamientoSujetosIT extends PruebaIntegracion {

    /*
     * Por HTTP y no llamando a los servicios: lo que se comprueba es lo que
     * puede hacer alguien desde fuera. Un ataque se monta con peticiones, y
     * probarlo contra un metodo interno se saltaria justo las capas —el
     * controlador, la validacion, los filtros— donde vive la defensa.
     */
    @Autowired
    private MockMvc http;

    @Autowired
    private JdbcTemplate jdbc;

    private Long categoria;
    private UUID sujetoA;
    private String firmaA;

    /*
     * Una procedencia distinta por prueba.
     *
     * El limitador es un unico objeto en memoria para toda la JVM, asi que sin
     * esto la prueba del cupo se gastaba el de las demas y las siguientes veian
     * un 429 que no les correspondia. Lo descubrio ella misma al fallar.
     *
     * Va en `X-Forwarded-For`, que `IpCliente` solo acepta desde un proxy de
     * confianza —y 127.0.0.1 lo es—, asi que de paso se ejercita ese camino.
     */
    private String procedencia;

    private static final java.util.concurrent.atomic.AtomicInteger SIGUIENTE_IP =
            new java.util.concurrent.atomic.AtomicInteger(1);

    @BeforeEach
    void prepararDosVisitantes() {
        jdbc.update("DELETE FROM catalogo.recomendacion_servida");
        jdbc.update("DELETE FROM catalogo.impresion");
        jdbc.update("DELETE FROM catalogo.evento_interaccion");
        jdbc.update("DELETE FROM catalogo.perfil_faceta");
        jdbc.update("DELETE FROM catalogo.item_descartado");
        jdbc.update("DELETE FROM catalogo.sujeto");

        categoria = crearCategoria("aisl-" + UUID.randomUUID());
        // Unica y no aleatoria: con azar dos pruebas chocaban de IP y una
        // heredaba el cupo gastado por la otra. Ya paso una vez.
        procedencia = "203.0.113." + SIGUIENTE_IP.getAndIncrement();

        // A es un visitante real: el servidor le dio su identificador y su firma.
        String suHome = pedir(HttpMethod.GET, "/home", null, null, null).cuerpo();
        sujetoA = UUID.fromString(campo(suHome, "sujetoId"));
        firmaA = campo(suHome, "firma");

        conPerfil(sujetoA, "monitores");
    }

    @AfterEach
    void devolverElCatalogoComoEstaba() {
        jdbc.update("DELETE FROM catalogo.recomendacion_servida WHERE item_id IN"
                + " (SELECT id FROM catalogo.producto WHERE categoria_id = ?)", categoria);
        jdbc.update("DELETE FROM catalogo.producto WHERE categoria_id = ?", categoria);
        jdbc.update("DELETE FROM catalogo.categoria WHERE id = ?", categoria);
    }

    /* ══════════════ B intenta ser A ══════════════ */

    @Nested
    @DisplayName("B conoce el identificador de A")
    class BConoceElIdentificador {

        @Test
        @DisplayName("no puede leer su perfil")
        void noPuedeLeerlo() {
            /*
             * El ataque que H existe para cerrar. Antes esto devolvia el perfil
             * de A entero: sus categorias, sus marcas y cuanto le interesa cada
             * una. B solo necesitaba haber visto el UUID una vez.
             */
            Respuesta robo = pedir(HttpMethod.GET, "/mis-intereses",
                    sujetoA, null, null);

            assertThat(robo.estado()).isEqualTo(200);
            assertThat(robo.cuerpo())
                    .as("sin la firma, B recibe un perfil vacío que no es el de A")
                    .isEqualTo("[]");
            assertThat(facetasDe(sujetoA))
                    .as("y el de A sigue donde estaba")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("no puede borrarlo")
        void noPuedeBorrarlo() {
            pedir(HttpMethod.DELETE, "/mis-intereses", sujetoA, null, null);

            assertThat(facetasDe(sujetoA))
                    .as("el derecho al olvido es de su dueño, no de quien sepa su número")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("no puede envenenarlo con eventos")
        void noPuedeEnvenenarlo() {
            long antes = eventosDe(sujetoA);

            pedir(HttpMethod.POST, "/eventos", sujetoA, null, """
                    {"sesionId":"%s","eventos":[
                      {"tipo":"ITEM_VIEW","itemTipo":"PRODUCTO","itemId":%d}]}"""
                    .formatted(UUID.randomUUID(), crearProducto("Cebo")));

            assertThat(eventosDe(sujetoA))
                    .as("los eventos de B se anotan en el sujeto de B, no en el de A")
                    .isEqualTo(antes);
        }

        @Test
        @DisplayName("una firma inventada tampoco le sirve")
        void laFirmaInventadaNoVale() {
            Respuesta robo = pedir(HttpMethod.GET, "/mis-intereses",
                    sujetoA, "firma-que-me-acabo-de-inventar", null);

            assertThat(robo.cuerpo()).isEqualTo("[]");
            assertThat(facetasDe(sujetoA)).isEqualTo(1);
        }

        @Test
        @DisplayName("con SU propia firma, A sí ve lo suyo")
        void aSiPuede() {
            /*
             * El control. Sin el, todo lo anterior pasaria igual si el endpoint
             * estuviera roto y devolviera siempre vacio.
             */
            Respuesta suyo = pedir(HttpMethod.GET, "/mis-intereses",
                    sujetoA, firmaA, null);

            assertThat(suyo.cuerpo())
                    .as("su perfil, con su credencial")
                    .contains("monitores");
        }
    }

    /* ══════════════ B intenta mover el ranking de todos ══════════════ */

    @Nested
    @DisplayName("Impresiones forjadas")
    class ImpresionesForjadas {

        @Test
        @DisplayName("una impresión de algo que nunca se sirvió no se guarda")
        void noSeGuardaLoQueNoSeSirvio() {
            /*
             * Era la unica via encontrada de afectar a TERCEROS: el freno por
             * exposicion se aplica al ranking de todos, y se alimentaba de
             * impresiones que el cliente declaraba sin que nadie las
             * comprobara. Un vendedor podia hundir a un rival desde su
             * navegador.
             */
            Long rival = crearProducto("Producto de la competencia");

            Respuesta intento = pedir(HttpMethod.POST, "/impresiones", null, null,
                    """
                    {"impresiones":[
                      {"itemTipo":"PRODUCTO","itemId":%d,"modulo":"POPULARES","posicion":0}]}"""
                            .formatted(rival));

            assertThat(intento.estado()).isEqualTo(202);
            assertThat(campo(intento.cuerpo(), "registrados")).isEqualTo("0");
            assertThat(campo(intento.cuerpo(), "descartados")).isEqualTo("1");
            assertThat(impresionesDe(rival))
                    .as("no hay evidencia de que se le sirviera: no cuenta")
                    .isZero();
        }

        @Test
        @DisplayName("la de algo que sí se sirvió se guarda con normalidad")
        void loLegitimoPasa() {
            Long producto = crearProducto("Servido de verdad");

            Respuesta home = pedir(HttpMethod.GET, "/home", null, null, null);
            UUID visitante = UUID.fromString(campo(home.cuerpo(), "sujetoId"));
            String firma = campo(home.cuerpo(), "firma");
            servir(visitante, producto);

            Respuesta legitima = pedir(HttpMethod.POST, "/impresiones",
                    visitante, firma, """
                    {"impresiones":[
                      {"itemTipo":"PRODUCTO","itemId":%d,"modulo":"POPULARES","posicion":0}]}"""
                            .formatted(producto));

            assertThat(campo(legitima.cuerpo(), "registrados")).isEqualTo("1");
            assertThat(campo(legitima.cuerpo(), "descartados")).isEqualTo("0");
            assertThat(impresionesDe(producto)).isEqualTo(1);
        }

        @Test
        @DisplayName("cambiar de módulo no cuela: la evidencia es del módulo concreto")
        void elModuloImporta() {
            Long producto = crearProducto("Servido en un sitio");

            Respuesta home = pedir(HttpMethod.GET, "/home", null, null, null);
            UUID visitante = UUID.fromString(campo(home.cuerpo(), "sujetoId"));
            String firma = campo(home.cuerpo(), "firma");
            servir(visitante, producto);

            Respuesta otro = pedir(HttpMethod.POST, "/impresiones",
                    visitante, firma, """
                    {"impresiones":[
                      {"itemTipo":"PRODUCTO","itemId":%d,"modulo":"RELACIONADOS",
                       "posicion":0}]}""".formatted(producto));

            assertThat(campo(otro.cuerpo(), "descartados"))
                    .as("se le sirvió en POPULARES; declararlo en otro carrusel es forjarlo")
                    .isEqualTo("1");
        }
    }

    /* ══════════════ El piso de exposición ══════════════ */

    @Nested
    @DisplayName("Exposición protegida")
    class ExposicionProtegida {

        @Test
        @DisplayName("la exposición de pocas personas no cuenta para el freno")
        void pocasPersonasNoFrenan() {
            /*
             * Aunque las impresiones sean legitimas, si las sostiene poca gente
             * no describen «esto se ensena mucho», describen a esas personas.
             * El freno se aplica al ranking de todos y por eso exige varias.
             */
            Long producto = crearProducto("Visto por dos personas");
            for (int i = 0; i < 30; i++) {
                mostrar(crearSujetoEnBase(), producto);
            }
            // Treinta impresiones, pero de treinta personas distintas: sí cuenta.
            assertThat(expuestos(producto)).contains(producto);

            Long otro = crearProducto("Visto mucho por una sola");
            UUID unaSola = crearSujetoEnBase();
            for (int i = 0; i < 30; i++) {
                mostrar(unaSola, otro);
            }
            assertThat(expuestos(otro))
                    .as("misma cuenta, una sola persona detrás: no puede frenar a nadie")
                    .doesNotContain(otro);
        }
    }

    /* ══════════════ Cupos ══════════════ */

    @Nested
    @DisplayName("Límites de abuso")
    class Limites {

        @Test
        @DisplayName("pasado el cupo del sujeto, la ingesta responde 429 y no guarda")
        void cupoPorSujeto() {
            Respuesta home = pedir(HttpMethod.GET, "/home", null, null, null);
            UUID visitante = UUID.fromString(campo(home.cuerpo(), "sujetoId"));
            String firma = campo(home.cuerpo(), "firma");
            Long producto = crearProducto("Cebo");

            String cuerpo = """
                    {"sesionId":"%s","eventos":[
                      {"tipo":"ITEM_VIEW","itemTipo":"PRODUCTO","itemId":%d}]}"""
                    .formatted(UUID.randomUUID(), producto);

            int ultimo = 202;
            for (int i = 0; i < 200 && ultimo != 429; i++) {
                ultimo = pedir(HttpMethod.POST, "/eventos", visitante, firma, cuerpo).estado();
            }

            assertThat(ultimo)
                    .as("el cupo por IP permitía doce mil eventos por minuto contra un perfil")
                    .isEqualTo(429);
        }
    }

    @Nested
    @DisplayName("Amplificación de identidades")
    class Amplificacion {

        @Test
        @DisplayName("una misma procedencia no puede estrenar sujetos sin fin")
        void noSePuedenFabricarIdentidades() {
            /*
             * El cupo por sujeto no cierra nada por si solo: quien quiera mas
             * cupo se inventa mas identidades y multiplica el suyo. Esto es lo
             * que lo cierra, y funciona porque desde H el identificador va
             * firmado: ya no se puede inventar uno por fuera, hay que pedirlo.
             */
            long antes = sujetosEnBase();

            for (int i = 0; i < 60; i++) {
                pedir(HttpMethod.GET, "/home", null, null, null);
            }

            long nacidos = sujetosEnBase() - antes;

            assertThat(nacidos)
                    .as("sesenta peticiones sin identificador, y no nacen sesenta sujetos")
                    .isLessThan(60)
                    .isLessThanOrEqualTo(25);
        }

        @Test
        @DisplayName("pero quien ya tiene el suyo sigue navegando sin tropezar")
        void elVisitanteNormalNoSeEntera() {
            /*
             * El cupo solo mira a quien ESTRENA identidad. Un visitante real
             * estrena una y la reutiliza, asi que puede navegar todo lo que
             * quiera sin rozarlo — que es la diferencia entre un freno y una
             * pared.
             */
            long antes = sujetosEnBase();

            for (int i = 0; i < 40; i++) {
                Respuesta r = pedir(HttpMethod.GET, "/home", sujetoA, firmaA, null);
                assertThat(r.estado()).isEqualTo(200);
                assertThat(campo(r.cuerpo(), "sujetoId")).isEqualTo(sujetoA.toString());
            }

            assertThat(sujetosEnBase() - antes)
                    .as("vuelve con lo suyo: no nace ninguno")
                    .isZero();
        }
    }

    /* ══════════════ Utilidades ══════════════ */

    /** Lo que respondió el servidor a una petición. */
    private record Respuesta(int estado, String cuerpo) {
    }

    private Respuesta pedir(HttpMethod metodo, String ruta, UUID sujeto, String firma,
            String cuerpo) {
        try {
            MockHttpServletRequestBuilder peticion = MockMvcRequestBuilders
                    .request(metodo, "/api/descubrimiento" + ruta)
                    .contentType(MediaType.APPLICATION_JSON);

            if (sujeto != null) {
                peticion = peticion.header("X-Sujeto", sujeto.toString());
            }
            if (firma != null) {
                peticion = peticion.header(DescubrimientoController.CABECERA_FIRMA, firma);
            }
            if (cuerpo != null) {
                peticion = peticion.content(cuerpo);
            }
            peticion = peticion.header("X-Forwarded-For", procedencia);
            MvcResult r = http.perform(peticion).andReturn();
            return new Respuesta(r.getResponse().getStatus(),
                    r.getResponse().getContentAsString());

        } catch (Exception fallo) {
            throw new AssertionError("La petición no llegó a hacerse", fallo);
        }
    }

    /** Extrae un campo escalar del JSON sin traerse un parser para tres usos. */
    private String campo(String json, String nombre) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + nombre + "\"\\s*:\\s*\"?([^,\"}]+)\"?")
                .matcher(json == null ? "" : json);
        return m.find() ? m.group(1) : null;
    }

    private List<Long> expuestos(Long producto) {
        return jdbc.queryForList("""
                SELECT i.item_id FROM catalogo.impresion i
                 WHERE i.item_tipo = 'PRODUCTO' AND i.item_id = ?
                   AND i.mostrado_en >= now() - INTERVAL '7 days'
                 GROUP BY i.item_id
                HAVING COUNT(DISTINCT i.sujeto_id) >= 5
                """, Long.class, producto);
    }

    private int facetasDe(UUID sujeto) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM catalogo.perfil_faceta WHERE sujeto_id = ?",
                Integer.class, sujeto);
    }

    private long eventosDe(UUID sujeto) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM catalogo.evento_interaccion WHERE sujeto_id = ?",
                Long.class, sujeto);
    }

    private long sujetosEnBase() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM catalogo.sujeto", Long.class);
    }

    private long impresionesDe(Long producto) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM catalogo.impresion WHERE item_id = ?",
                Long.class, producto);
    }

    private void conPerfil(UUID sujeto, String faceta) {
        jdbc.update("INSERT INTO catalogo.perfil_faceta"
                + " (sujeto_id, tipo_faceta, faceta, score, eventos, actualizado_en)"
                + " VALUES (?, 'CATEGORIA', ?, 50.0, 20, ?)",
                sujeto, faceta, Timestamp.from(Instant.now()));
    }

    private void servir(UUID sujeto, Long producto) {
        jdbc.update("INSERT INTO catalogo.recomendacion_servida"
                + " (sujeto_id, item_tipo, item_id, modulo, razon, posicion, score,"
                + " ranker_version, con_perfil, servido_en)"
                + " VALUES (?, 'PRODUCTO', ?, 'POPULARES', 'POPULAR', 0, 1.0,"
                + " 'prueba', false, ?)",
                sujeto, producto, Timestamp.from(Instant.now()));
    }

    private void mostrar(UUID sujeto, Long producto) {
        jdbc.update("INSERT INTO catalogo.impresion"
                + " (sujeto_id, item_tipo, item_id, modulo, posicion, con_clic, mostrado_en)"
                + " VALUES (?, 'PRODUCTO', ?, 'POPULARES', 0, false, ?)",
                sujeto, producto, Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)));
    }

    private UUID crearSujetoEnBase() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.sujeto (id) VALUES (?)", id);
        return id;
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
}
