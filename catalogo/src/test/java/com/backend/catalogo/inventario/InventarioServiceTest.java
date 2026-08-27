package com.backend.catalogo.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.backend.catalogo.producto.Producto;
import com.backend.catalogo.producto.ProductoRepository;
import com.backend.catalogo.producto.dto.ProductoDtos.AjusteStock;
import com.backend.catalogo.shared.error.ConflictoException;
import com.backend.catalogo.shared.error.RecursoNoEncontradoException;

/**
 * El participante de la saga de compra, del lado del inventario.
 *
 * <p>Aquí se decide cuánto stock hay de verdad, y los tres movimientos —apartar,
 * confirmar y devolver— tienen que cuadrar exactamente o la tienda vende lo que
 * no tiene o se queda con existencias bloqueadas que no puede vender.
 *
 * <p>Las reglas que se prueban, en orden de lo que costaría equivocarse:
 *
 * <ol>
 *   <li>Confirmar una reserva caducada NO puede pasar: el stock ya volvió al
 *       almacén y confirmarla lo descontaría dos veces.
 *   <li>Reservar dos veces con la misma referencia descuenta una sola vez: el
 *       reintento tras un timeout es normal y no puede vaciar el almacén.
 *   <li>Liberar o expirar devuelve exactamente lo apartado, y solo una vez.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Reservas de stock")
class InventarioServiceTest {

    private static final String REFERENCIA = "sz-42-1-1723456789000";

    @Mock
    private ReservaStockRepository reservas;
    @Mock
    private ProductoRepository productos;

    private InventarioService servicio;

    @BeforeEach
    void preparar() {
        servicio = new InventarioService(reservas, productos);
        ReflectionTestUtils.setField(servicio, "minutosReserva", 20);
    }

    private Producto producto(long id, int stock) {
        return Producto.builder().id(id).name("Monitor LG").stock(stock).build();
    }

    private ReservaStock reserva(ReservaStock.Estado estado, Instant expira) {
        return ReservaStock.builder()
                .id(1L).referencia(REFERENCIA).productoId(7L).cantidad(3)
                .estado(estado).expiraEn(expira)
                .build();
    }

    /* ══════════════ Apartar ══════════════ */

    @Test
    @DisplayName("reservar descuenta del stock disponible en el acto")
    void reservarDescuenta() {
        Producto monitor = producto(7L, 10);
        when(reservas.existsByReferencia(REFERENCIA)).thenReturn(false);
        when(productos.buscarParaActualizarStock(7L)).thenReturn(Optional.of(monitor));

        servicio.reservar(REFERENCIA, List.of(new AjusteStock(7L, 3)));

        /*
         * El descuento es inmediato y no cuando se cobra: si se esperara al
         * pago, la ficha seguiría anunciando diez unidades mientras tres ya
         * están comprometidas, y se vendería lo mismo dos veces.
         */
        assertThat(monitor.getStock()).isEqualTo(7);

        ArgumentCaptor<ReservaStock> guardada = ArgumentCaptor.forClass(ReservaStock.class);
        verify(reservas).save(guardada.capture());
        assertThat(guardada.getValue().getEstado()).isEqualTo(ReservaStock.Estado.ACTIVA);
        assertThat(guardada.getValue().getExpiraEn()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("sin stock suficiente no se aparta nada")
    void sinStock() {
        Producto casiAgotado = producto(7L, 2);
        when(reservas.existsByReferencia(REFERENCIA)).thenReturn(false);
        when(productos.buscarParaActualizarStock(7L)).thenReturn(Optional.of(casiAgotado));

        assertThatThrownBy(() -> servicio.reservar(REFERENCIA, List.of(new AjusteStock(7L, 3))))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("Stock insuficiente")
                .hasMessageContaining("quedan 2");

        assertThat(casiAgotado.getStock()).isEqualTo(2);
        verify(reservas, never()).save(any());
    }

    @Test
    @DisplayName("la misma referencia dos veces descuenta UNA vez")
    void reservaIdempotente() {
        /*
         * `compras` reintenta con espera exponencial cuando una llamada da
         * timeout, y el timeout puede ocurrir con el descuento ya hecho. Sin
         * esta comprobación, un reintento se llevaría el stock por segunda vez.
         */
        when(reservas.existsByReferencia(REFERENCIA)).thenReturn(true);

        servicio.reservar(REFERENCIA, List.of(new AjusteStock(7L, 3)));

        verify(productos, never()).buscarParaActualizarStock(any());
        verify(reservas, never()).save(any());
    }

    @Test
    @DisplayName("un producto que ya no existe corta la reserva")
    void productoInexistente() {
        when(reservas.existsByReferencia(REFERENCIA)).thenReturn(false);
        when(productos.buscarParaActualizarStock(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.reservar(REFERENCIA, List.of(new AjusteStock(7L, 3))))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    /* ══════════════ Confirmar ══════════════ */

    @Test
    @DisplayName("confirmar deja la reserva definitiva y NO vuelve a tocar el stock")
    void confirmar() {
        ReservaStock activa = reserva(ReservaStock.Estado.ACTIVA, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(reservas.findByReferencia(REFERENCIA)).thenReturn(List.of(activa));

        servicio.confirmar(REFERENCIA);

        assertThat(activa.getEstado()).isEqualTo(ReservaStock.Estado.CONFIRMADA);
        // El stock se descontó al reservar. Volver a tocarlo aquí lo restaría dos veces.
        verify(productos, never()).save(any());
    }

    @Test
    @DisplayName("confirmar una reserva CADUCADA se rechaza")
    void confirmarCaducada() {
        /*
         * Es la prueba más importante de esta clase. Al caducar, el barrido ya
         * devolvió el stock al almacén; darla por buena ahora significaría
         * entregar unas unidades que ya se pusieron otra vez a la venta.
         *
         * Rechazarlo es correcto aunque el cobro haya entrado: `compras`
         * compensa el pedido y queda un caso para mirar a mano, que es mucho
         * mejor que un descuadre silencioso de inventario.
         */
        ReservaStock expirada = reserva(ReservaStock.Estado.EXPIRADA, Instant.now().minus(1, ChronoUnit.MINUTES));
        when(reservas.findByReferencia(REFERENCIA)).thenReturn(List.of(expirada));

        assertThatThrownBy(() -> servicio.confirmar(REFERENCIA))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("expiró");
    }

    @Test
    @DisplayName("confirmar dos veces no rompe: la segunda no hace nada")
    void confirmarDosVeces() {
        // El webhook de la pasarela reintenta, y el comprador puede volver a la
        // tienda a la vez que llega ese aviso.
        ReservaStock yaConfirmada = reserva(ReservaStock.Estado.CONFIRMADA, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(reservas.findByReferencia(REFERENCIA)).thenReturn(List.of(yaConfirmada));

        servicio.confirmar(REFERENCIA);

        assertThat(yaConfirmada.getEstado()).isEqualTo(ReservaStock.Estado.CONFIRMADA);
        verify(productos, never()).save(any());
    }

    @Test
    @DisplayName("confirmar una referencia que no existe da 404, no un silencio")
    void confirmarSinReserva() {
        when(reservas.findByReferencia(anyString())).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.confirmar(REFERENCIA))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    /* ══════════════ Devolver ══════════════ */

    @Test
    @DisplayName("liberar devuelve al almacén exactamente lo apartado")
    void liberar() {
        ReservaStock activa = reserva(ReservaStock.Estado.ACTIVA, Instant.now().plus(10, ChronoUnit.MINUTES));
        Producto monitor = producto(7L, 7);
        when(reservas.findByReferencia(REFERENCIA)).thenReturn(List.of(activa));
        when(productos.buscarParaActualizarStock(7L)).thenReturn(Optional.of(monitor));

        servicio.liberar(REFERENCIA);

        assertThat(monitor.getStock()).isEqualTo(10);
        assertThat(activa.getEstado()).isEqualTo(ReservaStock.Estado.LIBERADA);
    }

    @Test
    @DisplayName("liberar una reserva ya CONFIRMADA no devuelve nada")
    void liberarConfirmadaNoDevuelve() {
        /*
         * La compensación puede llegar tarde, con la venta ya cerrada. Devolver
         * el stock entonces regalaría unidades que salieron por la puerta.
         */
        ReservaStock confirmada = reserva(ReservaStock.Estado.CONFIRMADA, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(reservas.findByReferencia(REFERENCIA)).thenReturn(List.of(confirmada));

        servicio.liberar(REFERENCIA);

        verify(productos, never()).buscarParaActualizarStock(any());
        assertThat(confirmada.getEstado()).isEqualTo(ReservaStock.Estado.CONFIRMADA);
    }

    @Test
    @DisplayName("liberar dos veces devuelve el stock una sola vez")
    void liberarEsIdempotente() {
        ReservaStock yaLiberada = reserva(ReservaStock.Estado.LIBERADA, Instant.now().plus(10, ChronoUnit.MINUTES));
        when(reservas.findByReferencia(REFERENCIA)).thenReturn(List.of(yaLiberada));

        servicio.liberar(REFERENCIA);

        verify(productos, never()).buscarParaActualizarStock(any());
    }

    @Test
    @DisplayName("liberar algo que no existe no es un error: no hay nada que deshacer")
    void liberarSinReserva() {
        when(reservas.findByReferencia(anyString())).thenReturn(List.of());

        servicio.liberar(REFERENCIA);

        verify(productos, never()).save(any());
    }

    /* ══════════════ Caducidad ══════════════ */

    @Test
    @DisplayName("el barrido devuelve el stock de lo que caducó y lo marca EXPIRADA")
    void expirarReservas() {
        ReservaStock vencida = reserva(ReservaStock.Estado.ACTIVA, Instant.now().minus(5, ChronoUnit.MINUTES));
        Producto monitor = producto(7L, 7);
        when(reservas.buscarCaducadas(any())).thenReturn(List.of(vencida));
        when(productos.buscarParaActualizarStock(7L)).thenReturn(Optional.of(monitor));

        servicio.expirarReservas();

        assertThat(monitor.getStock()).isEqualTo(10);
        assertThat(vencida.getEstado()).isEqualTo(ReservaStock.Estado.EXPIRADA);
    }

    @Test
    @DisplayName("si el producto de una reserva caducada ya no existe, la reserva se cierra igual")
    void expirarConProductoBorrado() {
        /*
         * Sin esto la reserva se quedaría ACTIVA para siempre y el barrido la
         * recogería en cada pasada, cada minuto, hasta el fin de los tiempos.
         */
        ReservaStock vencida = reserva(ReservaStock.Estado.ACTIVA, Instant.now().minus(5, ChronoUnit.MINUTES));
        when(reservas.buscarCaducadas(any())).thenReturn(List.of(vencida));
        when(productos.buscarParaActualizarStock(7L)).thenReturn(Optional.empty());

        servicio.expirarReservas();

        assertThat(vencida.getEstado()).isEqualTo(ReservaStock.Estado.EXPIRADA);
    }

    @Test
    @DisplayName("sin reservas caducadas el barrido no toca nada")
    void nadaQueExpirar() {
        when(reservas.buscarCaducadas(any())).thenReturn(List.of());

        servicio.expirarReservas();

        verify(reservas, never()).save(any());
    }
}
