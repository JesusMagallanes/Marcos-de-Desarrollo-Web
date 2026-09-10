package com.backend.catalogo.descubrimiento;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecomendacionServidaRepository extends JpaRepository<RecomendacionServida, Long> {

    /**
     * La agregación diaria, entera dentro de PostgreSQL.
     *
     * <p>Cruza tres cosas que viven en tablas distintas: lo que se decidió
     * servir, lo que el navegador confirmó que se vio, y lo que la persona hizo
     * DESPUÉS. Esa tercera parte es la que convierte un panel de vanidad en una
     * medida: un clic no es un éxito, y sin las acciones posteriores no se puede
     * distinguir un carrusel que engancha de uno que solo llama la atención.
     *
     * <p><b>La atribución es por ventana, no por identidad de evento.</b> Una
     * acción cuenta para una recomendación si ocurrió DESPUÉS de servirla y
     * dentro de {@code :horasAtribucion}. No es perfecto —alguien pudo llegar al
     * producto por el buscador— y por eso la ventana se configura: acortarla
     * atribuye de menos, alargarla atribuye de más, y qué se prefiere depende de
     * lo que se esté midiendo. Lo importante es que la regla sea explícita y la
     * misma para todos los módulos, para que la COMPARACIÓN entre ellos valga.
     *
     * <p>Cuenta también {@code sujetos} distintos: es el piso de privacidad del
     * agregado, porque una fila sostenida por una sola persona no describe un
     * patrón sino a esa persona.
     */
    @Modifying
    @Query(value = """
            WITH servida AS (
                SELECT r.*,
                       LEAST(r.posicion / 3, 7)::smallint AS banda
                  FROM catalogo.recomendacion_servida r
                 WHERE r.servido_en >= :desde AND r.servido_en < :hasta
            ),
            /* Lo que el navegador confirmo que entro en pantalla. */
            vista AS (
                SELECT s.id
                  FROM servida s
                  JOIN catalogo.impresion i
                    ON i.sujeto_id = s.sujeto_id
                   AND i.item_id = s.item_id
                   AND i.modulo = s.modulo
                   AND i.mostrado_en >= s.servido_en
                   AND i.mostrado_en < s.servido_en + make_interval(hours => :horasAtribucion)
                 GROUP BY s.id
            ),
            /* Lo que hizo despues, por tipo de accion. */
            accion AS (
                SELECT s.id,
                       BOOL_OR(e.tipo IN ('ITEM_VIEW', 'ITEM_VIEW_DEEP')) AS hubo_clic,
                       BOOL_OR(e.tipo = 'ITEM_VIEW_DEEP')                 AS hubo_profunda,
                       BOOL_OR(e.tipo = 'ADD_TO_CART')                    AS hubo_carrito,
                       BOOL_OR(e.tipo = 'PURCHASE')                       AS hubo_compra
                  FROM servida s
                  JOIN catalogo.evento_interaccion e
                    ON e.sujeto_id = s.sujeto_id
                   AND e.item_id = s.item_id
                   AND e.ocurrido_en >= s.servido_en
                   AND e.ocurrido_en < s.servido_en + make_interval(hours => :horasAtribucion)
                 GROUP BY s.id
            )
            INSERT INTO catalogo.metrica_descubrimiento
                   (dia, modulo, razon, ranker_version, banda_posicion, con_perfil,
                    servidas, vistas, clics, vistas_profundas, carritos, compras,
                    sujetos, calculado_en)
            SELECT CAST(:dia AS date), s.modulo, s.razon, s.ranker_version, s.banda, s.con_perfil,
                   COUNT(*),
                   COUNT(v.id),
                   COUNT(*) FILTER (WHERE a.hubo_clic),
                   COUNT(*) FILTER (WHERE a.hubo_profunda),
                   COUNT(*) FILTER (WHERE a.hubo_carrito),
                   COUNT(*) FILTER (WHERE a.hubo_compra),
                   COUNT(DISTINCT s.sujeto_id),
                   now()
              FROM servida s
              LEFT JOIN vista v ON v.id = s.id
              LEFT JOIN accion a ON a.id = s.id
             GROUP BY s.modulo, s.razon, s.ranker_version, s.banda, s.con_perfil
            ON CONFLICT (dia, modulo, razon, ranker_version, banda_posicion, con_perfil)
            DO UPDATE SET
                servidas         = EXCLUDED.servidas,
                vistas           = EXCLUDED.vistas,
                clics            = EXCLUDED.clics,
                vistas_profundas = EXCLUDED.vistas_profundas,
                carritos         = EXCLUDED.carritos,
                compras          = EXCLUDED.compras,
                sujetos          = EXCLUDED.sujetos,
                calculado_en     = EXCLUDED.calculado_en
            """, nativeQuery = true)
    int agregarDia(@Param("dia") LocalDate dia,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta,
            @Param("horasAtribucion") int horasAtribucion);

    /**
     * Borra el detalle antiguo.
     *
     * <p>El agregado sobrevive: es lo que permite comparar el recomendador de
     * hoy con el de hace un año sin conservar cada fila que lo produjo.
     */
    @Modifying
    @Query("DELETE FROM RecomendacionServida r WHERE r.servidoEn < :corte")
    int purgarAnterioresA(@Param("corte") Instant corte);

    /** Lo servido a un sujeto en una ventana. Para pruebas y para la evaluación. */
    @Query("""
            SELECT r FROM RecomendacionServida r
             WHERE r.sujetoId = :sujeto AND r.servidoEn >= :desde
             ORDER BY r.servidoEn DESC, r.posicion ASC
            """)
    List<RecomendacionServida> deSujetoDesde(@Param("sujeto") UUID sujeto,
            @Param("desde") Instant desde);
}
