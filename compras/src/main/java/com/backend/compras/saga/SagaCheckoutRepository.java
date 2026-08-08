package com.backend.compras.saga;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SagaCheckoutRepository extends JpaRepository<SagaCheckout, Long> {

    Optional<SagaCheckout> findByReferencia(String referencia);

    Optional<SagaCheckout> findByPaymentId(String paymentId);

    /** Saga viva del usuario: evita abrir dos checkouts en paralelo. */
    @Query("""
            SELECT s FROM SagaCheckout s
            WHERE s.usuarioId = :usuarioId
              AND s.estado IN (com.backend.compras.saga.SagaCheckout$Estado.INICIADA,
                               com.backend.compras.saga.SagaCheckout$Estado.ESPERANDO_PAGO)
            ORDER BY s.creadoEn DESC
            """)
    List<SagaCheckout> buscarActivasDeUsuario(@Param("usuarioId") Long usuarioId);

    /** Sagas que llevan demasiado tiempo esperando un pago que no llegó. */
    @Query("""
            SELECT s FROM SagaCheckout s
            WHERE s.estado = com.backend.compras.saga.SagaCheckout$Estado.ESPERANDO_PAGO
              AND s.actualizadoEn < :limite
            """)
    List<SagaCheckout> buscarAbandonadas(@Param("limite") LocalDateTime limite);

    /** Compensaciones que fallaron y hay que reintentar. */
    @Query("""
            SELECT s FROM SagaCheckout s
            WHERE s.estado = com.backend.compras.saga.SagaCheckout$Estado.COMPENSANDO
              AND s.intentos < :maxIntentos
            """)
    List<SagaCheckout> buscarCompensacionesPendientes(@Param("maxIntentos") int maxIntentos);
}
