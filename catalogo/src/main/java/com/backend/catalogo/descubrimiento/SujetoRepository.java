package com.backend.catalogo.descubrimiento;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SujetoRepository extends JpaRepository<Sujeto, UUID> {

    /** El sujeto vivo de una cuenta. Los fusionados quedan fuera. */
    @Query("SELECT s FROM Sujeto s WHERE s.usuarioId = :usuarioId AND s.fusionadoEn IS NULL")
    Optional<Sujeto> buscarVivoDeUsuario(@Param("usuarioId") Long usuarioId);

    /**
     * Traslada todo lo del sujeto anónimo al de la cuenta.
     *
     * <p>Va en SQL y no cargando entidades porque puede mover miles de filas y
     * cargarlas para reasignarles un campo sería un desperdicio.
     */
    @Modifying
    @Query(value = "UPDATE catalogo.evento_interaccion SET sujeto_id = :destino WHERE sujeto_id = :origen",
            nativeQuery = true)
    int moverEventos(@Param("origen") UUID origen, @Param("destino") UUID destino);

    @Modifying
    @Query(value = "UPDATE catalogo.impresion SET sujeto_id = :destino WHERE sujeto_id = :origen",
            nativeQuery = true)
    int moverImpresiones(@Param("origen") UUID origen, @Param("destino") UUID destino);

    /**
     * Funde las facetas del anónimo en las de la cuenta.
     *
     * <p>Suma los scores en vez de reasignar filas, porque las dos partes
     * pueden tener la misma faceta y la clave primaria lo impediría. Lo que ya
     * traía la cuenta se decae hasta ahora antes de sumarle lo del anónimo, o
     * un perfil viejo pesaría como si fuera de hoy.
     */
    @Modifying
    @Query(value = """
            INSERT INTO catalogo.perfil_faceta (sujeto_id, tipo_faceta, faceta, score, eventos, actualizado_en)
            SELECT :destino, o.tipo_faceta, o.faceta, o.score, o.eventos, now()
              FROM catalogo.perfil_faceta o
             WHERE o.sujeto_id = :origen
            ON CONFLICT (sujeto_id, tipo_faceta, faceta) DO UPDATE SET
                score = perfil_faceta.score
                        * POWER(2, - EXTRACT(EPOCH FROM (now() - perfil_faceta.actualizado_en))
                                   / :vidaMediaSeg)
                        + EXCLUDED.score,
                eventos = perfil_faceta.eventos + EXCLUDED.eventos,
                actualizado_en = now()
            """, nativeQuery = true)
    int fundirPerfil(@Param("origen") UUID origen, @Param("destino") UUID destino,
            @Param("vidaMediaSeg") long vidaMediaSeg);

    @Modifying
    @Query(value = """
            INSERT INTO catalogo.item_descartado (sujeto_id, item_tipo, item_id, motivo, creado_en)
            SELECT :destino, o.item_tipo, o.item_id, o.motivo, o.creado_en
              FROM catalogo.item_descartado o WHERE o.sujeto_id = :origen
            ON CONFLICT (sujeto_id, item_tipo, item_id) DO NOTHING
            """, nativeQuery = true)
    int fundirDescartes(@Param("origen") UUID origen, @Param("destino") UUID destino);

    @Modifying
    @Query(value = "DELETE FROM catalogo.perfil_faceta WHERE sujeto_id = :sujeto", nativeQuery = true)
    int borrarPerfil(@Param("sujeto") UUID sujeto);
}
