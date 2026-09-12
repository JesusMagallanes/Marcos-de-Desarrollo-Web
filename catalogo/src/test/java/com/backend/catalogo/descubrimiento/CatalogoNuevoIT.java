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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.backend.catalogo.PruebaIntegracion;
import com.backend.catalogo.categoria.CategoriaRepository;
import com.backend.catalogo.producto.EstadoModeracion;
import com.backend.catalogo.producto.Producto;
import com.backend.catalogo.producto.ProductoRepository;
import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.Carrusel;

/**
 * El producto que acaba de llegar, y el ciclo cerrado del que no sale solo.
 *
 * <p>Un producto recién publicado no genera eventos porque nadie lo ve; sin
 * eventos no entra en ninguna relación de co-visita; sin relaciones no lo
 * propone ningún generador; y sin que nadie lo proponga, nadie lo ve. Por bueno
 * que sea, de ahí no sale. El catálogo nuevo es la única puerta.
 *
 * <p>Y es una puerta estrecha a propósito. La mitad de esta clase comprueba que
 * ser nuevo NO es una credencial: no salta la elegibilidad, ni las exclusiones,
 * ni el descarte explícito, ni la fatiga, ni el ranker. Un mecanismo de
 * novedad que se saltara cualquiera de esas cosas sería una puerta trasera al
 * sistema entero.
 *
 * <p>NO es transaccional: el registro escribe en transacción propia y desde una
 * prueba con reversión no vería el sujeto.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Catálogo nuevo")
class CatalogoNuevoIT extends PruebaIntegracion {

    @Autowired
    private RecomendacionService recomendador;

    @Autowired
    private RecomendacionServidaRepository servidas;

    @Autowired
    private CandidatoRepository candidatos;

    @Autowired
    private PesosDescubrimiento pesos;

    @Autowired
    private ProductoRepository productos;

    @Autowired
    private CategoriaRepository categorias;

    @Autowired
    private JdbcTemplate jdbc;

    /** Centinela para que {@code NOT IN (:excluidos)} nunca reciba lista vacía. */
    private static final List<Long> NADA_EXCLUIDO = List.of(-1L);

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
        categoria = crearCategoria("catalogo-nuevo-it-" + UUID.randomUUID());
        sujeto = crearSujeto();
    }

    /**
     * Se lleva lo que crea.
     *
     * <p>Esta clase no puede ser transaccional, así que sus productos se
     * quedarían en el contenedor compartido. Y aquí importa el doble: un
     * producto con {@code creado_en} reciente que sobreviva sería «catálogo
     * nuevo» para todas las clases que corran después.
     */
    @AfterEach
    void devolverElCatalogoComoEstaba() {
        jdbc.update("DELETE FROM catalogo.recomendacion_servida"
                + " WHERE item_id IN (SELECT id FROM catalogo.producto WHERE categoria_id = ?)",
                categoria);
        jdbc.update("DELETE FROM catalogo.producto WHERE categoria_id = ?", categoria);
        jdbc.update("DELETE FROM catalogo.categoria WHERE id = ?", categoria);
    }

    /* ══════════════ La fecha ══════════════ */

    @Test
    @DisplayName("los productos anteriores a V24 se quedan sin fecha")
    void losHistoricosNoTienenFecha() {
        /*
         * La semilla metió sesenta y ocho productos por SQL, sin pasar por JPA.
         * Ninguno tiene fecha y ninguno debe tenerla: rellenarlos con algo
         * derivado del `id` habría sido inventar un dato del que después se
         * toman decisiones comerciales.
         */
        Integer conFecha = jdbc.queryForObject(
                "SELECT count(*) FROM catalogo.producto"
                        + " WHERE creado_en IS NOT NULL AND id < 100", Integer.class);

        assertThat(conFecha).as("la semilla no se rellenó").isZero();
    }

    @Test
    @DisplayName("un producto dado de alta por JPA recibe fecha real")
    void elAltaPorJpaSellaLaFecha() {
        /*
         * Lo pone el `@PrePersist` de la entidad, que es el idioma que ya usan
         * otras seis del proyecto. En la entidad y no en el servicio porque hay
         * DOS rutas de alta —panel y colaborador— y mañana puede haber una
         * tercera.
         */
        Long id = crearProductoPorJpa("Recien llegado");

        Instant creado = jdbc.queryForObject(
                "SELECT creado_en FROM catalogo.producto WHERE id = ?", Instant.class, id);

        assertThat(creado)
                .as("fecha real, puesta por el propio modelo")
                .isNotNull()
                .isCloseTo(Instant.now(), within(60_000));
    }

    @Test
    @DisplayName("sin fecha NO es nuevo, por alto que sea el identificador")
    void elIdNuncaEsUnProxyDeEdad() {
        /*
         * El caso que el encargo pide demostrar. Un `id` alto solo dice que la
         * fila se insertó después; la semilla metió sesenta y ocho de golpe en
         * un segundo. Si el `id` valiera como edad, medio catálogo sería «nuevo».
         */
        Long viejoConIdAlto = crearProducto("Id altisimo, edad desconocida");
        jdbc.update("UPDATE catalogo.producto SET creado_en = NULL WHERE id = ?", viejoConIdAlto);

        Long nuevoConIdBajo = crearProducto("Recien llegado");
        conFecha(nuevoConIdBajo, hace(1));

        List<Long> nuevos = idsDeCandidatos(candidatos.catalogoNuevo(
                pesos.fronteraCatalogoNuevo(), NADA_EXCLUIDO, 10));

        assertThat(nuevos)
                .as("id alto + sin fecha = edad desconocida, NO nuevo")
                .doesNotContain(viejoConIdAlto)
                .contains(nuevoConIdBajo);
    }

    @Test
    @DisplayName("lo dado de alta fuera de la ventana deja de ser nuevo")
    void laVentanaAcota() {
        Long dentro = crearProducto("Dentro de la ventana");
        Long fuera = crearProducto("Fuera de la ventana");
        conFecha(dentro, hace(pesos.getCatalogoNuevoDias() - 2));
        conFecha(fuera, hace(pesos.getCatalogoNuevoDias() + 5));

        List<Long> nuevos = idsDeCandidatos(candidatos.catalogoNuevo(
                pesos.fronteraCatalogoNuevo(), NADA_EXCLUIDO, 10));

        assertThat(nuevos).contains(dentro).doesNotContain(fuera);
    }

    /* ══════════════ Ser nuevo no es una credencial ══════════════ */

    @Test
    @DisplayName("un producto nuevo y agotado no es candidato")
    void elNuevoAgotadoNoEntra() {
        Long nuevo = crearProducto("Nuevo pero agotado");
        conFecha(nuevo, hace(1));
        jdbc.update("UPDATE catalogo.producto SET stock = 0 WHERE id = ?", nuevo);

        assertThat(idsDeCandidatos(candidatos.catalogoNuevo(
                pesos.fronteraCatalogoNuevo(), NADA_EXCLUIDO, 10)))
                .as("recién llegado y ya sin existencias: no se recomienda")
                .doesNotContain(nuevo);
    }

    @Test
    @DisplayName("un producto nuevo sin aprobar no es candidato")
    void elNuevoSinAprobarNoEntra() {
        Long pendiente = crearProducto("Nuevo y pendiente");
        Long rechazado = crearProducto("Nuevo y rechazado");
        conFecha(pendiente, hace(1));
        conFecha(rechazado, hace(1));
        jdbc.update("UPDATE catalogo.producto SET propietario_id = 99,"
                + " estado_moderacion = 'PENDIENTE' WHERE id = ?", pendiente);
        jdbc.update("UPDATE catalogo.producto SET propietario_id = 99,"
                + " estado_moderacion = 'RECHAZADO', motivo_rechazo = 'IT' WHERE id = ?",
                rechazado);

        assertThat(idsDeCandidatos(candidatos.catalogoNuevo(
                pesos.fronteraCatalogoNuevo(), NADA_EXCLUIDO, 10)))
                .doesNotContain(pendiente, rechazado);
    }

    @Test
    @DisplayName("lo excluido sigue excluido aunque sea nuevo")
    void lasExclusionesMandanSobreLaNovedad() {
        Long nuevo = crearProducto("Nuevo pero excluido");
        conFecha(nuevo, hace(1));

        assertThat(idsDeCandidatos(candidatos.catalogoNuevo(
                pesos.fronteraCatalogoNuevo(), List.of(nuevo, -1L), 10)))
                .isEmpty();
    }

    @Test
    @DisplayName("«no me interesa» gana también a un producto recién llegado")
    void elDescarteDuroGanaALaNovedad() {
        /*
         * La regla que no puede tener excepciones. Alguien dijo explícitamente
         * que no quiere ver esto; que acabe de llegar al catálogo no cambia
         * nada. Volver a enseñarlo por nuevo convertiría el botón en un adorno.
         */
        Long nuevo = crearProducto("Nuevo y descartado");
        conFecha(nuevo, hace(1));
        jdbc.update("INSERT INTO catalogo.item_descartado"
                + " (sujeto_id, item_tipo, item_id, motivo) VALUES (?, 'PRODUCTO', ?, ?)",
                sujeto, nuevo, "NOT_INTERESTED");

        assertThat(idsDe(recomendador.populares(sujeto, 12, new LinkedHashSet<>())))
                .as("descartado es descartado, venga por donde venga")
                .doesNotContain(nuevo);
        assertThat(servidas.findAll()).extracting(RecomendacionServida::getItemId)
                .as("ni siquiera llegó a ser candidato")
                .doesNotContain(nuevo);
    }

    @Test
    @DisplayName("la fatiga existente tampoco se salta por ser nuevo")
    void laFatigaGanaALaNovedad() {
        Long nuevo = crearProducto("Nuevo y ya muy visto");
        conFecha(nuevo, hace(1));

        for (int i = 0; i < pesos.getCooldownMaximo(); i++) {
            jdbc.update("INSERT INTO catalogo.impresion"
                    + " (sujeto_id, item_tipo, item_id, modulo, con_clic, mostrado_en)"
                    + " VALUES (?, 'PRODUCTO', ?, 'POPULARES', false, ?)",
                    sujeto, nuevo, Timestamp.from(hace(1)));
        }

        assertThat(idsDe(recomendador.populares(sujeto, 12, new LinkedHashSet<>())))
                .as("se le enseñó de sobra y lo ignoró: dejar de insistir")
                .doesNotContain(nuevo);
    }

    /* ══════════════ El presupuesto ══════════════ */

    @Test
    @DisplayName("el cupo es un techo: nunca entran más de los permitidos")
    void elCupoSeRespeta() {
        for (int i = 0; i < 10; i++) {
            Long p = crearProducto("Nuevo " + i);
            conFecha(p, hace(1));
        }

        List<Long> nuevos = idsDeCandidatos(candidatos.catalogoNuevo(
                pesos.fronteraCatalogoNuevo(), NADA_EXCLUIDO, pesos.getCatalogoNuevoCupo()));

        assertThat(nuevos)
                .as("diez disponibles, el cupo deja pasar %d", pesos.getCatalogoNuevoCupo())
                .hasSize(pesos.getCatalogoNuevoCupo());
    }

    @Test
    @DisplayName("con menos productos nuevos que el cupo no se inventa ninguno")
    void noSeInventanCandidatos() {
        Long unico = crearProducto("El unico nuevo");
        conFecha(unico, hace(1));

        assertThat(idsDeCandidatos(candidatos.catalogoNuevo(
                pesos.fronteraCatalogoNuevo(), NADA_EXCLUIDO, 5)))
                .containsExactly(unico);
    }

    @Test
    @DisplayName("sin productos nuevos, el carrusel es exactamente el de antes")
    void sinNovedadesNoCambiaNada() {
        /*
         * La condición de no regresión: quien no dé de alta nada ve el sistema
         * que ya tenía. El catálogo nuevo no puede ser un impuesto sobre el
         * resto.
         */
        List<Long> sinNuevos = idsDe(recomendador.populares(sujeto, 12, new LinkedHashSet<>()));

        assertThat(sinNuevos).as("el módulo sigue sirviendo").isNotEmpty();
        assertThat(servidas.findAll())
                .extracting(RecomendacionServida::getRazon)
                .as("ninguna recomendación viene por la vía nueva")
                .doesNotContain(RazonRecomendacion.NEW_ARRIVAL);
    }

    @Test
    @DisplayName("el catálogo nuevo no puede copar el carrusel")
    void nuncaOcupaElCienPorCien() {
        for (int i = 0; i < 10; i++) {
            Long p = crearProducto("Nuevo " + i);
            conFecha(p, hace(1));
        }

        recomendador.populares(sujeto, 12, new LinkedHashSet<>());

        List<RecomendacionServida> anotadas = servidas.findAll();
        long porNovedad = anotadas.stream()
                .filter(r -> r.getRazon() == RazonRecomendacion.NEW_ARRIVAL).count();

        assertThat(anotadas).isNotEmpty();
        assertThat(porNovedad)
                .as("con diez disponibles tiene que entrar alguno; si no, el cupo"
                        + " no acota nada y esta prueba pasaría en vacío")
                .isPositive()
                .isLessThanOrEqualTo(pesos.getCatalogoNuevoCupo());
        assertThat(porNovedad)
                .as("nunca el carrusel entero")
                .isLessThan(anotadas.size());
    }

    /* ══════════════ Que pase por el ranker ══════════════ */

    @Test
    @DisplayName("lo que entra por novedad queda anotado con su propia razón")
    void laFuenteSeDistingue() {
        /*
         * Sin razón propia no se puede medir si esta puerta sirve de algo, que
         * es lo que hay que poder responder antes de ampliarla. Anotarlo como
         * POPULAR —un producto nuevo no puede ser popular— habría hecho la
         * medición imposible y de paso habría sido mentira.
         */
        Long nuevo = crearProductoCompleto("Nuevo con buena ficha");
        conFecha(nuevo, hace(1));

        recomendador.populares(sujeto, 12, new LinkedHashSet<>());

        assertThat(servidas.findAll())
                .filteredOn(r -> r.getItemId().equals(nuevo))
                .as("si esto sale vacío, el cupo es decorativo y la prueba pasaría en vacío")
                .isNotEmpty()
                .allSatisfy(r -> {
                    assertThat(r.getRazon()).isEqualTo(RazonRecomendacion.NEW_ARRIVAL);
                    assertThat(r.getRankerVersion())
                            .as("pasó por el ranker: lleva su versión")
                            .isNotBlank();
                });
    }

    @Test
    @DisplayName("la selección es determinista")
    void mismoContextoMismaSeleccion() {
        /*
         * Sin esto no se podría depurar nada ni comparar dos ejecuciones. No hay
         * aleatoriedad en ninguna parte del camino: el orden sale de la fecha,
         * del score y de los pesos, que no cambian entre llamadas.
         */
        for (int i = 0; i < 5; i++) {
            Long p = crearProducto("Nuevo " + i);
            conFecha(p, hace(i + 1));
        }

        List<Long> primera = idsDe(recomendador.populares(sujeto, 12, new LinkedHashSet<>()));
        List<Long> segunda = idsDe(recomendador.populares(sujeto, 12, new LinkedHashSet<>()));

        assertThat(segunda).isEqualTo(primera);
    }

    /* ══════════════ Utilidades ══════════════ */

    private static org.assertj.core.data.TemporalUnitOffset within(long millis) {
        return new org.assertj.core.data.TemporalUnitWithinOffset(millis, ChronoUnit.MILLIS);
    }

    private List<Long> idsDe(Carrusel carrusel) {
        return carrusel == null ? List.of() : carrusel.items().stream().map(p -> p.id()).toList();
    }

    private List<Long> idsDeCandidatos(List<Candidato> lista) {
        return lista.stream().map(Candidato::getItemId).toList();
    }

    private Instant hace(int dias) {
        return Instant.now().minus(dias, ChronoUnit.DAYS);
    }

    private void conFecha(Long producto, Instant cuando) {
        jdbc.update("UPDATE catalogo.producto SET creado_en = ? WHERE id = ?",
                Timestamp.from(cuando), producto);
    }

    private Long crearCategoria(String slug) {
        jdbc.update("INSERT INTO catalogo.categoria (name, slug, description)"
                + " VALUES (?, ?, 'IT')", slug, slug);
        return jdbc.queryForObject("SELECT id FROM catalogo.categoria WHERE slug = ?",
                Long.class, slug);
    }

    /** Alta por SQL: nace SIN fecha, como los históricos. */
    private Long crearProducto(String nombre) {
        String unico = nombre + " " + UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.producto"
                + " (name, description, precio, stock, categoria_id, estado_moderacion)"
                + " VALUES (?, 'IT', ?, 10, ?, 'APROBADO')",
                unico, new BigDecimal("100.00"), categoria);
        return jdbc.queryForObject("SELECT id FROM catalogo.producto WHERE name = ?",
                Long.class, unico);
    }

    /** Con imágenes y atributos: una ficha completa puntúa mejor. */
    private Long crearProductoCompleto(String nombre) {
        Long id = crearProducto(nombre);
        for (int i = 0; i < 4; i++) {
            jdbc.update("INSERT INTO catalogo.producto_imagen (producto_id, url, posicion)"
                    + " VALUES (?, ?, ?)", id, "https://ejemplo/" + id + "-" + i + ".jpg", i);
        }
        return id;
    }

    /**
     * Alta por JPA de verdad, que es lo único que prueba el {@code @PrePersist}.
     *
     * <p>La primera versión de esto insertaba por SQL con {@code now()} y no
     * probaba nada: habría pasado igual sin el callback, porque quien ponía la
     * fecha era Postgres. Tiene que pasar por la entidad, que es donde vive el
     * mecanismo bajo prueba.
     */
    private Long crearProductoPorJpa(String nombre) {
        Producto guardado = productos.save(Producto.builder()
                .name(nombre + " " + UUID.randomUUID())
                .description("IT")
                .precio(new BigDecimal("100.00"))
                .stock(10)
                .categoria(categorias.findById(categoria).orElseThrow())
                .estadoModeracion(EstadoModeracion.APROBADO)
                .build());
        return guardado.getId();
    }

    private UUID crearSujeto() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.sujeto (id) VALUES (?)", id);
        return id;
    }
}
