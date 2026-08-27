package com.backend.catalogo.producto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import com.backend.catalogo.PruebaIntegracion;
import com.backend.catalogo.categoria.Categoria;
import com.backend.catalogo.categoria.CategoriaRepository;
import com.backend.catalogo.marca.MarcaRepository;

/**
 * Integración contra PostgreSQL real: verifica que buscarConImagenes()
 * carga la galería de imágenes con JOIN FETCH y que listarPorCategoriaSlug()
 * pagina correctamente.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
class ProductoRepositoryIT extends PruebaIntegracion {

    @Autowired
    private ProductoRepository productoRepositorio;

    @Autowired
    private CategoriaRepository categoriaRepositorio;

    @Autowired
    private ProductoImagenRepository imagenRepositorio;

    @Autowired
    private MarcaRepository marcaRepositorio;

    /**
     * Se borra en orden de dependencia, y las marcas NO sobran.
     *
     * <p>La base es la misma para todas las clases de integración, así que aquí
     * pueden quedar marcas creadas por otra —{@code DescuentosRepositoryIT} deja
     * una—, y {@code marca} apunta a {@code categoria}. Sin borrarlas antes, el
     * borrado de categorías chocaba con {@code fk_marca_categoria}.
     */
    @BeforeEach
    void limpiar() {
        imagenRepositorio.deleteAll();
        productoRepositorio.deleteAll();
        marcaRepositorio.deleteAll();
        categoriaRepositorio.deleteAll();
    }

    private Categoria crearCategoria(String nombre, String slug) {
        return categoriaRepositorio.save(Categoria.builder()
                .name(nombre).slug(slug).description("Test").build());
    }

    private Producto crearProducto(Categoria cat, String nombre) {
        return productoRepositorio.save(Producto.builder()
                .name(nombre).description("Desc").precio(new BigDecimal("100.00"))
                .stock(10).categoria(cat).estadoModeracion(EstadoModeracion.APROBADO)
                .build());
    }

    @Test
    @DisplayName("buscarConImagenes carga la galería con JOIN FETCH")
    void buscarConImagenesCargaGaleria() {
        Categoria cat = crearCategoria("Laptops", "laptops");
        Producto p = crearProducto(cat, "Laptop Test");

        imagenRepositorio.save(ProductoImagen.builder()
                .producto(p).url("https://img.example/1.jpg").posicion(0).build());
        imagenRepositorio.save(ProductoImagen.builder()
                .producto(p).url("https://img.example/2.jpg").posicion(1).build());

        List<Producto> resultado = productoRepositorio.buscarConImagenes(List.of(p.getId()));

        assertThat(resultado).hasSize(1);
        Producto recuperado = resultado.get(0);
        assertThat(recuperado.getImagenes()).hasSize(2);

        // El @OrderBy("posicion ASC") de la entidad: la principal va primero.
        assertThat(recuperado.getImagenes().get(0).getUrl()).isEqualTo("https://img.example/1.jpg");
        assertThat(recuperado.getImagenes().get(1).getUrl()).isEqualTo("https://img.example/2.jpg");

        /*
         * Aquí NO se comprueba `imageUrl`, y es a propósito.
         *
         * Es una columna propia, no un derivado de la galería: la mantiene al día
         * el servicio al crear o editar el producto. Esta prueba guarda por el
         * repositorio, saltándose esa lógica, así que la columna está vacía. Dar
         * por hecho que la galería la rellenaba era la suposición equivocada.
         */
    }

    @Test
    @DisplayName("buscarConImagenes con IDs vacíos devuelve lista vacía")
    void buscarConImagenesVacio() {
        List<Producto> resultado = productoRepositorio.buscarConImagenes(List.of());
        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("listarPorCategoriaSlug pagina correctamente")
    void listarPorCategoriaPaginado() {
        Categoria cat = crearCategoria("Monitores", "monitores");
        for (int i = 0; i < 5; i++) {
            crearProducto(cat, "Monitor " + i);
        }

        var pagina0 = productoRepositorio.listarPorCategoriaSlug(
                "monitores", org.springframework.data.domain.PageRequest.of(0, 2));
        var pagina1 = productoRepositorio.listarPorCategoriaSlug(
                "monitores", org.springframework.data.domain.PageRequest.of(1, 2));

        assertThat(pagina0.getContent()).hasSize(2);
        assertThat(pagina0.getTotalElements()).isEqualTo(5);
        assertThat(pagina0.getTotalPages()).isEqualTo(3);

        assertThat(pagina1.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("listarPorCategoriaSlug solo devuelve productos APROBADOS")
    void soloAprobados() {
        Categoria cat = crearCategoria("Test", "test");
        crearProducto(cat, "Aprobado");

        /*
         * El pendiente lleva `propietarioId`, y no es un detalle: lo exige la
         * restricción `ck_producto_tienda_aprobada` de la V15. Lo de la tienda
         * —sin dueño— no pasa por moderación y por eso solo puede estar
         * APROBADO; únicamente lo de un colaborador puede quedar pendiente.
         * Sin el dueño, el INSERT se rechaza antes de llegar a la consulta.
         */
        Producto pendiente = Producto.builder()
                .name("Pendiente").description("Desc").precio(new BigDecimal("50.00"))
                .stock(5).categoria(cat)
                .propietarioId(99L)
                .estadoModeracion(EstadoModeracion.PENDIENTE).build();
        productoRepositorio.save(pendiente);

        var pagina = productoRepositorio.listarPorCategoriaSlug(
                "test", org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(pagina.getContent()).hasSize(1);
        assertThat(pagina.getContent().get(0).getName()).isEqualTo("Aprobado");
    }
}
