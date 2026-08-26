package com.backend.catalogo.producto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import com.backend.catalogo.PruebaIntegracion;
import com.backend.catalogo.categoria.Categoria;
import com.backend.catalogo.categoria.CategoriaRepository;
import com.backend.catalogo.marca.Marca;
import com.backend.catalogo.marca.MarcaRepository;
import com.backend.catalogo.shared.seguridad.ContextoRls;

/**
 * Las consultas del panel de descuentos, contra PostgreSQL de verdad.
 *
 * <p>Estas dos no se pueden verificar con mocks, y no por pereza: llevan filtros
 * opcionales del tipo {@code :param IS NULL OR …}, comparan un parámetro contra
 * literales para elegir rama, y agregan con {@code CASE WHEN}. Todo eso es una
 * cadena de texto hasta que Hibernate la traduce, así que <b>compila igual esté
 * bien o mal</b>; la única forma de saber si funciona es ejecutarla.
 *
 * <p>Y lo que deciden importa: qué productos ve el administrador cuando va a
 * aplicar un descuento en lote, y cuántos dice cada pestaña que hay.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Consultas del panel de descuentos")
class DescuentosRepositoryIT extends PruebaIntegracion {

    @Autowired
    private ProductoRepository productos;
    @Autowired
    private CategoriaRepository categorias;
    @Autowired
    private MarcaRepository marcas;

    private Instant ahora;
    private Long categoriaId;
    private Long marcaId;

    @BeforeEach
    void preparar() {
        ahora = Instant.now();

        // Como sistema: `producto` tiene Row Level Security y estas altas no
        // vienen de ningún usuario en curso.
        ContextoRls.comoSistema(() -> {
            productos.deleteAll();
            marcas.deleteAll();
            categorias.deleteAll();

            Categoria categoria = categorias.save(Categoria.builder()
                    .name("Monitores").slug("monitores").description("d").build());
            categoriaId = categoria.getId();

            Marca marca = marcas.save(Marca.builder()
                    .name("LG").descripcion("d").categoria(categoria).build());
            marcaId = marca.getId();

            // Activo: vigencia abierta por los dos lados.
            guardar("Activo con rango", categoria, marca, new BigDecimal("80.00"),
                    ahora.minus(1, ChronoUnit.DAYS), ahora.plus(1, ChronoUnit.DAYS));

            // Activo también: con descuento y sin fechas, vale siempre.
            guardar("Activo sin fechas", categoria, marca, new BigDecimal("70.00"), null, null);

            // Programado: la vigencia empieza mañana.
            guardar("Programado", categoria, marca, new BigDecimal("60.00"),
                    ahora.plus(1, ChronoUnit.DAYS), ahora.plus(2, ChronoUnit.DAYS));

            // Inactivo por vencido.
            guardar("Vencido", categoria, marca, new BigDecimal("50.00"),
                    ahora.minus(10, ChronoUnit.DAYS), ahora.minus(1, ChronoUnit.DAYS));

            // Inactivo por no tener descuento.
            guardar("Sin descuento", categoria, marca, null, null, null);
        });
    }

    private void guardar(String nombre, Categoria categoria, Marca marca,
            BigDecimal precioOferta, Instant inicio, Instant fin) {
        productos.save(Producto.builder()
                .name(nombre).description("d")
                .precio(new BigDecimal("100.00")).stock(5)
                .precioOferta(precioOferta).ofertaInicio(inicio).ofertaFin(fin)
                .categoria(categoria).marca(marca)
                .estadoModeracion(EstadoModeracion.APROBADO)
                .build());
    }

    /**
     * `ContextoRls.comoSistema(Callable)` declara `Exception`, y en una prueba
     * una excepción comprobada solo añade ruido: aquí cualquier fallo debe
     * hacer fallar el test, no obligar a un try/catch en cada llamada.
     */
    private <T> T comoSistema(java.util.concurrent.Callable<T> bloque) {
        try {
            return ContextoRls.comoSistema(bloque);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private List<String> nombresDe(String estado, Long categoria, Long marca, String texto) {
        return comoSistema(() -> productos
                .listarParaDescuentos(estado, categoria, marca, texto, ahora, PageRequest.of(0, 50))
                .getContent().stream().map(Producto::getName).sorted().toList());
    }

    /* ══════════════ Estado del descuento ══════════════ */

    @Test
    @DisplayName("«activo» son los que tienen descuento vigente AHORA")
    void activos() {
        assertThat(nombresDe("activo", null, null, null))
                .containsExactly("Activo con rango", "Activo sin fechas");
    }

    @Test
    @DisplayName("«programado» son los que aún no han empezado")
    void programados() {
        assertThat(nombresDe("programado", null, null, null)).containsExactly("Programado");
    }

    @Test
    @DisplayName("«inactivo» son los vencidos y los que no tienen descuento")
    void inactivos() {
        assertThat(nombresDe("inactivo", null, null, null))
                .containsExactly("Sin descuento", "Vencido");
    }

    @Test
    @DisplayName("las tres secciones suman el catálogo, sin solapes ni huecos")
    void lasTresSeccionesCubrenTodo() {
        /*
         * Es la invariante de la clasificación: cada producto cae en una sección
         * y solo en una. Si se solaparan, el administrador vería el mismo
         * producto en dos pestañas; si quedara un hueco, habría productos que no
         * aparecen en ninguna y a los que no se puede llegar.
         */
        List<String> todos = nombresDe("todos", null, null, null);
        List<String> porSecciones = java.util.stream.Stream.of(
                nombresDe("activo", null, null, null),
                nombresDe("programado", null, null, null),
                nombresDe("inactivo", null, null, null))
                .flatMap(List::stream).sorted().toList();

        assertThat(porSecciones).containsExactlyElementsOf(todos);
        assertThat(todos).hasSize(5);
    }

    /* ══════════════ Filtros opcionales ══════════════ */

    @Test
    @DisplayName("un filtro nulo NO filtra: es lo que hace que sean opcionales")
    void filtrosNulosNoFiltran() {
        // `:param IS NULL OR …` es el idioma que evita cuatro consultas casi
        // iguales, y es exactamente lo que hay que comprobar contra la base.
        assertThat(nombresDe("todos", null, null, null)).hasSize(5);
    }

    @Test
    @DisplayName("filtrar por categoría y marca deja pasar lo que corresponde")
    void filtroPorCategoriaYMarca() {
        assertThat(nombresDe("todos", categoriaId, marcaId, null)).hasSize(5);

        // Una categoría que no existe no devuelve nada, en vez de ignorarse.
        assertThat(nombresDe("todos", categoriaId + 999, null, null)).isEmpty();
        assertThat(nombresDe("todos", null, marcaId + 999, null)).isEmpty();
    }

    @Test
    @DisplayName("la búsqueda por texto no distingue mayúsculas")
    void busquedaPorTexto() {
        assertThat(nombresDe("todos", null, null, "VENCIDO")).containsExactly("Vencido");
        assertThat(nombresDe("todos", null, null, "activo"))
                .containsExactly("Activo con rango", "Activo sin fechas");
    }

    @Test
    @DisplayName("los filtros se combinan entre sí")
    void filtrosCombinados() {
        assertThat(nombresDe("activo", categoriaId, marcaId, "rango"))
                .containsExactly("Activo con rango");
    }

    /* ══════════════ Conteos ══════════════ */

    @Test
    @DisplayName("los conteos cuadran con lo que devuelve cada sección")
    void conteos() {
        var conteo = comoSistema(() -> productos.contarPorEstadoDeDescuento(ahora));

        assertThat(conteo.getActivos()).isEqualTo(2);
        assertThat(conteo.getProgramados()).isEqualTo(1);
        assertThat(conteo.getInactivos()).isEqualTo(2);
        assertThat(conteo.getTodos()).isEqualTo(5);

        // Y la suma de las tres es el total: la misma invariante de arriba,
        // comprobada ahora sobre los números que ven las pestañas.
        assertThat(conteo.getActivos() + conteo.getProgramados() + conteo.getInactivos())
                .isEqualTo(conteo.getTodos());
    }

    @Test
    @DisplayName("sin productos, los conteos son cero y no nulos")
    void conteosSinProductos() {
        // `SUM` sobre cero filas devuelve null: sin el COALESCE, la pantalla
        // recibiría un hueco en vez de un cero.
        ContextoRls.comoSistema(() -> productos.deleteAll());

        var conteo = comoSistema(() -> productos.contarPorEstadoDeDescuento(ahora));

        assertThat(conteo.getActivos()).isZero();
        assertThat(conteo.getProgramados()).isZero();
        assertThat(conteo.getInactivos()).isZero();
        assertThat(conteo.getTodos()).isZero();
    }

    /* ══════════════ Paginación ══════════════ */

    @Test
    @DisplayName("la página trae su trozo y el total de la consulta entera")
    void paginacion() {
        var pagina = comoSistema(() -> productos.listarParaDescuentos(
                "todos", null, null, null, ahora, PageRequest.of(0, 2)));

        assertThat(pagina.getContent()).hasSize(2);
        assertThat(pagina.getTotalElements()).isEqualTo(5);
        assertThat(pagina.getTotalPages()).isEqualTo(3);
    }
}
