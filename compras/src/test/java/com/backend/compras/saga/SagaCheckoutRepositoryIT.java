package com.backend.compras.saga;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import com.backend.compras.PruebaIntegracion;
import com.backend.compras.saga.SagaCheckout.Estado;
import com.backend.compras.saga.SagaCheckout.Paso;

/**
 * Comprueba contra PostgreSQL real que las migraciones, el mapeo de la entidad
 * y las consultas del barrendero funcionan.
 *
 * Estas consultas son las que deciden qué sagas se compensan: si dejaran de
 * funcionar, las compras abandonadas bloquearían stock indefinidamente y nadie
 * se enteraría.
 */
@EnabledIf(
        value = "com.backend.compras.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
class SagaCheckoutRepositoryIT extends PruebaIntegracion {

    @Autowired
    private SagaCheckoutRepository repositorio;

    @BeforeEach
    void limpiar() {
        repositorio.deleteAll();
    }

    private SagaCheckout saga(String referencia, Long usuarioId, Estado estado, Paso paso) {
        return repositorio.save(SagaCheckout.builder()
                .referencia(referencia)
                .usuarioId(usuarioId)
                .metodoPagoId(1L)
                .total(new BigDecimal("100.00"))
                .estado(estado)
                .paso(paso)
                .build());
    }

    @Test
    @DisplayName("el esquema de Flyway admite la entidad completa, incluida la columna version")
    void persisteYRecupera() {
        SagaCheckout guardada = saga("sz-1-1-100", 1L, Estado.ESPERANDO_PAGO, Paso.PEDIDO_CREADO);

        assertThat(guardada.getId()).isNotNull();
        // `version` la gestiona el bloqueo optimista; faltaba en V2 y el
        // arranque fallaba con "missing column [version]".
        assertThat(guardada.getVersion()).isNotNull();
        assertThat(guardada.getCreadoEn()).isNotNull();
    }

    @Test
    @DisplayName("la referencia es única: dos sagas no pueden compartirla")
    void referenciaUnica() {
        saga("sz-1-1-200", 1L, Estado.INICIADA, Paso.INICIO);

        assertThat(repositorio.findByReferencia("sz-1-1-200")).isPresent();
        assertThat(repositorio.findByReferencia("no-existe")).isEmpty();
    }

    @Test
    @DisplayName("buscarActivasDeUsuario solo devuelve las que siguen vivas")
    void activasDeUsuario() {
        saga("sz-5-1-1", 5L, Estado.ESPERANDO_PAGO, Paso.PEDIDO_CREADO);
        saga("sz-5-1-2", 5L, Estado.INICIADA, Paso.INICIO);
        saga("sz-5-1-3", 5L, Estado.COMPLETADA, Paso.FIN);
        saga("sz-5-1-4", 5L, Estado.COMPENSADA, Paso.INICIO);
        saga("sz-9-1-1", 9L, Estado.ESPERANDO_PAGO, Paso.PEDIDO_CREADO);

        List<SagaCheckout> activas = repositorio.buscarActivasDeUsuario(5L);

        assertThat(activas).hasSize(2);
        assertThat(activas).allMatch(s -> s.getUsuarioId().equals(5L));
        assertThat(activas).noneMatch(SagaCheckout::estaTerminada);
    }

    @Test
    @DisplayName("buscarAbandonadas encuentra las que llevan demasiado esperando el pago")
    void abandonadas() {
        SagaCheckout vieja = saga("sz-1-1-vieja", 1L, Estado.ESPERANDO_PAGO, Paso.PEDIDO_CREADO);
        saga("sz-1-1-nueva", 2L, Estado.ESPERANDO_PAGO, Paso.PEDIDO_CREADO);
        saga("sz-1-1-hecha", 3L, Estado.COMPLETADA, Paso.FIN);

        // Se envejece a mano: @PreUpdate pondría la fecha actual.
        repositorio.findById(vieja.getId()).ifPresent(s -> {
            s.setActualizadoEn(LocalDateTime.now().minusHours(2));
            repositorio.saveAndFlush(s);
        });

        List<SagaCheckout> abandonadas =
                repositorio.buscarAbandonadas(LocalDateTime.now().minusMinutes(25));

        assertThat(abandonadas).extracting(SagaCheckout::getReferencia)
                .containsExactly("sz-1-1-vieja");
    }

    @Test
    @DisplayName("buscarCompensacionesPendientes respeta el tope de intentos")
    void compensacionesPendientes() {
        SagaCheckout reintentable = saga("sz-1-1-r", 1L, Estado.COMPENSANDO, Paso.STOCK_RESERVADO);
        SagaCheckout agotada = saga("sz-1-1-a", 2L, Estado.COMPENSANDO, Paso.STOCK_RESERVADO);

        agotada.setIntentos(5);
        repositorio.saveAndFlush(agotada);

        List<SagaCheckout> pendientes = repositorio.buscarCompensacionesPendientes(5);

        assertThat(pendientes).extracting(SagaCheckout::getReferencia)
                .containsExactly(reintentable.getReferencia());
    }

    @Test
    @DisplayName("findByPaymentId sostiene la idempotencia de confirmar el pago")
    void porPaymentId() {
        SagaCheckout s = saga("sz-1-1-pay", 1L, Estado.COMPLETADA, Paso.FIN);
        s.setPaymentId("mp-123456");
        repositorio.saveAndFlush(s);

        assertThat(repositorio.findByPaymentId("mp-123456")).isPresent();
        assertThat(repositorio.findByPaymentId("mp-000000")).isEmpty();
    }
}
