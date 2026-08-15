package com.backend.usuarios.colaborador;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitudRepository extends JpaRepository<SolicitudColaborador, Long> {

    /** La última del usuario, sea cual sea su estado: es lo que enseña "mi solicitud". */
    Optional<SolicitudColaborador> findFirstByUsuarioIdOrderByCreadaEnDesc(Long usuarioId);

    /**
     * Comprobación previa para dar un 409 con mensaje claro. No es la defensa
     * real: dos peticiones a la vez pasarían las dos por aquí. Quien de verdad
     * impide la segunda pendiente es el índice único parcial de la migración.
     */
    boolean existsByUsuarioIdAndEstado(Long usuarioId, EstadoSolicitud estado);

    /**
     * ¿Hay otra cuenta con ese documento en curso o ya aprobado?
     *
     * <p>Se excluye al propio usuario para que quien fue rechazado pueda volver
     * a solicitar con su mismo RUC. Las rechazadas no cuentan: si a alguien le
     * denegaron el negocio, ese documento vuelve a quedar libre.
     */
    @Query("""
            SELECT COUNT(s) > 0 FROM SolicitudColaborador s
            WHERE s.documento = :documento
              AND s.usuarioId <> :usuarioId
              AND s.estado IN (com.backend.usuarios.colaborador.EstadoSolicitud.PENDIENTE,
                               com.backend.usuarios.colaborador.EstadoSolicitud.APROBADA)
            """)
    boolean existsPorDocumentoEnCurso(@Param("documento") String documento,
            @Param("usuarioId") Long usuarioId);

    /** Bandeja filtrada por estado, lo más reciente primero. */
    List<SolicitudColaborador> findByEstadoOrderByCreadaEnDesc(EstadoSolicitud estado);

    /**
     * Bandeja completa con las pendientes arriba: son las únicas sobre las que
     * el administrador tiene algo que hacer.
     */
    List<SolicitudColaborador> findAllByOrderByEstadoAscCreadaEnDesc();
}
