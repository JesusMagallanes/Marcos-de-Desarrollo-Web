package com.backend.catalogo.descubrimiento;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.backend.catalogo.producto.Producto;

/**
 * La etapa de RECUPERACIÓN del recomendador.
 *
 * <p>Cada método es una fuente de candidatos distinta. Devuelven muchos y
 * baratos; filtrar, ordenar fino y diversificar es cosa de
 * {@code RecomendacionService}. Separar recuperación de ranking es lo que
 * permite que el coste no crezca con el catálogo.
 *
 * <p>Todas las consultas excluyen lo no aprobado y lo agotado en la propia base
 * y no en Java: traer candidatos para descartarlos después desperdicia la mitad
 * del presupuesto de latencia.
 */
public interface CandidatoRepository extends Repository<Producto, Long> {

    /**
     * SEGÚN TUS INTERESES · el perfil se cruza con el catálogo.
     *
     * <p>Suma tres afinidades sobre el mismo producto: la de su categoría, la
     * de su marca y la de sus características. Cada faceta se pondera por la
     * confianza —{@code 1 − e^(−n/k)}— para que un interés sostenido por un
     * solo evento no domine el Home.
     *
     * <p>Los factores 0,6 y 0,8 dicen que coincidir en marca vale menos que
     * coincidir en categoría, y coincidir en característica algo menos que
     * ninguna de las dos por separado pero mucho si se acumulan varias.
     */
    @Query(value = """
            WITH perfil AS (
                SELECT tipo_faceta, faceta,
                       score * (1 - EXP(- CAST(eventos AS numeric) / CAST(:confianzaK AS numeric)))
                           AS peso
                  FROM catalogo.perfil_faceta
                 WHERE sujeto_id = :sujeto AND score > 0
            ),
            aportes AS (
                SELECT p.id, SUM(f.peso) AS s
                  FROM catalogo.producto p
                  JOIN catalogo.categoria c ON c.id = p.categoria_id
                  JOIN perfil f ON f.tipo_faceta = 'CATEGORIA' AND f.faceta = c.slug
                 GROUP BY p.id
                UNION ALL
                SELECT p.id, SUM(f.peso) * 0.6
                  FROM catalogo.producto p
                  JOIN catalogo.marca m ON m.id = p.marca_id
                  JOIN perfil f ON f.tipo_faceta = 'MARCA' AND f.faceta = m.name
                 GROUP BY p.id
                UNION ALL
                SELECT pa.producto_id, SUM(f.peso) * 0.8
                  FROM catalogo.producto_atributo pa
                  JOIN catalogo.atributo a ON a.id = pa.atributo_id
                  JOIN perfil f ON f.tipo_faceta = 'ATRIBUTO'
                                AND f.faceta = a.codigo || '=' || pa.valor
                 GROUP BY pa.producto_id
            )
            SELECT p.id            AS "itemId",
                   p.categoria_id  AS "categoriaId",
                   p.marca_id      AS "marcaId",
                   CAST(SUM(a.s) AS double precision) AS "score"
              FROM aportes a
              JOIN catalogo.producto p ON p.id = a.id
             WHERE p.estado_moderacion = 'APROBADO'
               AND p.stock > 0
               AND p.id NOT IN (:excluidos)
             GROUP BY p.id, p.categoria_id, p.marca_id
             ORDER BY 4 DESC
             LIMIT :limite
            """, nativeQuery = true)
    List<Candidato> segunIntereses(@Param("sujeto") UUID sujeto,
            @Param("confianzaK") double confianzaK,
            @Param("excluidos") List<Long> excluidos,
            @Param("limite") int limite);

    /**
     * SIMILARES · por contenido, no por comportamiento.
     *
     * <p>Funciona el primer día y con productos recién dados de alta, que es
     * justo donde la co-visita no tiene nada que decir: el arranque en frío de
     * un producto nuevo se resuelve por lo que ES, no por quién lo miró.
     *
     * <p>Los candidatos se acotan primero a la misma categoría o marca y solo
     * después se puntúan por características compartidas. Sin ese cerco habría
     * que recorrer {@code producto_atributo} entero en cada ficha.
     *
     * <p>Cuando haya volumen, esto se complementa —no se sustituye— con la
     * co-visita precalculada de la fase 2.
     */
    @Query(value = """
            WITH ref AS (
                SELECT categoria_id, marca_id FROM catalogo.producto WHERE id = :itemId
            ),
            origen AS (
                SELECT atributo_id, valor FROM catalogo.producto_atributo WHERE producto_id = :itemId
            ),
            candidatos AS (
                SELECT p.id, p.categoria_id, p.marca_id
                  FROM catalogo.producto p, ref
                 WHERE p.id <> :itemId
                   AND p.estado_moderacion = 'APROBADO'
                   AND p.stock > 0
                   AND (p.categoria_id = ref.categoria_id OR p.marca_id = ref.marca_id)
            )
            SELECT c.id           AS "itemId",
                   c.categoria_id AS "categoriaId",
                   c.marca_id     AS "marcaId",
                   CAST(
                     COUNT(o.atributo_id)
                     + CASE WHEN c.categoria_id = (SELECT categoria_id FROM ref) THEN 3 ELSE 0 END
                     + CASE WHEN c.marca_id     = (SELECT marca_id FROM ref)     THEN 1.5 ELSE 0 END
                   AS double precision) AS "score"
              FROM candidatos c
              LEFT JOIN catalogo.producto_atributo pa ON pa.producto_id = c.id
              LEFT JOIN origen o ON o.atributo_id = pa.atributo_id AND o.valor = pa.valor
             GROUP BY c.id, c.categoria_id, c.marca_id
             ORDER BY 4 DESC
             LIMIT :limite
            """, nativeQuery = true)
    List<Candidato> similaresPorContenido(@Param("itemId") Long itemId,
            @Param("limite") int limite);

    /**
     * ARRANQUE EN FRÍO · para quien todavía no ha hecho nada.
     *
     * <p>Ordena por valoración y novedad, no por ventas: un producto recién
     * publicado no puede quedar condenado por no tener historia. La
     * completitud de la ficha —que tenga imágenes y características— entra en
     * el score porque una ficha vacía decepciona al que llega por primera vez,
     * que es el peor momento para decepcionar.
     */
    @Query(value = """
            SELECT p.id           AS "itemId",
                   p.categoria_id AS "categoriaId",
                   p.marca_id     AS "marcaId",
                   CAST(
                     COALESCE(AVG(v.calificacion), 3.5)
                     + LEAST(COUNT(DISTINCT img.id), 4) * 0.25
                     + LEAST(COUNT(DISTINCT pa.atributo_id), 6) * 0.15
                   AS double precision) AS "score"
              FROM catalogo.producto p
              LEFT JOIN catalogo.valoracion v ON v.producto_id = p.id
              LEFT JOIN catalogo.producto_imagen img ON img.producto_id = p.id
              LEFT JOIN catalogo.producto_atributo pa ON pa.producto_id = p.id
             WHERE p.estado_moderacion = 'APROBADO'
               AND p.stock > 0
               AND (:categoriaId IS NULL OR p.categoria_id = :categoriaId)
               AND p.id NOT IN (:excluidos)
             GROUP BY p.id, p.categoria_id, p.marca_id
             ORDER BY 4 DESC
             LIMIT :limite
            """, nativeQuery = true)
    List<Candidato> populares(@Param("categoriaId") Long categoriaId,
            @Param("excluidos") List<Long> excluidos, @Param("limite") int limite);

    /**
     * EXPLORACIÓN · categorías que este sujeto todavía no ha pisado.
     *
     * <p>Sin esto el recomendador converge: solo enseña lo que ya se miró, el
     * Home se repite y la sesión se acorta. Se buscan las categorías hermanas
     * de las que sí le interesan —comparten madre en el árbol— y de las que no
     * tiene ninguna faceta.
     *
     * <p>Es exploración dirigida, no aleatoria: una hermana de «Laptops» tiene
     * mucho más sentido que una categoría al azar del catálogo.
     */
    @Query(value = """
            WITH suyas AS (
                SELECT c.id, c.categoria_padre_id
                  FROM catalogo.perfil_faceta f
                  JOIN catalogo.categoria c ON c.slug = f.faceta
                 WHERE f.sujeto_id = :sujeto AND f.tipo_faceta = 'CATEGORIA' AND f.score > 0
            ),
            hermanas AS (
                SELECT DISTINCT h.id
                  FROM suyas s
                  JOIN catalogo.categoria h ON h.categoria_padre_id = s.categoria_padre_id
                 WHERE s.categoria_padre_id IS NOT NULL
                   AND h.id NOT IN (SELECT id FROM suyas)
            )
            SELECT p.id           AS "itemId",
                   p.categoria_id AS "categoriaId",
                   p.marca_id     AS "marcaId",
                   CAST(COALESCE(AVG(v.calificacion), 3.5) AS double precision) AS "score"
              FROM catalogo.producto p
              JOIN hermanas h ON h.id = p.categoria_id
              LEFT JOIN catalogo.valoracion v ON v.producto_id = p.id
             WHERE p.estado_moderacion = 'APROBADO'
               AND p.stock > 0
               AND p.id NOT IN (:excluidos)
             GROUP BY p.id, p.categoria_id, p.marca_id
             ORDER BY 4 DESC
             LIMIT :limite
            """, nativeQuery = true)
    List<Candidato> paraExplorar(@Param("sujeto") UUID sujeto,
            @Param("excluidos") List<Long> excluidos, @Param("limite") int limite);
}
