package com.backend.compras.saga;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.backend.compras.carrito.Carrito;
import com.backend.compras.carrito.CarritoItem;
import com.backend.compras.carrito.CarritoRepository;
import com.backend.compras.carrito.CarritoService;
import com.backend.compras.envio.EnvioService;
import com.backend.compras.pago.MercadoPagoClient;
import com.backend.compras.pago.MercadoPagoClient.Pago;
import com.backend.compras.pago.MercadoPagoClient.Preferencia;
import com.backend.compras.pago.dto.DireccionEntrega;
import com.backend.compras.pago.dto.PagoDtos.PreferenciaRequest;
import com.backend.compras.pago.dto.PagoDtos.PreferenciaResponse;
import com.backend.compras.pedido.EstadoPedido;
import com.backend.compras.pedido.Pedido;
import com.backend.compras.pedido.PedidoRepository;
import com.backend.compras.pedido.PedidoService;
import com.backend.compras.pedido.dto.PedidoDtos.PedidoResponse;
import com.backend.compras.saga.SagaCheckout.Estado;
import com.backend.compras.saga.SagaCheckout.Paso;
import com.backend.compras.shared.catalogo.CatalogoClient;
import com.backend.compras.shared.catalogo.CatalogoClient.AjusteStock;
import com.backend.compras.shared.error.ConflictoException;
import com.backend.compras.shared.error.RecursoNoEncontradoException;
import com.backend.compras.shared.metricas.MetricasSeguridad;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Orquestador de la saga de compra. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutOrquestador {

    /*
     * El propio orquestador, pero visto a traves del proxy de Spring.
     *
     * Llamar a `compensar(...)` directamente NO pasa por el proxy, y entonces su
     * @Transactional(REQUIRES_NEW) no se aplica: la compensacion se ejecutaba
     * dentro de la transaccion que estaba fallando, asi que al relanzar la
     * excepcion se deshacia junto con todo lo demas. Quedaba el stock liberado
     * en catalogo --eso es una llamada HTTP y no se deshace-- pero la saga
     * seguia diciendo ESPERANDO_PAGO y el pedido PENDIENTE.
     *
     * Es un ObjectProvider y no una inyeccion normal porque una dependencia a si
     * mismo por constructor seria un ciclo; asi se resuelve cuando se usa.
     */
    private final ObjectProvider<CheckoutOrquestador> proxia;

    private final SagaCheckoutRepository sagas;
    private final CarritoRepository carritos;
    private final CarritoService carritoService;
    private final PedidoRepository pedidos;
    private final PedidoService pedidoService;
    private final CatalogoClient catalogo;
    private final MercadoPagoClient mercadoPago;
    private final EnvioService envioService;
    private final MetricasSeguridad metricas;

    /* ══════════════ Fase 1: iniciar el checkout ══════════════ */

    @Transactional
    public PreferenciaResponse iniciar(Long usuarioId, PreferenciaRequest peticion, String token) {
        Long metodoPagoId = peticion.metodoPagoId();
        DireccionEntrega entrega = peticion.entrega();

        // Una compra a la vez por usuario: dos sagas simultáneas reservarían
        // el stock dos veces para el mismo carrito.
        List<SagaCheckout> activas = sagas.buscarActivasDeUsuario(usuarioId);
        if (!activas.isEmpty()) {
            SagaCheckout previa = activas.get(0);

            /*
             * ANTES DE TIRARLA, PREGUNTAR SI SE PAGÓ.
             *
             * Quien paga y no vuelve a la tienda —porque cierra la pestaña, o
             * porque MercadoPago no le da botón de volver— tiene una saga viva
             * con el cobro hecho. Si entonces reintenta la compra desde el
             * carrito, esto la compensaba: pedido cancelado, stock devuelto y el
             * dinero cobrado. Y como COMPENSADA es un estado final, el barrendero
             * ya no la miraba nunca más: el pago se perdía para siempre.
             *
             * Es la misma comprobación que hace el barrendero antes de compensar;
             * faltaba en este camino, que es justo el que recorre alguien
             * impaciente.
             */
            if (conciliarSiSePago(previa, token)) {
                throw new ConflictoException(
                        "Tu compra anterior sí se completó: el pago llegó aunque no volvieras "
                                + "a la tienda. Míralo en Mis compras antes de pagar otra vez.");
            }

            log.info("El usuario {} ya tenía la saga {} en curso; se compensa antes de reiniciar",
                    usuarioId, previa.getReferencia());
            proxia.getObject().compensar(previa, token, "reemplazada por un nuevo intento de compra");
        }

        Carrito carrito = carritos.buscarConItems(usuarioId)
                .orElseThrow(() -> new ConflictoException("Tu carrito está vacío"));

        if (carrito.getItems().isEmpty()) {
            throw new ConflictoException("Tu carrito está vacío");
        }

        BigDecimal total = carritoService.construir(carrito).subtotal();
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConflictoException("El importe de tu carrito no es válido");
        }

        SagaCheckout saga = sagas.save(SagaCheckout.builder()
                .referencia(Referencia.crear(usuarioId, metodoPagoId).formatear())
                .usuarioId(usuarioId)
                .metodoPagoId(metodoPagoId)
                // El destino se guarda ya: cuando vuelva el comprador (o llegue
                // el webhook) no habrá formulario del que leerlo.
                //
                // La línea de `direccionEnvio` se compone a partir de las partes
                // en vez de pedirla escrita: así lo que se imprime en la etiqueta
                // y lo que se le manda a la pasarela no pueden contradecirse.
                .direccionEnvio(entrega.enUnaLinea())
                .referenciaEnvio(entrega.referencia())
                .telefonoContacto(entrega.telefonoContacto())
                .calle(entrega.calle())
                .numero(entrega.numero())
                .codigoPostal(entrega.codigoPostal())
                .distrito(entrega.distrito())
                .provincia(entrega.provincia())
                .departamento(entrega.departamento())
                .pais(entrega.pais())
                .receptorNombre(entrega.receptorNombre())
                .latitud(entrega.latitud())
                .longitud(entrega.longitud())
                .total(total)
                .estado(Estado.INICIADA)
                .paso(Paso.INICIO)
                .build());

        try {
            // Paso 1 — reservar stock.
            List<AjusteStock> lineas = carrito.getItems().stream()
                    .map(i -> new AjusteStock(i.getProductoId(), i.getCantidad()))
                    .toList();

            catalogo.reservarStock(token, saga.getReferencia(), lineas);
            saga.avanzarA(Paso.STOCK_RESERVADO);
            sagas.save(saga);

            // Paso 2 — crear el pedido en estado PENDIENTE.
            Pedido pedido = pedidoService.crearDesdeCarrito(
                    usuarioId, metodoPagoId, saga.getReferencia(), total);
            saga.setPedidoId(pedido.getId());
            saga.avanzarA(Paso.PEDIDO_CREADO);
            sagas.save(saga);

            // Paso 3 — preferencia de pago con el importe calculado aquí.
            // El destino viaja con la preferencia: MercadoPago lo enseña en su
            // pantalla y con el código postal puede calcular el envío. Sin él, el
            // comprador solo ve un importe y tiene que fiarse.
            Preferencia preferencia = mercadoPago.crearPreferencia(
                    "Compra SmartZone", total, saga.getReferencia(), entrega);

            saga.avanzarA(Paso.PREFERENCIA_CREADA);
            saga.setEstado(Estado.ESPERANDO_PAGO);
            sagas.save(saga);

            log.info("Saga {} esperando pago por {}", saga.getReferencia(), total);

            return new PreferenciaResponse(
                    preferencia.id(),
                    preferencia.init_point(),
                    preferencia.sandbox_init_point(),
                    total);

        } catch (RuntimeException ex) {
            log.error("Fallo iniciando la saga {}: {}", saga.getReferencia(), ex.getMessage());
            saga.marcarError(ex.getMessage());
            proxia.getObject().compensar(saga, token, ex.getMessage());
            throw ex;
        }
    }

    /* ══════════════ Fase 2: confirmar el pago ══════════════ */

    @Transactional
    public PedidoResponse confirmar(Long usuarioId, String paymentId, String token) {

        // Idempotencia: recargar la página de retorno no debe crear un segundo
        // pedido ni volver a descontar stock.
        SagaCheckout yaHecha = sagas.findByPaymentId(paymentId).orElse(null);
        if (yaHecha != null && yaHecha.getEstado() == Estado.COMPLETADA) {
            return pedidoRespuesta(yaHecha);
        }

        Pago pago = mercadoPago.consultarPago(paymentId);
        Referencia referencia = Referencia.parsear(pago.external_reference());

        SagaCheckout saga = sagas.findByReferencia(pago.external_reference())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No hay una compra en curso para ese pago"));

        // A01: el pago tiene que ser de quien lo está confirmando.
        if (!saga.getUsuarioId().equals(usuarioId) || !referencia.usuarioId().equals(usuarioId)) {
            log.error("El pago {} es de la saga del usuario {} pero lo confirma {}",
                    paymentId, saga.getUsuarioId(), usuarioId);
            throw new ConflictoException("Este pago no corresponde a tu cuenta");
        }

        if (saga.getEstado() == Estado.COMPLETADA) {
            return pedidoRespuesta(saga);
        }
        if (saga.getEstado() != Estado.ESPERANDO_PAGO) {
            throw new ConflictoException(
                    "Esta compra ya no está activa. Vuelve a intentarlo desde el carrito.");
        }

        saga.setPaymentId(paymentId);

        try {
            // Paso 4 — verificar el pago contra la pasarela.
            if (!pago.aprobado()) {
                throw new ConflictoException(
                        "El pago no está aprobado (estado: " + pago.status() + ")");
            }

            // El importe cobrado debe coincidir con el que fijó el servidor.
            if (pago.transaction_amount() != null
                    && pago.transaction_amount().compareTo(saga.getTotal()) != 0) {
                log.error("Importe inconsistente en {}: cobrado {} vs esperado {}",
                        paymentId, pago.transaction_amount(), saga.getTotal());
                metricas.importeInconsistente();
                throw new ConflictoException(
                        "El importe pagado no coincide con tu compra. Contacta con soporte.");
            }

            saga.avanzarA(Paso.PAGO_VERIFICADO);
            sagas.save(saga);

            // Paso 5 — la reserva de stock pasa a definitiva.
            catalogo.confirmarReserva(token, saga.getReferencia());
            saga.avanzarA(Paso.STOCK_CONFIRMADO);
            sagas.save(saga);

            // Paso 6 — el pedido pasa a PAGADO.
            Pedido pedido = pedidos.buscarConDetalles(saga.getPedidoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Pedido de la saga no encontrado"));
            pedido.setPaymentId(paymentId);
            pedido.cambiarEstado(EstadoPedido.PAGADO);
            pedidos.save(pedido);
            saga.avanzarA(Paso.PEDIDO_PAGADO);
            sagas.save(saga);

            // Paso 7 — envío.
            envioService.crearParaPedido(pedido, saga);
            saga.avanzarA(Paso.ENVIO_CREADO);

            // Paso 8 — vaciar el carrito, ya convertido en pedido.
            carritos.buscarConItems(usuarioId).ifPresent(carrito -> {
                carrito.vaciar();
                carritos.save(carrito);
            });

            saga.avanzarA(Paso.FIN);
            saga.setEstado(Estado.COMPLETADA);
            sagas.save(saga);

            metricas.sagaFinalizada("completada");
            log.info("Saga {} completada: pedido {}", saga.getReferencia(), pedido.getId());
            return PedidoResponse.desde(pedido);

        } catch (RuntimeException ex) {
            log.error("Fallo confirmando la saga {}: {}", saga.getReferencia(), ex.getMessage());
            saga.marcarError(ex.getMessage());
            proxia.getObject().compensar(saga, token, ex.getMessage());
            throw ex;
        }
    }

    /* ══════════════ Conciliación ══════════════ */

    /**
     * Completa la compra si la pasarela dice que sí se pagó.
     *
     * <p>Hace falta siempre que se vaya a tirar una compra a la basura, y hay dos
     * sitios donde eso ocurre: el barrendero, cuando pasa el plazo, y el propio
     * checkout, cuando el comprador reintenta. Vive aquí y no en el barrendero
     * porque los dos la necesitan y porque tirar un pago cobrado es el peor error
     * que puede cometer esta clase.
     *
     * <p>El {@code paymentId} no se conoce: solo llega por la URL de retorno, y
     * precisamente estos son los casos en los que el comprador no volvió. Por eso
     * se busca por la referencia que se puso en la preferencia.
     *
     * @return true si se completó, y entonces NO hay que compensar
     */
    public boolean conciliarSiSePago(SagaCheckout saga, String token) {
        var pago = mercadoPago.buscarPagoAprobado(saga.getReferencia());
        if (pago.isEmpty()) {
            return false;
        }

        log.warn("La compra {} estaba por descartarse pero SÍ se pagó (pago={}). Se completa.",
                saga.getReferencia(), pago.get().id());

        // Por el mismo camino que el retorno del navegador y con el dueño de la
        // saga: así el pedido, el stock y el envío quedan igual que si hubiera
        // vuelto, y las comprobaciones de importe y de propiedad se aplican una
        // sola vez y en un único sitio.
        confirmar(saga.getUsuarioId(), pago.get().id(), token);
        return true;
    }

    /* ══════════════ Compensación ══════════════ */

    /** Deshace los pasos dados, en orden inverso. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensar(SagaCheckout saga, String token, String motivo) {
        if (saga.estaTerminada()) {
            return;
        }

        saga.setEstado(Estado.COMPENSANDO);
        saga.marcarError(motivo);
        sagas.save(saga);

        boolean todoOk = true;

        // C2 — cancelar el pedido si llegó a crearse.
        if (saga.tienePedido()) {
            try {
                pedidos.findById(saga.getPedidoId()).ifPresent(pedido -> {
                    if (pedido.getEstado() != EstadoPedido.CANCELADO
                            && pedido.getEstado() != EstadoPedido.ENTREGADO) {
                        pedido.setEstado(EstadoPedido.CANCELADO);
                        pedidos.save(pedido);
                    }
                });
            } catch (RuntimeException ex) {
                log.error("No se pudo cancelar el pedido {} de la saga {}: {}",
                        saga.getPedidoId(), saga.getReferencia(), ex.getMessage());
                todoOk = false;
            }
        }

        // C1 — devolver el stock reservado.
        if (saga.tieneStockReservado()) {
            try {
                catalogo.liberarReserva(token, saga.getReferencia());
            } catch (RuntimeException ex) {
                log.error("No se pudo liberar la reserva {}: {}", saga.getReferencia(), ex.getMessage());
                todoOk = false;
            }
        }

        saga.setEstado(todoOk ? Estado.COMPENSADA : Estado.FALLIDA);
        sagas.save(saga);

        metricas.sagaFinalizada(todoOk ? "compensada" : "fallida");

        if (todoOk) {
            log.info("Saga {} compensada ({})", saga.getReferencia(), motivo);
        } else {
            // FALLIDA es la señal de que hace falta mirar a mano: el dinero
            // pudo cobrarse y algo no se pudo deshacer.
            log.error("Saga {} en estado FALLIDA: requiere revisión manual", saga.getReferencia());
        }
    }

    private PedidoResponse pedidoRespuesta(SagaCheckout saga) {
        return pedidos.buscarConDetalles(saga.getPedidoId())
                .map(PedidoResponse::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido no encontrado"));
    }
}
