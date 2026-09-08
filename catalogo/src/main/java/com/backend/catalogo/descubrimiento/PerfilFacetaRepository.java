package com.backend.catalogo.descubrimiento;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * El perfil de intereses.
 *
 * <p>Todo lo que escribe aquí lo hace con un UPSERT que aplica el olvido en el
 * momento: {@code score = score × 2^(−Δt/vidaMedia) + peso}. Es una sentencia
 * por evento, en tiempo constante, sin trabajo nocturno y sin recorrer nunca el
 * historial. Recalcular el perfil leyendo los eventos sería el diseño obvio y
 * el que no aguanta.
 */
public interface PerfilFacetaRepository extends JpaRepository<PerfilFaceta, PerfilFaceta.Id> {

    /** El upsert con decaimiento, para una faceta suelta. */
    @Modifying
    @Query(value = """
            INSERT INTO catalogo.perfil_faceta (sujeto_id, tipo_faceta, faceta, score, eventos, actualizado_en)
            VALUES (:sujeto, :tipoFaceta, :faceta, :peso, 1, now())
            ON CONFLICT (sujeto_id, tipo_faceta, faceta) DO UPDATE SET
                score = perfil_faceta.score
                        * POWER(2, - EXTRACT(EPOCH FROM (now() - perfil_faceta.actualizado_en))
                                   / :vidaMediaSeg)
                        + EXCLUDED.score,
                eventos = perfil_faceta.eventos + 1,
                actualizado_en = now()
            """, nativeQuery = true)
    int acumular(@Param("sujeto") UUID sujeto, @Param("tipoFaceta") String tipoFaceta,
            @Param("faceta") String faceta, @Param("peso") double peso,
            @Param("vidaMediaSeg") long vidaMediaSeg);

    /**
     * Acredita una categoría Y TODAS SUS ANCESTRAS, con atenuación.
     *
     * <p>Ver cinco laptops no debería enseñar solo «laptops». La categoría
     * vista se lleva el peso entero, su madre la mitad y la abuela un cuarto,
     * de modo que el sistema aprende que a esta persona le interesa
     * «Computación» y puede ofrecerle monitores, que nunca visitó.
     *
     * <p>Todo en UNA sentencia, con un CTE recursivo sobre la jerarquía de
     * {@code categoria}. Subir el árbol en Java serían N consultas por evento.
     * El tope de seis niveles es un cinturón: la jerarquía no puede tener
     * ciclos —lo impiden un CHECK y el servicio— pero un bucle infinito dentro
     * de la base sería muy caro de diagnosticar.
     */
    @Modifying
    @Query(value = """
            WITH RECURSIVE cadena(id, padre, slug, factor, nivel) AS (
                SELECT c.id, c.categoria_padre_id, c.slug, CAST(1 AS numeric), 1
                  FROM catalogo.categoria c WHERE c.id = :categoriaId
                UNION ALL
                SELECT a.id, a.categoria_padre_id, a.slug,
                       cadena.factor * CAST(:atenuacion AS numeric), cadena.nivel + 1
                  FROM catalogo.categoria a
                  JOIN cadena ON a.id = cadena.padre
                 WHERE cadena.nivel < 6
            )
            INSERT INTO catalogo.perfil_faceta (sujeto_id, tipo_faceta, faceta, score, eventos, actualizado_en)
            SELECT :sujeto, 'CATEGORIA', cadena.slug,
                   CAST(:peso AS numeric) * cadena.factor, 1, now()
              FROM cadena
            ON CONFLICT (sujeto_id, tipo_faceta, faceta) DO UPDATE SET
                score = perfil_faceta.score
                        * POWER(2, - EXTRACT(EPOCH FROM (now() - perfil_faceta.actualizado_en))
                                   / :vidaMediaSeg)
                        + EXCLUDED.score,
                eventos = perfil_faceta.eventos + 1,
                actualizado_en = now()
            """, nativeQuery = true)
    int acumularCategoriaConAncestros(@Param("sujeto") UUID sujeto,
            @Param("categoriaId") Long categoriaId, @Param("peso") double peso,
            @Param("atenuacion") double atenuacion, @Param("vidaMediaSeg") long vidaMediaSeg);

    /**
     * Acredita las características FILTRABLES del producto que se miró.
     *
     * <p>De aquí sale que a alguien le interesan las 27 pulgadas y los 144 Hz
     * sin que lo haya dicho nunca. Solo las marcadas como filtrables: el resto
     * son datos de ficha —peso, garantía— que describen el producto pero no
     * discriminan gustos, y meterlas llenaría el perfil de ruido.
     *
     * <p>Es posible porque los atributos ya son filas en
     * {@code producto_atributo}; con las especificaciones en un bloque de texto
     * habría que parsear Markdown en cada evento.
     */
    @Modifying
    @Query(value = """
            INSERT INTO catalogo.perfil_faceta (sujeto_id, tipo_faceta, faceta, score, eventos, actualizado_en)
            SELECT :sujeto, 'ATRIBUTO', a.codigo || '=' || pa.valor,
                   CAST(:peso AS numeric), 1, now()
              FROM catalogo.producto_atributo pa
              JOIN catalogo.atributo a ON a.id = pa.atributo_id
             WHERE pa.producto_id = :productoId AND a.filtrable = TRUE
            ON CONFLICT (sujeto_id, tipo_faceta, faceta) DO UPDATE SET
                score = perfil_faceta.score
                        * POWER(2, - EXTRACT(EPOCH FROM (now() - perfil_faceta.actualizado_en))
                                   / :vidaMediaSeg)
                        + EXCLUDED.score,
                eventos = perfil_faceta.eventos + 1,
                actualizado_en = now()
            """, nativeQuery = true)
    int acumularAtributosDe(@Param("sujeto") UUID sujeto, @Param("productoId") Long productoId,
            @Param("peso") double peso, @Param("vidaMediaSeg") long vidaMediaSeg);

    /** Acredita la marca del producto que se miró. */
    @Modifying
    @Query(value = """
            INSERT INTO catalogo.perfil_faceta (sujeto_id, tipo_faceta, faceta, score, eventos, actualizado_en)
            SELECT :sujeto, 'MARCA', m.name, CAST(:peso AS numeric), 1, now()
              FROM catalogo.producto p JOIN catalogo.marca m ON m.id = p.marca_id
             WHERE p.id = :productoId
            ON CONFLICT (sujeto_id, tipo_faceta, faceta) DO UPDATE SET
                score = perfil_faceta.score
                        * POWER(2, - EXTRACT(EPOCH FROM (now() - perfil_faceta.actualizado_en))
                                   / :vidaMediaSeg)
                        + EXCLUDED.score,
                eventos = perfil_faceta.eventos + 1,
                actualizado_en = now()
            """, nativeQuery = true)
    int acumularMarcaDe(@Param("sujeto") UUID sujeto, @Param("productoId") Long productoId,
            @Param("peso") double peso, @Param("vidaMediaSeg") long vidaMediaSeg);

    /**
     * Las facetas más fuertes de un tipo.
     *
     * <p>Sin aplicar el olvido al leer: lo aplica cada escritura. Aquí se
     * ordena por el score tal como quedó, que es lo que hace que esta consulta
     * sea un simple recorrido de índice.
     */
    @Query("""
            SELECT f FROM PerfilFaceta f
             WHERE f.id.sujetoId = :sujeto AND f.id.tipoFaceta = :tipo AND f.score > 0
             ORDER BY f.score DESC
            """)
    List<PerfilFaceta> top(@Param("sujeto") UUID sujeto, @Param("tipo") TipoFaceta tipo,
            Pageable pagina);

    /** Todo el perfil, para la pantalla «Tus intereses» y para los derechos ARCO. */
    @Query("SELECT f FROM PerfilFaceta f WHERE f.id.sujetoId = :sujeto ORDER BY f.score DESC")
    List<PerfilFaceta> todasDe(@Param("sujeto") UUID sujeto);

    long countByIdSujetoId(UUID sujetoId);
}
