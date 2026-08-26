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
import com.backend.compras.carrito.dto.CarritoDtos.CarritoResponse;
import com.backend.compras.envio.EnvioService;
import com.backend.compras.metodopago.MetodoPago;
import com.backend.compras.metodopago.MetodoPagoRepository;
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
import com.backend.compras.shared.error.PagoEnCursoException;
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
    private final MetodoPagoRepository metodosPago;
    private final CatalogoClient catalogo;
    private final MercadoPagoClient mercadoPago;
    private final EnvioService envioService;
    private final MetricasSeguridad metricas;

    /* ══════════════ Fase 1: iniciar el checkout ══════════════ */

    @Transactional
    public PreferenciaResponse iniciar(Long usuarioId, PreferenciaRequest peticion, String token) {
        Long metodoPagoId = peticion.metodoPagoId();
        DireccionEntrega entrega = peticion.entrega();

        /*
         * El método de pago, ANTES de tocar nada.
         *
         * Lo comprobaba `crearDesdeCarrito`, que es el paso 2: con un id
         * inexistente ya se había reservado el stock, y el 404 llegaba con media
         * saga montada que había que compensar. Un identificador que no existe
         * es una petición mal formada, no un fallo a mitad de la compra.
         */
        MetodoPago metodoPago = metodosPago.findById(metodoPagoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Método de pago no encontrado"));

        // Una compra a la vez por usuario: dos sagas simultáneas reservarían
        // el stock dos veces para el mismo carrito. Se recorren TODAS las vivas y
        // no solo la primera: la que quedara sin tocar bloquearía el siguiente
        // intento igual que ésta, y nadie volvería a mirarla.
        for (SagaCheckout previa : sagas.buscarActivasDeUsuario(usuarioId)) {

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
             * POR QUÉ VA POR EL PROXY, que es el segundo fallo y el peor:
             * `conciliar` cierra la compra anterior, y aquí se lanza acto seguido
             * una excepción. Llamándolo directo, todo ese cierre ocurría DENTRO de
             * esta transacción y la excepción lo deshacía entero —pedido otra vez
             * PENDIENTE, saga otra vez ESPERANDO_PAGO— mientras la confirmación
             * del stock, que es una llamada HTTP, sí quedaba hecha. El comprador
             * leía «míralo en Mis compras» y allí no había nada, y el siguiente
             * intento repetía lo mismo: encerrado hasta que pasara el barrendero.
             * Con REQUIRES_NEW el cierre se confirma por su cuenta y sobrevive.
             */
            switch (proxia.getObject().conciliar(previa, token)) {
                case COMPLETADA -> throw new ConflictoException(
                        "Tu compra anterior sí se completó: el pago llegó aunque no volvieras "
                                + "a la tienda. Míralo en Mis compras antes de pagar otra vez.");

                case PAGO_EN_CURSO -> throw new PagoEnCursoException(
                        "Tienes un pago anterior todavía en proceso. En cuanto la pasarela lo "
                                + "confirme cerramos esa compra sola; espera antes de pagar otra vez.");

                case SIN_PAGO -> {
                    log.info("El usuario {} ya tenía la saga {} en curso; se compensa antes de reiniciar",
                            usuarioId, previa.getReferencia());
                    proxia.getObject().compensar(previa, token,
                            "reemplazada por un nuevo intento de compra");
                }
            }
        }

        Carrito carrito = carritos.buscarConItems(usuarioId)
                .orElseThrow(() -> new ConflictoException("Tu carrito está vacío"));

        if (carrito.getItems().isEmpty()) {
            throw new ConflictoException("Tu carrito está vacío");
        }

        /*
         * UNA sola lectura del carrito, y de ella salen las tres cosas: las
         * líneas del pedido, el importe que se cobra y el desglose que verá el
         * comprador. Se construía dos veces —aquí para el total y otra vez
         * dentro de `crearDesdeCarrito` para las líneas—, lo que además de una
         * segunda llamada a catálogo eran dos fotos distintas de los precios.
         *
         * El importe es subtotal MÁS ENVÍO. Antes se cobraba el subtotal pelado
         * mientras el carrito enseñaba el total con el envío sumado: el
         * comprador veía un número y se le cobraba otro más bajo.
         */
        CarritoResponse resumen = carritoService.construir(carrito);
        BigDecimal total = resumen.total();
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
                    usuarioId, metodoPagoId, saga.getReferencia(), resumen);
            saga.setPedidoId(pedido.getId());
            saga.avanzarA(Paso.PEDIDO_CREADO);
            sagas.save(saga);

            /*
             * Aquí se bifurca, y hasta ahora no lo hacía.
             *
             * «Contra entrega» estaba en el desplegable desde la primera
             * migración, pero el checkout mandaba a MercadoPago pasara lo que
             * pasara: quien elegía pagar en efectivo acababa en la pasarela
             * igual, y si no estaba configurada se llevaba un 503. Elegía una
             * opción que la tienda no sabía ejecutar.
             */
            if (!metodoPago.esMercadoPago()) {
                return cerrarContraEntrega(saga, usuarioId, token);
            }

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

            return PreferenciaResponse.conPasarela(
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

    /**
     * Cierra una compra que se paga al recibirla.
     *
     * <p>No hay fase 2: sin pasarela no hay nada que verificar después, así que
     * los pasos que en el checkout con tarjeta esperan al pago —confirmar la
     * reserva, dejar listo el pedido, crear el envío y vaciar el carrito— se dan
     * ahora y la saga termina aquí mismo.
     *
     * <p>El pedido queda en CONFIRMADO y no en PAGADO: el dinero llega con el
     * repartidor. Es la diferencia que hace que no se pueda valorar todavía el
     * producto y que el panel de envíos vea de un vistazo qué hay que cobrar.
     *
     * <p>Corre dentro del try de {@code iniciar}, así que si algo falla —catálogo
     * no responde al confirmar la reserva— la compensación de allí lo deshace
     * igual que en cualquier otro paso.
     */
    private PreferenciaResponse cerrarContraEntrega(SagaCheckout saga, Long usuarioId, String token) {

        // La reserva pasa a definitiva: el stock sale del inventario ya, no
        // cuando se cobre. Si se dejara reservado caducaría en catálogo a los
        // veinte minutos y el repartidor saldría con un pedido sin existencias.
        catalogo.confirmarReserva(token, saga.getReferencia());
        saga.avanzarA(Paso.STOCK_CONFIRMADO);
        sagas.save(saga);

        Pedido pedido = pedidos.buscarConDetalles(saga.getPedidoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido de la saga no encontrado"));
        pedido.cambiarEstado(EstadoPedido.CONFIRMADO);
        pedidos.save(pedido);
        // Se reutiliza el paso de la saga con pasarela: el nombre habla del
        // pago, pero lo que marca es «el pedido ya está listo para enviarse», y
        // partir la máquina de estados en dos por un matiz de nombre haría que
        // el barrendero y la compensación tuvieran que conocer las dos.
        saga.avanzarA(Paso.PEDIDO_PAGADO);
        sagas.save(saga);

        envioService.crearParaPedido(pedido, saga);
        saga.avanzarA(Paso.ENVIO_CREADO);

        carritoService.vaciar(usuarioId);

        saga.avanzarA(Paso.FIN);
        saga.setEstado(Estado.COMPLETADA);
        sagas.save(saga);

        metricas.sagaFinalizada("completada");
        log.info("Saga {} completada sin pasarela: pedido {} a cobrar contra entrega",
                saga.getReferencia(), pedido.getId());

        return PreferenciaResponse.sinPasarela(saga.getTotal(), pedido.getId());
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
            //
            // Un pago que sigue en curso NO es un pago fallido, y la diferencia
            // vale la compra entera: un efectivo nace `pending` y una tarjeta en
            // revisión pasa por `in_process`. Los dos acababan aquí, en el mismo
            // saco que un rechazo, y salían por el catch de abajo compensando:
            // pedido cancelado y stock devuelto justo antes de que el cobro
            // entrara. Y con la saga ya COMPENSADA —estado final— el aviso de
            // aprobación no tenía después nada que cerrar.
            if (pago.enCurso()) {
                throw new PagoEnCursoException(
                        "Tu pago sigue en proceso (estado: " + pago.status() + "). En cuanto la "
                                + "pasarela lo confirme cerramos la compra sola; no pagues otra vez.");
            }

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

            // Paso 8 — vaciar el carrito, ya convertido en pedido. Por el
            // servicio y no a mano: el vaciado de aquí era una segunda copia del
            // mismo borrado, y arreglar uno dejaba el otro como estaba.
            carritoService.vaciar(usuarioId);

            saga.avanzarA(Paso.FIN);
            saga.setEstado(Estado.COMPLETADA);
            sagas.save(saga);

            metricas.sagaFinalizada("completada");
            log.info("Saga {} completada: pedido {}", saga.getReferencia(), pedido.getId());
            return PedidoResponse.desde(pedido);

        } catch (PagoEnCursoException ex) {
            // A propósito ANTES del catch de abajo: esta compra no se compensa.
            // Se queda esperando, tal cual, hasta que la pasarela decida. La
            // transacción se deshace, así que la saga sigue en ESPERANDO_PAGO y
            // tanto el webhook como el barrendero podrán cerrarla más tarde.
            log.info("La saga {} sigue esperando a la pasarela: {}",
                    saga.getReferencia(), ex.getMessage());
            throw ex;

        } catch (RuntimeException ex) {
            log.error("Fallo confirmando la saga {}: {}", saga.getReferencia(), ex.getMessage());
            saga.marcarError(ex.getMessage());
            proxia.getObject().compensar(saga, token, ex.getMessage());
            throw ex;
        }
    }

    /* ══════════════ Conciliación ══════════════ */

    /** Qué dice la pasarela de una compra que estaba a punto de tirarse. */
    public enum Conciliacion {
        /** Se pagó y la compra ha quedado cerrada. No hay nada que compensar. */
        COMPLETADA,
        /** Hay un pago que aún puede aprobarse: esperar, NUNCA compensar. */
        PAGO_EN_CURSO,
        /** No hay ningún pago vivo: se puede tirar sin perder dinero. */
        SIN_PAGO
    }

    /**
     * Pregunta a la pasarela antes de tirar una compra, y la cierra si se pagó.
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
     * <p><b>REQUIRES_NEW no es decorativo.</b> Quien llama desde el checkout
     * lanza una excepción justo después de esto, y sin transacción propia el
     * cierre de la compra se deshacía con ella: quedaba el stock confirmado en
     * catálogo —eso es HTTP y no se deshace— pero el pedido volvía a PENDIENTE y
     * la saga a ESPERANDO_PAGO, así que el comprador no veía su compra por
     * ninguna parte y el siguiente intento tropezaba con lo mismo. Es la misma
     * razón por la que {@link #compensar} lleva la anotación, y por la que las
     * dos se invocan a través de {@code proxia}: una llamada directa se salta el
     * proxy y con él la anotación.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Conciliacion conciliar(SagaCheckout saga, String token) {
        var pago = mercadoPago.buscarPagoDeLaCompra(saga.getReferencia());
        if (pago.isEmpty()) {
            return Conciliacion.SIN_PAGO;
        }

        // Ni aprobado ni rechazado: la compra se queda como está. Se decide aquí
        // y no dentro de `confirmar` para no dejar la transacción marcada para
        // deshacerse por una excepción que sí sabemos manejar.
        if (pago.get().enCurso()) {
            log.info("La compra {} no se descarta: tiene un pago en estado {}",
                    saga.getReferencia(), pago.get().status());
            return Conciliacion.PAGO_EN_CURSO;
        }

        log.warn("La compra {} estaba por descartarse pero SÍ se pagó (pago={}). Se completa.",
                saga.getReferencia(), pago.get().id());

        // Por el mismo camino que el retorno del navegador y con el dueño de la
        // saga: así el pedido, el stock y el envío quedan igual que si hubiera
        // vuelto, y las comprobaciones de importe y de propiedad se aplican una
        // sola vez y en un único sitio.
        confirmar(saga.getUsuarioId(), pago.get().id(), token);
        return Conciliacion.COMPLETADA;
    }

    /* ══════════════ Compensación ══════════════ */

    /**
     * Deshace los pasos dados, en orden inverso.
     *
     * <p><b>La saga se vuelve a leer aquí dentro, y no se toca la instancia que
     * llega.</b> Es lo que arregla un 409 que salía en producción:
     *
     * <ol>
     *   <li>{@code iniciar} carga la saga anterior en SU transacción y nos la
     *       pasa. Esa instancia sigue siendo suya, y la sigue vigilando.
     *   <li>Esto corre en REQUIRES_NEW: si mutáramos ese mismo objeto y lo
     *       guardáramos, la fila subiría de {@code version} en la base…
     *   <li>…pero el contexto de {@code iniciar} conservaría la versión con la
     *       que la leyó, y además vería el objeto sucio. Al confirmar su
     *       transacción lanzaría un UPDATE con la versión vieja, que no encaja
     *       con ninguna fila.
     * </ol>
     *
     * <p>El resultado era «Otra operación modificó estos datos al mismo tiempo»
     * en el PRIMER intento de pagar después de un checkout abandonado, sin haber
     * ninguna otra operación: el conflicto era consigo mismo. Al segundo intento
     * funcionaba, porque la saga anterior ya estaba compensada y este camino no
     * se recorría.
     *
     * <p>Si la saga ya no estuviera en la base se usa la que llegó: es un caso
     * que no debería darse, y perder la compensación por ello sería peor.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensar(SagaCheckout deLlamante, String token, String motivo) {
        SagaCheckout saga = sagas.findByReferencia(deLlamante.getReferencia())
                .orElse(deLlamante);

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
