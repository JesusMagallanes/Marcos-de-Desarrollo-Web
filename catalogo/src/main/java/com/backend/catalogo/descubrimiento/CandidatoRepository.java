package com.backend.catalogo.descubrimiento;

import java.time.Instant;
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

    /**
     * COLABORATIVO · ítem a ítem, por co-interacción.
     *
     * <p>Parte de lo último que esta persona tocó y salta a lo que la gente que
     * tocó eso mismo tocó también. Es la única vía por la que el sistema puede
     * proponer un brazo articulado a quien mira monitores: no comparten
     * categoría, ni marca, ni un solo atributo. Solo comparten público.
     *
     * <p>Acotada por los dos extremos: unas pocas semillas —lo más reciente, que
     * es lo que describe el interés de ahora— y, por cada una, un recorrido del
     * índice de {@code item_relacion}. No hay recorrido de catálogo ni
     * comparación contra otros sujetos: eso ya lo pagó el proceso por lotes.
     *
     * <p>Se excluye lo que la propia persona ya tocó. Recomendarle lo que acaba
     * de ver es el error más visible que puede cometer un recomendador.
     */
    @Query(value = """
            WITH semilla AS (
                SELECT e.item_id, MAX(e.ocurrido_en) AS ultimo
                  FROM catalogo.evento_interaccion e
                 WHERE e.sujeto_id = :sujeto
                   AND e.item_tipo = 'PRODUCTO'
                   AND e.item_id IS NOT NULL
                   AND e.tipo IN ('ITEM_VIEW', 'ITEM_VIEW_DEEP', 'ADD_TO_CART',
                                  'FAVORITE', 'PURCHASE')
                 GROUP BY e.item_id
                 ORDER BY MAX(e.ocurrido_en) DESC
                 LIMIT :semillas
            )
            SELECT p.id           AS "itemId",
                   p.categoria_id AS "categoriaId",
                   p.marca_id     AS "marcaId",
                   CAST(SUM(r.score) AS double precision) AS "score"
              FROM semilla s
              JOIN catalogo.item_relacion r
                ON r.item_tipo = 'PRODUCTO' AND r.item_a = s.item_id
              JOIN catalogo.producto p ON p.id = r.item_b
             WHERE p.estado_moderacion = 'APROBADO'
               AND p.stock > 0
               AND p.id NOT IN (:excluidos)
               AND NOT EXISTS (SELECT 1 FROM semilla ya WHERE ya.item_id = p.id)
             GROUP BY p.id, p.categoria_id, p.marca_id
             ORDER BY 4 DESC
             LIMIT :limite
            """, nativeQuery = true)
    List<Candidato> porCoVisita(@Param("sujeto") UUID sujeto,
            @Param("semillas") int semillas,
            @Param("excluidos") List<Long> excluidos,
            @Param("limite") int limite);

    /**
     * COLABORATIVO · lo que se mira junto con UN producto concreto.
     *
     * <p>La misma tabla que {@link #porCoVisita}, pero con una sola semilla: la
     * ficha que se está viendo. No necesita sujeto, y eso la hace valiosa
     * justo donde el perfil todavía no existe —el visitante que llega desde una
     * búsqueda externa y aterriza en un producto—, que es cuando la fase 1 no
     * tiene absolutamente nada que decir.
     */
    @Query(value = """
            SELECT p.id           AS "itemId",
                   p.categoria_id AS "categoriaId",
                   p.marca_id     AS "marcaId",
                   CAST(r.score AS double precision) AS "score"
              FROM catalogo.item_relacion r
              JOIN catalogo.producto p ON p.id = r.item_b
             WHERE r.item_tipo = 'PRODUCTO'
               AND r.item_a = :itemId
               AND p.estado_moderacion = 'APROBADO'
               AND p.stock > 0
               AND p.id NOT IN (:excluidos)
             ORDER BY r.score DESC
             LIMIT :limite
            """, nativeQuery = true)
    List<Candidato> porCoVisitaDeItem(@Param("itemId") Long itemId,
            @Param("excluidos") List<Long> excluidos,
            @Param("limite") int limite);

    /**
     * COLABORATIVO · por sujetos de perfil parecido.
     *
     * <p>Lo que descubrió gente cuyo gusto se parece al de esta persona y que
     * ella todavía no ha visto. Es la señal que rompe la burbuja de la fase 1:
     * el perfil propio solo puede devolver más de lo mismo, porque está hecho
     * exactamente de lo mismo.
     *
     * <p><b>Cada vecino cuenta una vez por producto.</b> El {@code DISTINCT}
     * del interior es lo que impide que un vecino especialmente insistente
     * decida él solo la recomendación: aporta su parecido, no su número de
     * clics. El score suma los parecidos de quienes coincidieron, de modo que
     * un producto que gustó a cinco vecinos flojos puede ganar a uno que gustó a
     * un vecino muy parecido, que es lo correcto.
     *
     * <p>Nada de esto sale del backend. Ver {@code SujetoSimilitud}.
     */
    @Query(value = """
            WITH vecino AS (
                SELECT s.sujeto_b, s.score
                  FROM catalogo.sujeto_similitud s
                 WHERE s.sujeto_a = :sujeto
                   AND s.score >= :minSimilitud
                 ORDER BY s.score DESC
                 LIMIT :vecinos
            ),
            ajeno AS (
                SELECT DISTINCT e.sujeto_id, e.item_id
                  FROM catalogo.evento_interaccion e
                 WHERE e.item_tipo = 'PRODUCTO'
                   AND e.item_id IS NOT NULL
                   AND e.ocurrido_en >= :desde
                   AND e.tipo IN ('ITEM_VIEW_DEEP', 'ADD_TO_CART', 'FAVORITE', 'PURCHASE')
            ),
            propio AS (
                SELECT DISTINCT e.item_id
                  FROM catalogo.evento_interaccion e
                 WHERE e.sujeto_id = :sujeto AND e.item_id IS NOT NULL
            )
            SELECT p.id           AS "itemId",
                   p.categoria_id AS "categoriaId",
                   p.marca_id     AS "marcaId",
                   CAST(SUM(v.score) AS double precision) AS "score"
              FROM vecino v
              JOIN ajeno a ON a.sujeto_id = v.sujeto_b
              JOIN catalogo.producto p ON p.id = a.item_id
             WHERE p.estado_moderacion = 'APROBADO'
               AND p.stock > 0
               AND p.id NOT IN (:excluidos)
               AND NOT EXISTS (SELECT 1 FROM propio yo WHERE yo.item_id = p.id)
             GROUP BY p.id, p.categoria_id, p.marca_id
            /*
             * El piso de privacidad de este generador.
             *
             * Sin el, un unico vecino podria aportar el carrusel entero, y eso
             * no es una recomendacion agregada: es ensenarle a alguien lo que
             * ha estado mirando otra persona. Que hagan falta varios vecinos
             * distintos para que un producto salga es lo que convierte el dato
             * individual en un patron. Con pocos usuarios esto devuelve vacio, y
             * devolver vacio es la respuesta correcta: el Home cae a los otros
             * modulos y nadie queda expuesto por ser de los primeros.
             */
            HAVING COUNT(DISTINCT v.sujeto_b) >= :minAportantes
             ORDER BY 4 DESC
             LIMIT :limite
            """, nativeQuery = true)
    List<Candidato> porSujetosSimilares(@Param("sujeto") UUID sujeto,
            @Param("minSimilitud") double minSimilitud,
            @Param("vecinos") int vecinos,
            @Param("desde") Instant desde,
            @Param("minAportantes") int minAportantes,
            @Param("excluidos") List<Long> excluidos,
            @Param("limite") int limite);
}
