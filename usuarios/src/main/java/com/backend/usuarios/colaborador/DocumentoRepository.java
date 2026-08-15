package com.backend.usuarios.colaborador;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentoRepository extends JpaRepository<DocumentoIdentidad, Long> {

    /** Los adjuntos de una solicitud, para la bandeja del administrador. */
    List<DocumentoIdentidad> findBySolicitudIdOrderByTipoAsc(Long solicitudId);

    /**
     * Los adjuntos de varias solicitudes de una vez. Es lo que evita que pintar
     * la bandeja lance una consulta por fila.
     */
    List<DocumentoIdentidad> findBySolicitudIdIn(List<Long> solicitudIds);

    /**
     * Los que el usuario ha subido y todavía no ha enviado con nada. Es lo que
     * se reclama al crear la solicitud.
     */
    List<DocumentoIdentidad> findByUsuarioIdAndSolicitudIdIsNull(Long usuarioId);

    /**
     * El más reciente de cada tipo entre los sueltos.
     *
     * <p>Hace falta porque el usuario puede subir una foto, verla borrosa y
     * subir otra: las dos quedan sin asignar y al enviar hay que quedarse con la
     * última, no fallar por duplicado ni elegir al azar.
     */
    Optional<DocumentoIdentidad> findFirstByUsuarioIdAndTipoAndSolicitudIdIsNullOrderBySubidoEnDesc(
            Long usuarioId, TipoAdjunto tipo);

    /**
     * Candidatos a purga: los huérfanos viejos y los de solicitudes ya
     * resueltas hace tiempo. Se piden en una sola consulta para no traer toda la
     * tabla y filtrar en memoria.
     */
    @Query("""
            SELECT d FROM DocumentoIdentidad d
            WHERE d.purgadoEn IS NULL
              AND (
                    (d.solicitudId IS NULL AND d.subidoEn < :corteHuerfanos)
                 OR (d.solicitudId IN (
                        SELECT s.id FROM SolicitudColaborador s
                        WHERE s.resueltaEn IS NOT NULL AND s.resueltaEn < :corteResueltas))
              )
            """)
    List<DocumentoIdentidad> buscarPurgables(@Param("corteHuerfanos") Instant corteHuerfanos,
            @Param("corteResueltas") Instant corteResueltas);
}
