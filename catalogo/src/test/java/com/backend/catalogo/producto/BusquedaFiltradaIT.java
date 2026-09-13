package com.backend.catalogo.producto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.backend.catalogo.PruebaIntegracion;
import com.backend.catalogo.producto.FiltroProductos.Orden;
import com.backend.catalogo.producto.dto.ProductoDtos.Facetas;
import com.backend.catalogo.producto.dto.ProductoDtos.PaginaResponse;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;

/**
 * Filtrar, ordenar y paginar sobre TODO el catálogo, en la base.
 *
 * <p>Lo que se defiende: la base hace el trabajo. Los filtros operan sobre el
 * conjunto entero antes de paginar, el orden es determinista, los atributos se
 * combinan con AND entre códigos, y las facetas cuentan sobre todos los
 * resultados y no sobre la página. Antes de este bloque la categoría filtraba
 * solo los doce visibles y la búsqueda no filtraba nada.
 *
 * <p>NO es transaccional: crea catálogo propio con nombres y códigos que no
 * chocan con la semilla, y lo limpia al terminar.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Búsqueda y filtrado con facetas")
class BusquedaFiltradaIT extends PruebaIntegracion {

    private static final String SLUG = "filt-laptops";
    private static final String SLUG_OTRA = "filt-monitores";
    private static final String COD_RAM = "filt_ram";
    private static final String COD_ALM = "filt_alm";

    @Autowired
    private ProductoService servicio;

    @Autowired
    private JdbcTemplate jdbc;

    private Long categoria;
    private Long otraCategoria;
    private Long lenovo;
    private Long asus;
    private Long hp;

    @BeforeEach
    void prepararCatalogo() {
        limpiarLoNuestro();
        categoria = crearCategoria(SLUG);
        otraCategoria = crearCategoria(SLUG_OTRA);
        lenovo = crearMarca("FILT-Lenovo");
        asus = crearMarca("FILT-ASUS");
        hp = crearMarca("FILT-HP");
        crearAtributo(COD_RAM, "RAM (filtro)");
        crearAtributo(COD_ALM, "Almacenamiento (filtro)");
    }

    @AfterEach
    void restaurar() {
        limpiarLoNuestro();
    }

    private void limpiarLoNuestro() {
        jdbc.update("DELETE FROM catalogo.producto_atributo WHERE producto_id IN"
                + " (SELECT id FROM catalogo.producto WHERE categoria_id IN"
                + " (SELECT id FROM catalogo.categoria WHERE slug IN (?, ?)))", SLUG, SLUG_OTRA);
        jdbc.update("DELETE FROM catalogo.producto WHERE categoria_id IN"
                + " (SELECT id FROM catalogo.categoria WHERE slug IN (?, ?))", SLUG, SLUG_OTRA);
        jdbc.update("DELETE FROM catalogo.categoria WHERE slug IN (?, ?)", SLUG, SLUG_OTRA);
        jdbc.update("DELETE FROM catalogo.marca WHERE name LIKE 'FILT-%'");
        jdbc.update("DELETE FROM catalogo.atributo WHERE codigo IN (?, ?)", COD_RAM, COD_ALM);
    }

    /* ══════════════ Compatibilidad ══════════════ */

    @Nested
    @DisplayName("Compatibilidad")
    class Compatibilidad {

        @Test
        @DisplayName("sin filtros ni texto, devuelve la vitrina de la categoría")
        void sinFiltrosDevuelveTodo() {
            crearProducto(categoria, "Laptop A", "1000", 5, lenovo);
            crearProducto(categoria, "Laptop B", "2000", 5, asus);
            crearProducto(otraCategoria, "Monitor X", "800", 5, hp);

            PaginaResponse<ProductoResponse> r = servicio.buscar(null, SLUG, filtroVacio(), 0, 12);

            assertThat(r.totalElements()).isEqualTo(2);
            assertThat(r.content()).allSatisfy(
                    p -> assertThat(p.categoriaId()).isEqualTo(categoria));
        }

        @Test
        @DisplayName("solo texto, busca por nombre sobre todo el catálogo")
        void soloTexto() {
            crearProducto(categoria, "Laptop zqxwmega gamer", "1000", 5, lenovo);
            crearProducto(categoria, "Teclado", "50", 5, lenovo);

            assertThat(servicio.buscar("zqxwmega", null, filtroVacio(), 0, 12).totalElements())
                    .isEqualTo(1);
        }
    }

    /* ══════════════ Precio ══════════════ */

    @Nested
    @DisplayName("Precio")
    class Precio {

        @Test
        @DisplayName("rango, mínimo y máximo, sobre el conjunto entero")
        void rangoDePrecio() {
            crearProducto(categoria, "Barata", "500", 5, lenovo);
            crearProducto(categoria, "Media", "1500", 5, lenovo);
            crearProducto(categoria, "Cara", "3500", 5, lenovo);

            assertThat(buscar(new FiltroProductos(new BigDecimal("1000"), new BigDecimal("3000"),
                    null, null, false, Orden.RELEVANCIA))).containsExactly("Media");
            assertThat(buscar(new FiltroProductos(new BigDecimal("1000"), null,
                    null, null, false, Orden.RELEVANCIA)))
                    .containsExactlyInAnyOrder("Media", "Cara");
            assertThat(buscar(new FiltroProductos(null, new BigDecimal("1000"),
                    null, null, false, Orden.RELEVANCIA))).containsExactly("Barata");
        }

        @Test
        @DisplayName("los límites son inclusivos")
        void limitesInclusivos() {
            crearProducto(categoria, "Justa", "1000", 5, lenovo);
            assertThat(buscar(new FiltroProductos(new BigDecimal("1000"), new BigDecimal("1000"),
                    null, null, false, Orden.RELEVANCIA))).containsExactly("Justa");
        }
    }

    /* ══════════════ Marca ══════════════ */

    @Nested
    @DisplayName("Marca")
    class Marca {

        @Test
        @DisplayName("una marca y varias marcas")
        void filtroPorMarca() {
            crearProducto(categoria, "De Lenovo", "1000", 5, lenovo);
            crearProducto(categoria, "De ASUS", "1000", 5, asus);
            crearProducto(categoria, "De HP", "1000", 5, hp);

            assertThat(buscar(marcas(lenovo))).containsExactly("De Lenovo");
            assertThat(buscar(marcas(lenovo, asus)))
                    .containsExactlyInAnyOrder("De Lenovo", "De ASUS");
        }
    }

    /* ══════════════ Atributos ══════════════ */

    @Nested
    @DisplayName("Atributos")
    class Atributos {

        @Test
        @DisplayName("un atributo filtra por su valor")
        void unAtributo() {
            Long a = crearProducto(categoria, "16GB", "1000", 5, lenovo);
            ponerAtributo(a, COD_RAM, "16");
            Long b = crearProducto(categoria, "8GB", "1000", 5, lenovo);
            ponerAtributo(b, COD_RAM, "8");

            assertThat(buscar(atributos(COD_RAM + ":16"))).containsExactly("16GB");
        }

        @Test
        @DisplayName("dos atributos distintos se exigen AMBOS")
        void dosAtributosAND() {
            Long completo = crearProducto(categoria, "16GB+512", "1000", 5, lenovo);
            ponerAtributo(completo, COD_RAM, "16");
            ponerAtributo(completo, COD_ALM, "512");

            Long soloRam = crearProducto(categoria, "16GB+256", "1000", 5, lenovo);
            ponerAtributo(soloRam, COD_RAM, "16");
            ponerAtributo(soloRam, COD_ALM, "256");

            assertThat(buscar(atributos(COD_RAM + ":16", COD_ALM + ":512")))
                    .as("solo el que cumple los dos").containsExactly("16GB+512");
        }

        @Test
        @DisplayName("dos valores del mismo atributo son alternativa")
        void dosValoresMismoAtributoOR() {
            Long r16 = crearProducto(categoria, "16GB", "1000", 5, lenovo);
            ponerAtributo(r16, COD_RAM, "16");
            Long r32 = crearProducto(categoria, "32GB", "1000", 5, lenovo);
            ponerAtributo(r32, COD_RAM, "32");
            Long r8 = crearProducto(categoria, "8GB", "1000", 5, lenovo);
            ponerAtributo(r8, COD_RAM, "8");

            assertThat(buscar(atributos(COD_RAM + ":16", COD_RAM + ":32")))
                    .as("cualquiera de los dos valores del mismo código")
                    .containsExactlyInAnyOrder("16GB", "32GB");
        }
    }

    /* ══════════════ Disponibilidad ══════════════ */

    @Nested
    @DisplayName("Disponibilidad")
    class Disponibilidad {

        @Test
        @DisplayName("soloDisponibles deja fuera lo agotado")
        void soloDisponibles() {
            crearProducto(categoria, "Con stock", "1000", 5, lenovo);
            crearProducto(categoria, "Agotado", "1000", 0, lenovo);

            assertThat(buscar(new FiltroProductos(null, null, null, null, true, Orden.RELEVANCIA)))
                    .containsExactly("Con stock");
            assertThat(buscar(filtroVacio()))
                    .containsExactlyInAnyOrder("Con stock", "Agotado");
        }
    }

    /* ══════════════ Orden ══════════════ */

    @Nested
    @DisplayName("Orden")
    class OrdenTest {

        @Test
        @DisplayName("precio ascendente y descendente")
        void ordenPorPrecio() {
            crearProducto(categoria, "Media", "1500", 5, lenovo);
            crearProducto(categoria, "Barata", "500", 5, lenovo);
            crearProducto(categoria, "Cara", "3500", 5, lenovo);

            assertThat(buscar(orden(Orden.PRECIO_ASC)))
                    .containsExactly("Barata", "Media", "Cara");
            assertThat(buscar(orden(Orden.PRECIO_DESC)))
                    .containsExactly("Cara", "Media", "Barata");
        }

        @Test
        @DisplayName("nombre ascendente y descendente")
        void ordenPorNombre() {
            crearProducto(categoria, "Zeta", "1000", 5, lenovo);
            crearProducto(categoria, "Alfa", "1000", 5, lenovo);

            assertThat(buscar(orden(Orden.NOMBRE_ASC))).containsExactly("Alfa", "Zeta");
            assertThat(buscar(orden(Orden.NOMBRE_DESC))).containsExactly("Zeta", "Alfa");
        }

        @Test
        @DisplayName("con precios iguales, el desempate por id es determinista")
        void desempateDeterminista() {
            crearProducto(categoria, "Primera", "1000", 5, lenovo);
            crearProducto(categoria, "Segunda", "1000", 5, lenovo);
            crearProducto(categoria, "Tercera", "1000", 5, lenovo);

            List<String> a = buscar(orden(Orden.PRECIO_ASC));
            List<String> b = buscar(orden(Orden.PRECIO_ASC));
            assertThat(a).isEqualTo(b).containsExactly("Primera", "Segunda", "Tercera");
        }
    }

    /* ══════════════ Combinación ══════════════ */

    @Nested
    @DisplayName("Combinación")
    class Combinacion {

        @Test
        @DisplayName("marca + precio + atributo + disponibilidad juntos")
        void todoJunto() {
            Long objetivo = crearProducto(categoria, "El bueno", "1500", 5, lenovo);
            ponerAtributo(objetivo, COD_RAM, "16");

            Long precioAlto = crearProducto(categoria, "Caro", "5000", 5, lenovo);
            ponerAtributo(precioAlto, COD_RAM, "16");
            Long otraMarca = crearProducto(categoria, "De ASUS", "1500", 5, asus);
            ponerAtributo(otraMarca, COD_RAM, "16");
            crearProducto(categoria, "Sin ram", "1500", 5, lenovo);
            Long agotado = crearProducto(categoria, "Agotado", "1500", 0, lenovo);
            ponerAtributo(agotado, COD_RAM, "16");

            FiltroProductos f = new FiltroProductos(new BigDecimal("1000"), new BigDecimal("2000"),
                    List.of(lenovo), List.of(COD_RAM + ":16"), true, Orden.RELEVANCIA);

            assertThat(buscar(f)).containsExactly("El bueno");
        }
    }

    /* ══════════════ Paginación ══════════════ */

    @Nested
    @DisplayName("Paginación")
    class Paginacion {

        @Test
        @DisplayName("total del conjunto filtrado, páginas sin solape ni huecos")
        void paginacionCompleta() {
            for (int i = 0; i < 19; i++) {
                Long p = crearProducto(categoria, String.format("P%02d", i), "1000", 5, lenovo);
                ponerAtributo(p, COD_RAM, "16");
            }
            for (int i = 0; i < 5; i++) {
                crearProducto(categoria, "Otro " + i, "1000", 5, asus);
            }

            FiltroProductos f = new FiltroProductos(null, null, List.of(lenovo),
                    List.of(COD_RAM + ":16"), false, Orden.NOMBRE_ASC);

            PaginaResponse<ProductoResponse> p0 = servicio.buscar(null, SLUG, f, 0, 12);
            PaginaResponse<ProductoResponse> p1 = servicio.buscar(null, SLUG, f, 1, 12);

            assertThat(p0.totalElements()).as("19 filtrados, no 24").isEqualTo(19);
            assertThat(p0.content()).hasSize(12);
            assertThat(p1.content()).as("los 7 restantes").hasSize(7);

            List<String> nombres = new ArrayList<>();
            p0.content().forEach(x -> nombres.add(x.name()));
            p1.content().forEach(x -> nombres.add(x.name()));
            assertThat(nombres).as("sin solape ni huecos").doesNotHaveDuplicates().hasSize(19);
            assertThat(nombres.get(0)).isEqualTo("P00");
            assertThat(nombres.get(18)).isEqualTo("P18");
        }
    }

    /* ══════════════ Facetas ══════════════ */

    @Nested
    @DisplayName("Facetas")
    class FacetasTest {

        @Test
        @DisplayName("los conteos son del conjunto entero, no de la página")
        void conteosSobreTodo() {
            for (int i = 0; i < 20; i++) {
                Long p = crearProducto(categoria, "Lenovo " + i, "1000", 5, lenovo);
                ponerAtributo(p, COD_RAM, "16");
            }
            for (int i = 0; i < 8; i++) {
                Long p = crearProducto(categoria, "ASUS " + i, "1000", 5, asus);
                ponerAtributo(p, COD_RAM, "8");
            }
            crearProducto(categoria, "Agotado", "1000", 0, hp);

            Facetas f = servicio.facetas(null, SLUG, filtroVacio());

            assertThat(f.total()).isEqualTo(29);
            assertThat(f.disponibles()).isEqualTo(28);
            assertThat(f.marcas())
                    .anySatisfy(m -> { assertThat(m.nombre()).isEqualTo("FILT-Lenovo");
                                       assertThat(m.conteo()).isEqualTo(20); })
                    .anySatisfy(m -> { assertThat(m.nombre()).isEqualTo("FILT-ASUS");
                                       assertThat(m.conteo()).isEqualTo(8); });
            assertThat(f.atributos()).anySatisfy(a -> {
                assertThat(a.codigo()).isEqualTo(COD_RAM);
                assertThat(a.valores()).anySatisfy(v -> {
                    assertThat(v.valor()).isEqualTo("16");
                    assertThat(v.conteo()).isEqualTo(20);
                });
            });
        }

        @Test
        @DisplayName("las facetas respetan los filtros ya aplicados")
        void facetasRespetanFiltro() {
            for (int i = 0; i < 5; i++) {
                Long p = crearProducto(categoria, "Lenovo " + i, "1000", 5, lenovo);
                ponerAtributo(p, COD_RAM, "16");
            }
            for (int i = 0; i < 3; i++) {
                Long p = crearProducto(categoria, "ASUS " + i, "1000", 5, asus);
                ponerAtributo(p, COD_RAM, "16");
            }

            Facetas f = servicio.facetas(null, SLUG, marcas(lenovo));

            assertThat(f.total()).as("solo los Lenovo entran en la faceta").isEqualTo(5);
            assertThat(f.atributos()).anySatisfy(a -> assertThat(a.valores())
                    .anySatisfy(v -> assertThat(v.conteo()).isEqualTo(5)));
        }

        @Test
        @DisplayName("conjunto vacío, facetas vacías sin reventar")
        void facetasVacias() {
            crearProducto(categoria, "Uno", "1000", 5, lenovo);

            Facetas f = servicio.facetas("noexisteestetermino", null, filtroVacio());

            assertThat(f.total()).isZero();
            assertThat(f.marcas()).isEmpty();
            assertThat(f.atributos()).isEmpty();
        }
    }

    /* ══════════════ Utilidades ══════════════ */

    private FiltroProductos filtroVacio() {
        return new FiltroProductos(null, null, null, null, false, Orden.RELEVANCIA);
    }

    private FiltroProductos marcas(Long... ids) {
        return new FiltroProductos(null, null, List.of(ids), null, false, Orden.RELEVANCIA);
    }

    private FiltroProductos atributos(String... tokens) {
        return new FiltroProductos(null, null, null, List.of(tokens), false, Orden.RELEVANCIA);
    }

    private FiltroProductos orden(Orden o) {
        return new FiltroProductos(null, null, null, null, false, o);
    }

    private List<String> buscar(FiltroProductos filtro) {
        return servicio.buscar(null, SLUG, filtro, 0, 50)
                .content().stream().map(ProductoResponse::name).toList();
    }

    private Long crearCategoria(String slug) {
        jdbc.update("INSERT INTO catalogo.categoria (name, slug, description)"
                + " VALUES (?, ?, 'IT')", slug, slug);
        return jdbc.queryForObject("SELECT id FROM catalogo.categoria WHERE slug = ?",
                Long.class, slug);
    }

    private Long crearMarca(String nombre) {
        jdbc.update("INSERT INTO catalogo.marca (name, descripcion) VALUES (?, 'IT')", nombre);
        return jdbc.queryForObject("SELECT id FROM catalogo.marca WHERE name = ?",
                Long.class, nombre);
    }

    private void crearAtributo(String codigo, String nombre) {
        jdbc.update("INSERT INTO catalogo.atributo (codigo, nombre, tipo)"
                + " VALUES (?, ?, 'TEXTO')", codigo, nombre);
    }

    private Long crearProducto(Long cat, String nombre, String precio, int stock, Long marca) {
        jdbc.update("INSERT INTO catalogo.producto"
                + " (name, description, precio, stock, categoria_id, marca_id, estado_moderacion)"
                + " VALUES (?, 'IT', ?, ?, ?, ?, 'APROBADO')",
                nombre, new BigDecimal(precio), stock, cat, marca);
        return jdbc.queryForObject("SELECT id FROM catalogo.producto WHERE name = ? AND"
                + " categoria_id = ?", Long.class, nombre, cat);
    }

    private void ponerAtributo(Long producto, String codigo, String valor) {
        Long atributoId = jdbc.queryForObject(
                "SELECT id FROM catalogo.atributo WHERE codigo = ?", Long.class, codigo);
        jdbc.update("INSERT INTO catalogo.producto_atributo (producto_id, atributo_id, valor)"
                + " VALUES (?, ?, ?)", producto, atributoId, valor);
    }
}
