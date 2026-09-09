package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.PruebaIntegracion;

/**
 * El SQL más intrincado del proyecto, ejecutado de verdad.
 *
 * <p>El perfil de intereses no se calcula en Java: son cuatro sentencias
 * nativas que hacen `INSERT … ON CONFLICT DO UPDATE` con un decaimiento
 * exponencial dentro del propio UPDATE, y una de ellas ademas sube el arbol de
 * categorias con un CTE recursivo. Nada de eso lo comprueba el compilador —es
 * una cadena de texto— ni lo puede comprobar una unitaria con un simulacro: el
 * comportamiento ESTA en PostgreSQL.
 *
 * <p>Se prueban las dos propiedades de las que depende todo lo demas: que el
 * peso se reparte por la jerarquia con la atenuacion pactada, y que un segundo
 * evento sobre la misma faceta suma en lugar de estrellarse contra la clave
 * primaria.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Perfil de facetas")
/*
 * Estas consultas son `@Modifying` y exigen transaccion activa; en produccion la
 * pone `IngestaService`, que es quien las llama. Aqui la pone la anotacion, y de
 * paso deshace lo escrito al terminar: el contenedor es UNO para toda la
 * ejecucion y una categoria de prueba que sobreviva se le aparece a la clase
 * siguiente.
 */
@Transactional
class PerfilFacetaIT extends PruebaIntegracion {

    /** Un dia, que es la vida media que usa el servicio. */
    private static final long VIDA_MEDIA_SEG = 86_400L;

    @Autowired
    private PerfilFacetaRepository perfiles;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID sujeto;

    @BeforeEach
    void preparar() {
        sujeto = UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.sujeto (id) VALUES (?)", sujeto);
    }

    /** {@code faceta -> score}, para poder afirmar sobre valores y no sobre orden. */
    private Map<String, BigDecimal> facetasDe(String tipo) {
        return jdbc.queryForList(
                "SELECT faceta, score FROM catalogo.perfil_faceta"
                        + " WHERE sujeto_id = ? AND tipo_faceta = ?",
                sujeto, tipo)
                .stream()
                .collect(Collectors.toMap(
                        f -> (String) f.get("faceta"),
                        f -> (BigDecimal) f.get("score")));
    }

    @Test
    @DisplayName("una categoría acredita también a sus ancestras, atenuadas")
    void elPesoSubePorLaJerarquia() {
        /*
         * Tres niveles reales: tecnologia > computacion > monitores. Es la forma
         * que tiene el catalogo, y es lo que permite recomendar un monitor a
         * quien solo ha mirado laptops.
         */
        Long abuela = crearCategoria("Tecnologia IT", "tecnologia-it", null);
        Long madre = crearCategoria("Computacion IT", "computacion-it", abuela);
        Long hija = crearCategoria("Monitores IT", "monitores-it", madre);

        perfiles.acumularCategoriaConAncestros(sujeto, hija, 10.0, 0.5, VIDA_MEDIA_SEG);

        Map<String, BigDecimal> facetas = facetasDe("CATEGORIA");

        assertThat(facetas)
                .as("la vista, su madre y su abuela; nadie mas")
                .containsOnlyKeys("monitores-it", "computacion-it", "tecnologia-it");

        assertThat(facetas.get("monitores-it").doubleValue())
                .as("la categoria vista se lleva el peso entero")
                .isCloseTo(10.0, within(0.001));
        assertThat(facetas.get("computacion-it").doubleValue())
                .as("la madre, la mitad")
                .isCloseTo(5.0, within(0.001));
        assertThat(facetas.get("tecnologia-it").doubleValue())
                .as("la abuela, un cuarto")
                .isCloseTo(2.5, within(0.001));
    }

    @Test
    @DisplayName("un segundo evento suma sobre el primero en vez de chocar")
    void elSegundoEventoAcumula() {
        /*
         * Es la mitad del `ON CONFLICT`: sin el, la segunda vez que alguien mira
         * algo de la misma marca la sentencia violaria la clave primaria y el
         * evento se perderia entero.
         *
         * El decaimiento se aplica sobre el tiempo transcurrido, que aqui es de
         * milisegundos: 2^(-0/86400) es practicamente 1, asi que se esperan los
         * dos pesos casi intactos. Lo que se comprueba es que ACUMULA; el olvido
         * a lo largo de dias no se puede provocar en una prueba sin falsear el
         * reloj de la base.
         */
        perfiles.acumular(sujeto, "MARCA", "Acer", 3.0, VIDA_MEDIA_SEG);
        perfiles.acumular(sujeto, "MARCA", "Acer", 4.0, VIDA_MEDIA_SEG);

        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT score, eventos FROM catalogo.perfil_faceta"
                        + " WHERE sujeto_id = ? AND tipo_faceta = 'MARCA' AND faceta = 'Acer'",
                sujeto);

        assertThat(filas).as("una sola fila, no dos").hasSize(1);
        assertThat(((BigDecimal) filas.get(0).get("score")).doubleValue())
                .as("3 decaido casi nada, mas 4")
                .isCloseTo(7.0, within(0.01));
        assertThat(filas.get(0).get("eventos"))
                .as("los eventos que lo sostienen, para la confianza")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("una categoría sin madre no arrastra a nadie")
    void laRaizNoTieneAncestros() {
        Long suelta = crearCategoria("Sin Madre IT", "sin-madre-it", null);

        perfiles.acumularCategoriaConAncestros(sujeto, suelta, 8.0, 0.5, VIDA_MEDIA_SEG);

        assertThat(facetasDe("CATEGORIA")).containsOnlyKeys("sin-madre-it");
    }

    private Long crearCategoria(String nombre, String slug, Long padre) {
        jdbc.update("INSERT INTO catalogo.categoria (name, slug, description, categoria_padre_id)"
                + " VALUES (?, ?, ?, ?)", nombre, slug, "Creada por una prueba", padre);
        return jdbc.queryForObject(
                "SELECT id FROM catalogo.categoria WHERE slug = ?", Long.class, slug);
    }
}
