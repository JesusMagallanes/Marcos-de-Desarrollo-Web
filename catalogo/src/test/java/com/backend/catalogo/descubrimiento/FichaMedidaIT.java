package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.backend.catalogo.PruebaIntegracion;
import com.backend.catalogo.descubrimiento.adaptativo.PesosAdaptativos;
import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.Carrusel;

/**
 * La ficha de producto, que hasta ahora recomendaba a ciegas.
 *
 * <p>Era la superficie con más tráfico del sitio y la única sin medir: servía
 * un carrusel de relacionados y de ahí no quedaba constancia de nada. No se
 * podía saber qué había propuesto, ni por qué, ni en qué posición, ni con qué
 * configuración de pesos.
 *
 * <p>Y había algo peor que la falta de medición, que salió al arreglarlo: la
 * ficha tampoco aplicaba los filtros duros. Un producto marcado como «no me
 * interesa» reaparecía en la ficha de cualquier otro, de modo que el botón
 * parecía no servir — que es peor que no tenerlo.
 *
 * <p>NO es transaccional: el registro escribe en transacción propia y desde una
 * prueba con reversión no vería el sujeto.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("La ficha de producto, medida")
class FichaMedidaIT extends PruebaIntegracion {

    @Autowired
    private RecomendacionService recomendador;

    @Autowired
    private RecomendacionServidaRepository servidas;

    @Autowired
    private ColaborativoService colaborativo;

    @Autowired
    private PerfilFacetaRepository perfiles;

    @Autowired
    private PesosDescubrimiento pesos;

    @Autowired
    private PesosAdaptativos adaptativos;

    @Autowired
    private JdbcTemplate jdbc;

    private Long categoria;
    private UUID sujeto;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM catalogo.recomendacion_servida");
        jdbc.update("DELETE FROM catalogo.item_relacion");
        jdbc.update("DELETE FROM catalogo.item_descartado");
        jdbc.update("DELETE FROM catalogo.impresion");
        jdbc.update("DELETE FROM catalogo.evento_interaccion");
        jdbc.update("DELETE FROM catalogo.perfil_faceta");
        jdbc.update("DELETE FROM catalogo.sujeto");
        categoria = crearCategoria("ficha-it-" + UUID.randomUUID());
        sujeto = crearSujeto();
    }

    /* ══════════════ Que quede constancia ══════════════ */

    @Test
    @DisplayName("la ficha anota lo servido con todo lo que hace falta")
    void laFichaRegistraLoServido() {
        Long visto = crearProducto("El que se mira");
        crearProducto("Hermano de categoria");
        crearProducto("Otro hermano");

        Carrusel carrusel = recomendador.similares(visto, sujeto, 12);
        assertThat(carrusel).isNotNull();

        List<RecomendacionServida> filas = servidas.deSujetoDesde(
                sujeto, Instant.now().minus(1, ChronoUnit.HOURS));

        assertThat(filas).as("la superficie más visitada deja de ser invisible").isNotEmpty();
        assertThat(filas).hasSameSizeAs(carrusel.items());

        RecomendacionServida primera = filas.get(0);
        assertThat(primera.getModulo())
                .as("el módulo real que ya existía, no un nombre nuevo")
                .isEqualTo(ModuloDescubrimiento.RELACIONADOS.name());
        assertThat(primera.getPosicion()).isZero();
        assertThat(primera.getRankerVersion()).isEqualTo(versionEsperada());
        assertThat(primera.getRazon()).isNotNull();
        assertThat(primera.getScore()).isNotNull();
    }

    @Test
    @DisplayName("las posiciones se anotan en el orden en que se sirvieron")
    void lasPosicionesSonConsecutivas() {
        Long visto = crearProducto("El que se mira");
        for (int i = 0; i < 5; i++) {
            crearProducto("Hermano " + i);
        }

        Carrusel carrusel = recomendador.similares(visto, sujeto, 12);

        List<RecomendacionServida> filas = servidas.deSujetoDesde(
                sujeto, Instant.now().minus(1, ChronoUnit.HOURS));

        for (int i = 0; i < filas.size(); i++) {
            assertThat(filas.get(i).getPosicion())
                    .as("sin la posición no se puede corregir el sesgo de estar arriba")
                    .isEqualTo((short) i);
            assertThat(filas.get(i).getItemId()).isEqualTo(carrusel.items().get(i).id());
        }
    }

    @Test
    @DisplayName("la co-visita conserva su razón y no se disfraza de contenido")
    void seConservaLaRazonRealDeCadaItem() {
        /*
         * La ficha mezcla dos generadores en un solo carrusel. Anotar los dos
         * como CONTENT_SIMILAR —la razón del módulo— haría imposible saber cuál
         * de ellos acierta, que es justo lo que la medición existe para
         * responder.
         */
        Long visto = crearProducto("El que se mira");
        Long porCoVisita = crearProductoAjeno("Va con el, sin parecerse");

        for (int i = 0; i < pesos.getColaborativoMinSoporte(); i++) {
            UUID vecino = crearSujeto();
            ver(vecino, visto, hace(1));
            ver(vecino, porCoVisita, hace(1));
        }
        colaborativo.recalcular();

        recomendador.similares(visto, sujeto, 12);

        assertThat(servidas.findAll())
                .filteredOn(r -> r.getItemId().equals(porCoVisita))
                .singleElement()
                .satisfies(r -> assertThat(r.getRazon())
                        .as("llegó por conducta, no por ficha: tiene que constar")
                        .isEqualTo(RazonRecomendacion.CO_VIEWED));
    }


    /**
     * La versión que DEBE quedar grabada: la de la fórmula que ordenó de verdad.
     *
     * <p>No se fija a mano. Si se escribiera «v3.0», la prueba pasaría a estar
     * mintiendo en cuanto se encendiera el ranker adaptativo — que es
     * exactamente lo que pasó — y peor aún: dejaría de comprobar la propiedad
     * que importa, que es que la versión grabada y la fórmula usada no puedan
     * separarse.
     */
    private String versionEsperada() {
        return adaptativos.isActivo() ? adaptativos.version() : pesos.rankerVersion();
    }

    /* ══════════════ Identidad ══════════════ */

    @Test
    @DisplayName("un visitante anónimo con sujeto propio queda anotado a ese sujeto")
    void elAnonimoSeAnotaASuSujeto() {
        Long visto = crearProducto("El que se mira");
        crearProducto("Hermano");

        recomendador.similares(visto, sujeto, 12);

        assertThat(servidas.findAll())
                .isNotEmpty()
                .allSatisfy(r -> assertThat(r.getSujetoId()).isEqualTo(sujeto));
    }

    @Test
    @DisplayName("sin sujeto no se inventa uno ni se cuelga de nadie")
    void sinSujetoNoSeAnotaNada() {
        /*
         * Un rastreador recorriendo el catálogo llenaría la tabla de sujetos que
         * no son nadie. Y, sobre todo, atribuir esa recomendación a cualquier
         * otro sería mezclar identidades: lo peor que puede hacer un sistema que
         * guarda conducta.
         */
        Long visto = crearProducto("El que se mira");
        crearProducto("Hermano");

        Carrusel carrusel = recomendador.similares(visto, null, 12);

        assertThat(carrusel)
                .as("el visitante sigue viendo su carrusel: no medir no es no servir")
                .isNotNull();
        assertThat(carrusel.items()).isNotEmpty();
        assertThat(servidas.findAll()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalogo.sujeto", Integer.class))
                .as("no se acuñó ninguna identidad nueva")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("lo anotado para un sujeto no aparece bajo otro")
    void noSeMezclanIdentidades() {
        Long visto = crearProducto("El que se mira");
        crearProducto("Hermano");
        UUID otro = crearSujeto();

        recomendador.similares(visto, sujeto, 12);

        assertThat(servidas.deSujetoDesde(otro, Instant.now().minus(1, ChronoUnit.HOURS)))
                .as("cada uno con lo suyo")
                .isEmpty();
        assertThat(servidas.deSujetoDesde(sujeto, Instant.now().minus(1, ChronoUnit.HOURS)))
                .isNotEmpty();
    }

    @Test
    @DisplayName("tener identificador no es tener perfil")
    void elArranqueEnFrioNoSeConfundeConTenerSujeto() {
        /*
         * Lo delató el recorrido en navegador: el sujeto estrenado tras cerrar
         * sesión salía anotado como si el sistema lo conociera. Se estaba
         * guardando «hay sujeto» donde tenía que ir «hay perfil», y con eso la
         * segmentación de arranque en frío —la que dice si el recomendador sirve
         * a quien llega o solo a quien ya lo usaba— quedaba vacía de sentido.
         *
         * Un sujeto recién creado tiene identificador desde su primera petición
         * y no tiene ni un evento.
         */
        Long visto = crearProducto("El que se mira");
        crearProducto("Hermano");
        UUID reciennacido = crearSujeto();

        recomendador.similares(visto, reciennacido, 12);

        assertThat(servidas.findAll())
                .isNotEmpty()
                .allSatisfy(r -> assertThat(r.isConPerfil())
                        .as("sin un solo evento detrás, no hay perfil que valga")
                        .isFalse());
    }

    /* ══════════════ Las reglas que no se pueden saltar ══════════════ */

    @Test
    @DisplayName("«no me interesa» también manda en la ficha")
    void elDescarteDuroSeRespetaEnLaFicha() {
        /*
         * Este era el fallo de verdad, no la falta de medición: alguien
         * descartaba un producto y volvía a encontrárselo en la ficha de
         * cualquier otro. La ficha no aplicaba ningún filtro duro.
         */
        Long visto = crearProducto("El que se mira");
        Long descartado = crearProducto("Hermano descartado");
        crearProducto("Hermano aceptable");

        jdbc.update("INSERT INTO catalogo.item_descartado"
                + " (sujeto_id, item_tipo, item_id, motivo) VALUES (?, 'PRODUCTO', ?, ?)",
                sujeto, descartado, "NOT_INTERESTED");

        Carrusel carrusel = recomendador.similares(visto, sujeto, 12);

        assertThat(idsDe(carrusel))
                .as("descartado es descartado, también aquí")
                .doesNotContain(descartado);
    }

    @Test
    @DisplayName("la fatiga también manda en la ficha")
    void laFatigaSeRespetaEnLaFicha() {
        Long visto = crearProducto("El que se mira");
        Long cansado = crearProducto("Hermano ya muy visto");
        crearProducto("Hermano fresco");

        for (int i = 0; i < pesos.getCooldownMaximo(); i++) {
            jdbc.update("INSERT INTO catalogo.impresion"
                    + " (sujeto_id, item_tipo, item_id, modulo, con_clic, mostrado_en)"
                    + " VALUES (?, 'PRODUCTO', ?, 'RELACIONADOS', false, ?)",
                    sujeto, cansado, Timestamp.from(hace(1)));
        }

        assertThat(idsDe(recomendador.similares(visto, sujeto, 12)))
                .doesNotContain(cansado);
    }

    @Test
    @DisplayName("el producto que se está viendo no se recomienda a sí mismo")
    void elProductoNoSeRecomiendaASiMismo() {
        Long visto = crearProducto("El que se mira");
        crearProducto("Hermano");

        assertThat(idsDe(recomendador.similares(visto, sujeto, 12))).doesNotContain(visto);
        assertThat(idsDe(recomendador.similares(visto, null, 12))).doesNotContain(visto);
    }

    @Test
    @DisplayName("un producto no se anota dos veces en la misma respuesta")
    void noHayDuplicadosEnLoAnotado() {
        /*
         * Llega por los dos generadores a la vez —parecido por ficha Y
         * co-visita— y tiene que salir UNA vez. Si se anotara dos, su CTR
         * quedaría dividido por el doble de impresiones y parecería la mitad de
         * bueno de lo que es.
         */
        Long visto = crearProducto("El que se mira");
        Long porAmbas = crearProducto("Hermano que ademas va con el");

        for (int i = 0; i < pesos.getColaborativoMinSoporte(); i++) {
            UUID vecino = crearSujeto();
            ver(vecino, visto, hace(1));
            ver(vecino, porAmbas, hace(1));
        }
        colaborativo.recalcular();

        recomendador.similares(visto, sujeto, 12);

        List<RecomendacionServida> filas = servidas.findAll();
        assertThat(filas).extracting(RecomendacionServida::getItemId).doesNotHaveDuplicates();
        assertThat(filas)
                .filteredOn(r -> r.getItemId().equals(porAmbas))
                .hasSize(1);
    }

    @Test
    @DisplayName("anotar no cambia lo que se recomienda")
    void medirNoAlteraElRanking() {
        /*
         * La comprobación que sostiene todo lo demás. Si medir cambiara el
         * resultado, las métricas describirían un sistema que no es el que
         * atiende a la gente.
         */
        Long visto = crearProducto("El que se mira");
        for (int i = 0; i < 4; i++) {
            crearProducto("Hermano " + i);
        }

        List<Long> conSujeto = idsDe(recomendador.similares(visto, sujeto, 12));
        jdbc.update("DELETE FROM catalogo.recomendacion_servida");
        List<Long> sinSujeto = idsDe(recomendador.similares(visto, null, 12));

        assertThat(conSujeto)
                .as("sin descartes ni fatiga, la lista es la misma se anote o no")
                .isEqualTo(sinSujeto);
    }

    /* ══════════════ Utilidades ══════════════ */

    private List<Long> idsDe(Carrusel carrusel) {
        return carrusel == null ? List.of() : carrusel.items().stream().map(p -> p.id()).toList();
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
        return crearProductoEn(categoria, nombre);
    }

    /** En otra categoría: solo puede llegar por co-visita, no por parecido. */
    private Long crearProductoAjeno(String nombre) {
        return crearProductoEn(crearCategoria("ajena-" + UUID.randomUUID()), nombre);
    }

    private Long crearProductoEn(Long cat, String nombre) {
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
