package com.backend.usuarios.auth.recuperacion;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface CodigoRecuperacionRepository extends JpaRepository<CodigoRecuperacion, Long> {

    /**
     * El último código emitido a la cuenta: es el único que puede valer.
     *
     * <p>Bloquea la fila ({@code SELECT … FOR UPDATE}) hasta el final de la
     * transacción. Sin el bloqueo, varios intentos en paralelo leían el mismo
     * {@code intentos}, cada uno guardaba +1 sobre él y los incrementos se
     * pisaban: mandando peticiones a la vez se probaban más de cinco códigos.
     * Con él, los intentos sobre un mismo código van de uno en uno, y el que
     * acierta lo marca usado antes de que el siguiente pueda leerlo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CodigoRecuperacion> findFirstByUsuarioIdOrderByCreadoEnDescIdDesc(Long usuarioId);

    /** Los que aún no se usaron, para anularlos al emitir uno nuevo. */
    List<CodigoRecuperacion> findByUsuarioIdAndUsadoEnIsNull(Long usuarioId);
}
