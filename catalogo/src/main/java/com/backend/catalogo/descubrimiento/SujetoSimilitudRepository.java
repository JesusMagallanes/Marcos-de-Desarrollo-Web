package com.backend.catalogo.descubrimiento;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * El parecido entre perfiles, calculado sobre el vector que la fase 1 ya tenía.
 */
public interface SujetoSimilitudRepository
        extends JpaRepository<SujetoSimilitud, SujetoSimilitud.Id> {

    /**
     * Recalcula los vecinos de cada sujeto activo en la ventana.
     *
     * <p><b>Por qué esto no es O(N²).</b> La forma ingenua —comparar cada sujeto
     * con todos los demás— es justo lo que no puede hacerse. Aquí el cruce va
     * por faceta compartida, así que dos sujetos que no coinciden en nada no
     * llegan a encontrarse nunca. Eso deja un único peligro real, y es la faceta
     * que tiene TODO el mundo: si el 90 % de los perfiles contiene
     * «categoria:tecnologia», esa sola fila del cruce vuelve a ser el producto
     * cartesiano.
     *
     * <p>La defensa es la misma idea que hay detrás de IDF, y además es la
     * correcta desde el punto de vista de la recomendación: una faceta que
     * comparte casi todo el mundo NO DISTINGUE a nadie. Saber que a dos personas
     * les interesa la tecnología en una tienda de tecnología no dice nada. Se
     * descartan del cruce las facetas con más de {@code topeSujetosPorFaceta}
     * sujetos: se gana rendimiento y se gana precisión a la vez.
     *
     * <p>Se exige además un mínimo de facetas compartidas. Coincidir en una sola
     * cosa no es parecerse, y el coseno de un vector de dimensión uno vale
     * siempre 1: sin este mínimo, dos desconocidos que miraron el mismo teclado
     * saldrían como almas gemelas.
     */
    @Modifying
    @Query(value = """
            WITH activo AS (
                SELECT DISTINCT e.sujeto_id
                  FROM catalogo.evento_interaccion e
                 WHERE e.ocurrido_en >= :desde
            ),
            faceta AS (
                SELECT f.sujeto_id, f.tipo_faceta, f.faceta, f.score
                  FROM catalogo.perfil_faceta f
                  JOIN activo a ON a.sujeto_id = f.sujeto_id
                 WHERE f.score > 0
            ),
            /* Las que tiene casi todo el mundo no distinguen a nadie. */
            generica AS (
                SELECT tipo_faceta, faceta
                  FROM faceta
                 GROUP BY tipo_faceta, faceta
                HAVING COUNT(*) > :topeSujetosPorFaceta
            ),
            util AS (
                SELECT f.*
                  FROM faceta f
                 WHERE NOT EXISTS (SELECT 1 FROM generica g
                                    WHERE g.tipo_faceta = f.tipo_faceta
                                      AND g.faceta = f.faceta)
            ),
            /* La norma se calcula sobre las MISMAS facetas que entran al cruce. */
            norma AS (
                SELECT sujeto_id, SQRT(SUM(score * score)) AS magnitud
                  FROM util GROUP BY sujeto_id
            ),
            par AS (
                SELECT a.sujeto_id AS sa, b.sujeto_id AS sb,
                       SUM(a.score * b.score) AS producto,
                       COUNT(*) AS compartidas
                  FROM util a
                  JOIN util b ON b.tipo_faceta = a.tipo_faceta
                             AND b.faceta = a.faceta
                             AND b.sujeto_id > a.sujeto_id
                 GROUP BY a.sujeto_id, b.sujeto_id
                HAVING COUNT(*) >= :minCompartidas
            ),
            calculado AS (
                SELECT par.sa, par.sb,
                       par.producto / (na.magnitud * nb.magnitud) AS score,
                       par.compartidas
                  FROM par
                  JOIN norma na ON na.sujeto_id = par.sa
                  JOIN norma nb ON nb.sujeto_id = par.sb
                 WHERE na.magnitud > 0 AND nb.magnitud > 0
            )
            INSERT INTO catalogo.sujeto_similitud
                   (sujeto_a, sujeto_b, score, soporte, ventana_dias, calculado_en)
            /*
             * Las dos direcciones. El cruce solo produce (a < b) para no contar
             * cada par dos veces; la tabla las guarda ambas para que «mis
             * vecinos» sea un recorrido de indice y no un OR.
             */
            SELECT sa, sb, LEAST(score, 1), compartidas,
                   CAST(:ventanaDias AS smallint), CAST(:ahora AS timestamptz) FROM calculado
             WHERE score > 0
            UNION ALL
            SELECT sb, sa, LEAST(score, 1), compartidas,
                   CAST(:ventanaDias AS smallint), CAST(:ahora AS timestamptz) FROM calculado
             WHERE score > 0
            ON CONFLICT (sujeto_a, sujeto_b) DO UPDATE SET
                score        = EXCLUDED.score,
                soporte      = EXCLUDED.soporte,
                ventana_dias = EXCLUDED.ventana_dias,
                calculado_en = EXCLUDED.calculado_en
            """, nativeQuery = true)
    int recalcular(@Param("desde") Instant desde,
            @Param("minCompartidas") int minCompartidas,
            @Param("topeSujetosPorFaceta") int topeSujetosPorFaceta,
            @Param("ventanaDias") int ventanaDias,
            @Param("ahora") Instant ahora);

    /** Lo que dejó de sostenerse deja de existir. Ver {@code ItemRelacionRepository}. */
    @Modifying
    @Query("DELETE FROM SujetoSimilitud s WHERE s.calculadoEn < :corte")
    int purgarObsoletas(@Param("corte") Instant corte);

    /** Los vecinos de un sujeto. Uso interno: NO existe endpoint que lo exponga. */
    @Query("""
            SELECT s FROM SujetoSimilitud s
             WHERE s.id.sujetoA = :sujeto
             ORDER BY s.score DESC
            """)
    List<SujetoSimilitud> vecinosDe(@Param("sujeto") UUID sujeto);
}
