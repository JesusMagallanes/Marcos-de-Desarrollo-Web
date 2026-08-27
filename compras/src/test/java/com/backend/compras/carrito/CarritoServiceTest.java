package com.backend.compras.carrito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backend.compras.carrito.dto.CarritoDtos.CarritoResponse;
import com.backend.compras.envio.TarifaEnvio;
import com.backend.compras.shared.catalogo.CatalogoClient;
import com.backend.compras.shared.catalogo.CatalogoClient.LineaPrecio;
import com.backend.compras.shared.error.ConflictoException;
import com.backend.compras.shared.error.RecursoNoEncontradoException;

import jakarta.persistence.EntityManager;

/**
 * El carrito: lo que se ve, lo que se cobra y —sobre todo— lo que se borra.
 *
 * <p>El borrado tiene su propio bloque porque falló de la peor forma posible:
 * en silencio. Se quitaba el ítem de la colección y se dejaba que el
 * {@code orphanRemoval} de JPA hiciera el resto, y la respuesta se construía
 * desde esa colección <b>en memoria</b>. Si el DELETE no llegaba a tocar
 * ninguna fila, el endpoint devolvía 200 con un carrito sin el producto y el
 * producto reaparecía al recargar.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Carrito")
class CarritoServiceTest {

    private static final Long USUARIO = 42L;

    @Mock
    private CarritoRepository carritos;
    @Mock
    private CarritoItemRepository items;
    @Mock
    private CatalogoClient catalogo;
    @Mock
    private EntityManager entityManager;

    private CarritoService servicio;

    @BeforeEach
    void preparar() {
        // La tarifa de verdad, no un mock: es una función pura y lo que se quiere
        // comprobar es que el total que se cobra lleve el envío.
        TarifaEnvio tarifa = new TarifaEnvio(new BigDecimal("200.00"), new BigDecimal("15.00"));
        servicio = new CarritoService(carritos, items, catalogo, tarifa, entityManager);
    }

    private Carrito carritoCon(CarritoItem... contenido) {
        Carrito carrito = Carrito.builder().id(5L).usuarioId(USUARIO).build();
        for (CarritoItem item : contenido) {
            carrito.agregar(item);
        }
        return carrito;
    }

    private CarritoItem item(long id, long productoId, int cantidad) {
        return CarritoItem.builder().id(id).productoId(productoId).cantidad(cantidad).build();
    }

    private void catalogoResponde(long productoId, String precio, int stock) {
        lenient().when(catalogo.precios(any(), anyList())).thenReturn(
                List.of(new LineaPrecio(productoId, "Monitor LG", null, new BigDecimal(precio), stock)));
    }

    /* ══════════════ Borrar ══════════════ */

    @Test
    @DisplayName("borrar una línea la quita de la base, no solo de la memoria")
    void eliminar() {
        Carrito carrito = carritoCon(item(11L, 300L, 1));
        when(carritos.findByUsuarioId(USUARIO)).thenReturn(Optional.of(carrito));
        when(items.findByIdAndCarritoId(11L, 5L)).thenReturn(Optional.of(item(11L, 300L, 1)));
        when(items.borrarDelCarrito(11L, 5L)).thenReturn(1);
        when(carritos.buscarConItems(USUARIO)).thenReturn(Optional.of(carritoCon()));

        CarritoResponse respuesta = servicio.eliminar(USUARIO, 11L);

        verify(items).borrarDelCarrito(11L, 5L);
        assertThat(respuesta.items()).isEmpty();
    }

    @Test
    @DisplayName("un borrado que no toca ninguna fila DA ERROR, no un 200 optimista")
    void eliminarQueNoBorraFalla() {
        /*
         * Este es el fallo que se estaba arreglando. Antes, si el DELETE no
         * alcanzaba ninguna fila, la respuesta se construía desde la colección
         * en memoria —de la que sí se había quitado el ítem— y el usuario veía
         * un carrito correcto que al recargar volvía a tener el producto.
         *
         * Un borrado que no borra tiene que decirlo.
         */
        Carrito carrito = carritoCon(item(11L, 300L, 1));
        when(carritos.findByUsuarioId(USUARIO)).thenReturn(Optional.of(carrito));
        when(items.findByIdAndCarritoId(11L, 5L)).thenReturn(Optional.of(item(11L, 300L, 1)));
        when(items.borrarDelCarrito(11L, 5L)).thenReturn(0);

        assertThatThrownBy(() -> servicio.eliminar(USUARIO, 11L))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("No se pudo quitar");
    }

    @Test
    @DisplayName("la respuesta se relee de la base, no de la colección en memoria")
    void eliminarReleeDeLaBase() {
        Carrito carrito = carritoCon(item(11L, 300L, 1), item(12L, 301L, 2));
        when(carritos.findByUsuarioId(USUARIO)).thenReturn(Optional.of(carrito));
        when(items.findByIdAndCarritoId(11L, 5L)).thenReturn(Optional.of(item(11L, 300L, 1)));
        when(items.borrarDelCarrito(11L, 5L)).thenReturn(1);

        // Lo que queda de verdad en la base tras el borrado.
        when(carritos.buscarConItems(USUARIO)).thenReturn(Optional.of(carritoCon(item(12L, 301L, 2))));
        catalogoResponde(301L, "50.00", 9);

        CarritoResponse respuesta = servicio.eliminar(USUARIO, 11L);

        // El `clear()` es lo que convierte la consulta en una lectura de verdad:
        // sin él devolvería las entidades que JPA ya tiene, la borrada incluida.
        verify(entityManager).flush();
        verify(entityManager).clear();
        assertThat(respuesta.items()).singleElement()
                .satisfies(i -> assertThat(i.productId()).isEqualTo(301L));
    }

    @Test
    @DisplayName("un id de otro carrito no borra nada: se busca por ítem Y carrito")
    void noSeBorraLoDeOtro() {
        Carrito carrito = carritoCon();
        when(carritos.findByUsuarioId(USUARIO)).thenReturn(Optional.of(carrito));
        when(items.findByIdAndCarritoId(999L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(USUARIO, 999L))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(items, never()).borrarDelCarrito(anyLong(), anyLong());
    }

    @Test
    @DisplayName("vaciar borra todas las líneas con una sentencia")
    void vaciar() {
        when(carritos.findByUsuarioId(USUARIO)).thenReturn(Optional.of(carritoCon(item(11L, 300L, 1))));

        CarritoResponse respuesta = servicio.vaciar(USUARIO);

        verify(items).borrarTodoElCarrito(5L);
        assertThat(respuesta.items()).isEmpty();
        assertThat(respuesta.total()).isEqualByComparingTo("0.00");
    }

    /* ══════════════ Importes ══════════════ */

    @Test
    @DisplayName("por debajo del umbral, el total lleva el envío")
    void totalConEnvio() {
        Carrito carrito = carritoCon(item(11L, 300L, 2));
        catalogoResponde(300L, "50.00", 9);

        CarritoResponse respuesta = servicio.construir(carrito);

        /*
         * Es lo que estaba descuadrado: el carrito enseñaba el total con envío y
         * la pasarela cobraba solo el subtotal. Ahora los dos números salen de
         * aquí.
         */
        assertThat(respuesta.subtotal()).isEqualByComparingTo("100.00");
        assertThat(respuesta.costoEnvio()).isEqualByComparingTo("15.00");
        assertThat(respuesta.total()).isEqualByComparingTo("115.00");
    }

    @Test
    @DisplayName("alcanzado el umbral, el envío es gratis")
    void envioGratis() {
        Carrito carrito = carritoCon(item(11L, 300L, 4));
        catalogoResponde(300L, "50.00", 9);

        CarritoResponse respuesta = servicio.construir(carrito);

        assertThat(respuesta.subtotal()).isEqualByComparingTo("200.00");
        assertThat(respuesta.costoEnvio()).isEqualByComparingTo("0");
        assertThat(respuesta.total()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("un carrito vacío no cobra envío")
    void carritoVacioNoPagaEnvio() {
        // Cobrar el envío de una compra que no existe es lo que hacía que un
        // carrito recién vaciado enseñara 15 soles a pagar.
        CarritoResponse respuesta = servicio.construir(carritoCon());

        assertThat(respuesta.total()).isEqualByComparingTo("0");
        assertThat(respuesta.costoEnvio()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("un producto que desapareció del catálogo se omite en vez de romper la vista")
    void productoDesaparecido() {
        Carrito carrito = carritoCon(item(11L, 300L, 1), item(12L, 999L, 1));
        // Catálogo solo conoce uno de los dos.
        when(catalogo.precios(any(), anyList())).thenReturn(
                List.of(new LineaPrecio(300L, "Monitor LG", null, new BigDecimal("50.00"), 9)));

        CarritoResponse respuesta = servicio.construir(carrito);

        assertThat(respuesta.items()).hasSize(1);
        assertThat(respuesta.subtotal()).isEqualByComparingTo("50.00");
    }

    /* ══════════════ Cantidades ══════════════ */

    @Test
    @DisplayName("no se puede pedir más de lo que hay en stock")
    void cantidadPorEncimaDelStock() {
        Carrito carrito = carritoCon(item(11L, 300L, 1));
        when(carritos.findByUsuarioId(USUARIO)).thenReturn(Optional.of(carrito));
        when(items.findByIdAndCarritoId(11L, 5L)).thenReturn(Optional.of(item(11L, 300L, 1)));
        when(catalogo.precios(any(), eq(List.of(300L)))).thenReturn(
                List.of(new LineaPrecio(300L, "Monitor LG", null, new BigDecimal("50.00"), 2)));

        assertThatThrownBy(() -> servicio.cambiarCantidad(USUARIO, 11L, 5))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("Solo quedan 2");
    }

    /* ══════════════ Ver (read-only) ══════════════ */

    @Test
    @DisplayName("ver() no crea un carrito si no existe: devuelve vacío")
    void verNoCreaCarrito() {
        when(carritos.buscarConItems(USUARIO)).thenReturn(Optional.empty());

        CarritoResponse respuesta = servicio.ver(USUARIO);

        assertThat(respuesta.items()).isEmpty();
        assertThat(respuesta.subtotal()).isEqualByComparingTo("0");
        verify(carritos, never()).save(any());
    }

    @Test
    @DisplayName("ver() devuelve el carrito existente con sus items")
    void verCarritoExistente() {
        Carrito carrito = carritoCon(item(11L, 300L, 2));
        when(carritos.buscarConItems(USUARIO)).thenReturn(Optional.of(carrito));
        when(catalogo.precios(any(), anyList())).thenReturn(
                List.of(new LineaPrecio(300L, "Monitor LG", null, new BigDecimal("100.00"), 10)));

        CarritoResponse respuesta = servicio.ver(USUARIO);

        assertThat(respuesta.items()).hasSize(1);
        assertThat(respuesta.subtotal()).isEqualByComparingTo("200.00");
    }
}
