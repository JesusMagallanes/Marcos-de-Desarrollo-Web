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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.PruebaIntegracion;
import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.Carrusel;

/**
 * Lo colaborativo dentro del Home, y las reglas que no puede saltarse.
 *
 * <p>Que el generador funcione ya se prueba en {@link ColaborativoIT}. Lo que se
 * comprueba aquí es otra cosa y es la que de verdad puede hacer daño: que la
 * señal nueva NO atropelle a las que ya estaban. Un descarte explícito, la
 * fatiga y la de-duplicación entre carruseles tienen que seguir ganando aunque
 * la evidencia colaborativa sea abrumadora, porque son las reglas que la
 * persona ha puesto o que el sistema se ha impuesto por decencia.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Colaborativo dentro del Home")
@Transactional
class RecomendacionColaborativaIT extends PruebaIntegracion {

    @Autowired
    private RecomendacionService recomendador;

    @Autowired
    private ColaborativoService colaborativo;

    @Autowired
    private PerfilFacetaRepository perfiles;

    @Autowired
    private PesosDescubrimiento pesos;

    @Autowired
    private JdbcTemplate jdbc;

    private Long categoria;
    private UUID yo;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM catalogo.item_relacion");
        jdbc.update("DELETE FROM catalogo.sujeto_similitud");
        jdbc.update("DELETE FROM catalogo.item_descartado");
        jdbc.update("DELETE FROM catalogo.impresion");
        jdbc.update("DELETE FROM catalogo.evento_interaccion");
        jdbc.update("DELETE FROM catalogo.perfil_faceta");
        jdbc.update("DELETE FROM catalogo.sujeto");
        categoria = crearCategoria("home-colab-" + UUID.randomUUID());
        yo = crearSujeto();
    }

    /**
     * El montaje mínimo que hace nacer una recomendación colaborativa.
     *
     * <p>Yo miro la semilla. Otras personas que miraron esa misma semilla
     * miraron además el destino, que yo no he visto. Esa es toda la evidencia:
     * ni categoría compartida, ni marca, ni atributo.
     *
     * @return el producto que debería acabar recomendándome el sistema
     */
    private Long montarCoVisita(Long semilla, int cuantosVecinos) {
        Long destino = crearProducto("Destino");
        for (int i = 0; i < cuantosVecinos; i++) {
            UUID vecino = crearSujeto();
            ver(vecino, semilla, hace(1));
            ver(vecino, destino, hace(1));
        }
        ver(yo, semilla, hace(1));
        colaborativo.recalcular();
        return destino;
    }

    @Test
    @DisplayName("un producto sin nada en común entra por co-visita")
    void elCandidatoColaborativoEntra() {
        Long semilla = crearProducto("Semilla");
        Long destino = montarCoVisita(semilla, pesos.getColaborativoMinSoporte());

        Carrusel carrusel = recomendador.colaborativo(yo, 12, new LinkedHashSet<>());

        assertThat(carrusel).as("hay evidencia suficiente: tiene que haber carrusel").isNotNull();
        assertThat(carrusel.items()).extracting(p -> p.id()).contains(destino);
        assertThat(carrusel.origen())
                .as("se presenta como lo que es: patrón agregado, no gusto propio")
                .isEqualTo(Origen.COHORTE);
    }

    @Test
    @DisplayName("«no me interesa» gana a toda la evidencia colaborativa")
    void elDescarteDuroGanaSiempre() {
        /*
         * Es la regla que no puede tener excepciones. Alguien dijo
         * explícitamente que no quiere ver esto; que veinte personas parecidas
         * lo miren no cambia nada, y volver a enseñarlo convierte el botón en
         * un adorno y a la tienda en algo que no escucha.
         */
        Long semilla = crearProducto("Semilla");
        Long destino = montarCoVisita(semilla, 20);

        jdbc.update("INSERT INTO catalogo.item_descartado"
                + " (sujeto_id, item_tipo, item_id, motivo) VALUES (?, 'PRODUCTO', ?, ?)",
                yo, destino, "NOT_INTERESTED");

        Carrusel carrusel = recomendador.colaborativo(yo, 12, new LinkedHashSet<>());

        assertThat(itemsDe(carrusel))
                .as("descartado es descartado, venga de donde venga")
                .doesNotContain(destino);
    }

    @Test
    @DisplayName("la fatiga sigue apagando lo que ya se enseñó sin éxito")
    void laFatigaSigueMandando() {
        Long semilla = crearProducto("Semilla");
        Long destino = montarCoVisita(semilla, 20);

        // Se le enseñó de sobra y nunca lo pulsó: dejar de insistir.
        for (int i = 0; i <= pesos.getTopeImpresionesSinClic(); i++) {
            jdbc.update("INSERT INTO catalogo.impresion"
                    + " (sujeto_id, item_tipo, item_id, modulo, con_clic, mostrado_en)"
                    + " VALUES (?, 'PRODUCTO', ?, 'POPULARES', false, ?)",
                    yo, destino, Timestamp.from(hace(1)));
        }

        assertThat(itemsDe(recomendador.colaborativo(yo, 12, new LinkedHashSet<>())))
                .doesNotContain(destino);
    }

    @Test
    @DisplayName("lo que otro carrusel ya se llevó no se repite aquí")
    void noSeDuplicaEntreModulos() {
        Long semilla = crearProducto("Semilla");
        Long destino = montarCoVisita(semilla, pesos.getColaborativoMinSoporte());

        Set<Long> yaUsados = new LinkedHashSet<>(List.of(destino));

        assertThat(itemsDe(recomendador.colaborativo(yo, 12, yaUsados)))
                .as("un producto aparece UNA vez en todo el Home")
                .doesNotContain(destino);
    }

    @Test
    @DisplayName("sin evidencia no se inventa un carrusel")
    void arranqueEnFrioNoInventa() {
        // Un sujeto que solo ha mirado una cosa y nadie más ha mirado nada.
        ver(yo, crearProducto("Solitario"), hace(1));
        colaborativo.recalcular();

        assertThat(recomendador.colaborativo(yo, 12, new LinkedHashSet<>()))
                .as("mejor ningún carrusel que uno relleno de casualidades")
                .isNull();
    }

    @Test
    @DisplayName("un solo vecino no puede llenar el carrusel con su historial")
    void elPisoDePrivacidadDeLosVecinos() {
        /*
         * El riesgo concreto: si «otras personas descubrieron» se puede sostener
         * con UNA sola persona parecida, lo que se está enseñando es su
         * historial. Hacen falta varios aportantes distintos para que el dato
         * individual se convierta en patrón.
         */
        UUID gemelo = crearSujeto();
        ver(gemelo, crearProducto("Cualquiera"), hace(1));
        ver(yo, crearProducto("Cualquiera mio"), hace(1));

        for (String[] f : new String[][] { { "CATEGORIA", "monitores" },
                { "MARCA", "LG" }, { "ATRIBUTO", "pulgadas=27" } }) {
            perfiles.acumular(yo, f[0], f[1], 5.0, 86_400L);
            perfiles.acumular(gemelo, f[0], f[1], 5.0, 86_400L);
        }
        // Su secreto: un producto que solo ha mirado él.
        Long suyo = crearProducto("Solo lo miro el gemelo");
        ver(gemelo, suyo, hace(1));

        colaborativo.recalcular();

        assertThat(itemsDe(recomendador.colaborativo(yo, 12, new LinkedHashSet<>())))
                .as("con un aportante no se sirve: sería su historial, no un patrón")
                .doesNotContain(suyo);
    }

    @Test
    @DisplayName("la respuesta no lleva ni un identificador de nadie")
    void laSalidaEsAnonima() {
        Long semilla = crearProducto("Semilla");
        montarCoVisita(semilla, pesos.getColaborativoMinSoporte());

        Carrusel carrusel = recomendador.colaborativo(yo, 12, new LinkedHashSet<>());
        assertThat(carrusel).isNotNull();

        /*
         * Se mira el DTO entero convertido a texto. Es tosco y por eso mismo
         * atrapa lo que una comprobación campo a campo dejaría pasar el día que
         * alguien añada un campo nuevo sin pensar.
         */
        String serializado = carrusel.toString();
        assertThat(serializado)
                .as("ni el sujeto que pregunta ni ninguno de los que aportaron")
                .doesNotContain(yo.toString());

        for (UUID otro : jdbc.queryForList(
                "SELECT id FROM catalogo.sujeto WHERE id <> ?", UUID.class, yo)) {
            assertThat(serializado).doesNotContain(otro.toString());
        }

        assertThat(carrusel.motivo())
                .as("el texto habla de personas en general, nunca de alguien")
                .isEqualTo("Descubierto por gente con intereses parecidos a los tuyos");
    }

    @Test
    @DisplayName("la ficha mezcla contenido y co-visita sin perder el contenido")
    void laFichaSigueTeniendoRelacionadosPorContenido() {
        /*
         * El riesgo al mezclar es callar la señal vieja. El contenido es lo
         * ÚNICO que funciona con un producto recién publicado, que todavía no ha
         * coincidido con nadie: si la mezcla lo apagara, la ficha de un producto
         * nuevo se quedaría vacía.
         */
        Long visto = crearProducto("Visto");
        crearProducto("Hermano de categoria");
        crearProducto("Otro hermano");

        Carrusel carrusel = recomendador.similares(visto, 12);

        assertThat(itemsDe(carrusel))
                .as("sin ninguna co-visita registrada, el contenido sigue respondiendo")
                .isNotEmpty();
    }

    /* ══════════════ Utilidades ══════════════ */

    private List<Long> itemsDe(Carrusel carrusel) {
        if (carrusel == null) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        carrusel.items().forEach(p -> ids.add(p.id()));
        return ids;
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

    private void ver(UUID sujeto, Long producto, Instant cuando) {
        jdbc.update("INSERT INTO catalogo.evento_interaccion"
                + " (sujeto_id, tipo, item_tipo, item_id, ocurrido_en)"
                + " VALUES (?, 'ITEM_VIEW', 'PRODUCTO', ?, ?)",
                sujeto, producto, Timestamp.from(cuando));
    }
}
