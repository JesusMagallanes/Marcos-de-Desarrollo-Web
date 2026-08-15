package com.backend.compras.saga;

import java.math.BigDecimal;
import java.util.List;

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

        // Una compra a la vez por usuario: dos sagas simultáneas reservarían
        // el stock dos veces para el mismo carrito.
        List<SagaCheckout> activas = sagas.buscarActivasDeUsuario(usuarioId);
        if (!activas.isEmpty()) {
            SagaCheckout previa = activas.get(0);
            log.info("El usuario {} ya tenía la saga {} en curso; se compensa antes de reiniciar",
                    usuarioId, previa.getReferencia());
            compensar(previa, token, "reemplazada por un nuevo intento de compra");
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
                .direccionEnvio(peticion.direccionEnvio())
                .referenciaEnvio(peticion.referenciaEnvio())
                .telefonoContacto(peticion.telefonoContacto())
                .latitud(peticion.latitud())
                .longitud(peticion.longitud())
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
            Preferencia preferencia = mercadoPago.crearPreferencia(
                    "Compra SmartZone", total, saga.getReferencia());

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
            compensar(saga, token, ex.getMessage());
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
            compensar(saga, token, ex.getMessage());
            throw ex;
        }
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
