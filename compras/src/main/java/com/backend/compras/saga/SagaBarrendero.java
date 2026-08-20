package com.backend.compras.saga;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.backend.compras.shared.seguridad.TokenServicio;
import com.backend.compras.shared.seguridad.ContextoRls;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Cierra las sagas que quedaron a medias. */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaBarrendero {

    private final SagaCheckoutRepository sagas;
    private final CheckoutOrquestador orquestador;
    private final TokenServicio tokenServicio;

    /** Tras este tiempo sin confirmarse, se da el pago por abandonado. */
    @Value("${compras.saga.minutos-abandono:25}")
    private int minutosAbandono;

    @Value("${compras.saga.max-intentos-compensacion:5}")
    private int maxIntentos;

    /**
     * El usuario empezó a pagar y no volvió. Se compensa para devolver el stock; el margen
     * es mayor que la caducidad de la reserva en catálogo, que actúa como segunda red de
     * seguridad.
     */
    @Scheduled(fixedDelay = 120_000)
    public void compensarAbandonadas() {
        // Sin este contexto RLS no devolvería ninguna saga: el barrido corre en
        // un hilo del planificador, sin usuario autenticado, y las políticas de
        // V4__row_level_security.sql filtran por `app.usuario_id`. El stock
        // reservado se quedaría bloqueado para siempre y en silencio.
        ContextoRls.comoSistema(this::barrerAbandonadas);
    }

    private void barrerAbandonadas() {
        LocalDateTime limite = LocalDateTime.now().minusMinutes(minutosAbandono);
        List<SagaCheckout> abandonadas = sagas.buscarAbandonadas(limite);

        if (abandonadas.isEmpty()) {
            return;
        }

        log.info("Revisando {} compras sin confirmar", abandonadas.size());

        // El servicio se identifica con su propio token. Antes se pasaba `null`
        // y la cabecera de autorización no viajaba: catálogo respondía 401 y la
        // compensación fallaba en silencio, apoyándose en que la reserva
        // caducase sola.
        String token = tokenServicio.emitir();

        for (SagaCheckout saga : abandonadas) {
            try {
                // ANTES DE CANCELAR, PREGUNTAR SI SE PAGÓ.
                //
                // Sin esto, quien pagaba y cerraba la pestaña se quedaba sin
                // pedido, con el stock devuelto a la tienda y con el cobro hecho.
                // El `paymentId` solo llega por la URL de retorno, así que para
                // los que no volvieron hay que buscar por la referencia.
                switch (orquestador.conciliar(saga, token)) {
                    case COMPLETADA -> log.info("La compra {} se cerró al conciliar, no se compensa",
                            saga.getReferencia());

                    // El plazo se agotó pero el pago sigue vivo. Cancelar ahora
                    // es exactamente el error que esta comprobación evita, solo
                    // que unos minutos antes: el cobro entra después y ya no hay
                    // pedido al que asociarlo. Se deja para la siguiente pasada;
                    // si nunca se aprueba, la reserva de stock caduca sola en
                    // catálogo, que es la segunda red de seguridad.
                    case PAGO_EN_CURSO -> log.warn("La compra {} pasó del plazo pero su pago sigue"
                            + " en curso: se deja viva y se revisa en la siguiente pasada",
                            saga.getReferencia());

                    case SIN_PAGO -> orquestador.compensar(saga, token, "pago no completado a tiempo");
                }
            } catch (RuntimeException ex) {
                log.error("Error compensando la saga {}: {}", saga.getReferencia(), ex.getMessage());
            }
        }
    }

    /**
     * Reintenta compensaciones que fallaron (por ejemplo, catálogo caído en ese momento).
     * Tras varios intentos la saga queda FALLIDA para revisión.
     */
    @Scheduled(fixedDelay = 300_000)
    public void reintentarCompensaciones() {
        ContextoRls.comoSistema(this::barrerCompensacionesPendientes);
    }

    private void barrerCompensacionesPendientes() {
        List<SagaCheckout> pendientes = sagas.buscarCompensacionesPendientes(maxIntentos);

        if (pendientes.isEmpty()) {
            return;
        }

        log.info("Reintentando {} compensaciones pendientes", pendientes.size());
        for (SagaCheckout saga : pendientes) {
            try {
                orquestador.compensar(saga, tokenServicio.emitir(), "reintento de compensación");
            } catch (RuntimeException ex) {
                log.error("Reintento fallido de la saga {}: {}", saga.getReferencia(), ex.getMessage());
            }
        }
    }
}
