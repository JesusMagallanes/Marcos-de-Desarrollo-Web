package com.backend.catalogo.descubrimiento;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MetricaDescubrimientoRepository
        extends JpaRepository<MetricaDescubrimiento, MetricaDescubrimiento.Id> {

    /** Todo lo de un rango, sin agrupar. Para el informe y para las pruebas. */
    @Query("""
            SELECT m FROM MetricaDescubrimiento m
             WHERE m.id.dia >= :desde AND m.id.dia <= :hasta
             ORDER BY m.id.dia DESC, m.id.modulo, m.id.razon, m.id.bandaPosicion
            """)
    List<MetricaDescubrimiento> entre(@Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /**
     * El resumen por razón, que es la pregunta que se hace primero.
     *
     * <p>Se exige un mínimo de sujetos por fila. No es puntillosidad
     * estadística: con cuatro personas detrás, un CTR del 50 % significa que dos
     * hicieron clic, y tomar decisiones de producto con eso es peor que no
     * medir, porque el número da una confianza que no existe. Y es además el
     * piso de privacidad del agregado.
     *
     * @return filas {@code [razon, servidas, vistas, clics, profundas, carritos,
     *     compras, sujetos]}
     */
    @Query(value = """
            SELECT m.razon,
                   SUM(m.servidas), SUM(m.vistas), SUM(m.clics),
                   SUM(m.vistas_profundas), SUM(m.carritos), SUM(m.compras),
                   SUM(m.sujetos)
              FROM catalogo.metrica_descubrimiento m
             WHERE m.dia >= :desde AND m.dia <= :hasta
             GROUP BY m.razon
            HAVING SUM(m.sujetos) >= :minimoSujetos
             ORDER BY 2 DESC
            """, nativeQuery = true)
    List<Object[]> resumenPorRazon(@Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            @Param("minimoSujetos") int minimoSujetos);

    /**
     * La caída por posición, que hay que conocer antes de comparar nada.
     *
     * <p>Una tarjeta arriba recibe más clics simplemente por estar arriba.
     * Comparar la razón que ocupa las primeras posiciones con la que ocupa las
     * últimas, sin mirar esto, es medirse el propio sesgo y llamarlo calidad.
     *
     * @return filas {@code [banda, vistas, clics]}
     */
    @Query(value = """
            SELECT m.banda_posicion, SUM(m.vistas), SUM(m.clics)
              FROM catalogo.metrica_descubrimiento m
             WHERE m.dia >= :desde AND m.dia <= :hasta
             GROUP BY m.banda_posicion
             ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> caidaPorPosicion(@Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /** Qué versiones de ranking sirvieron tráfico. Sin esto no se compara nada. */
    @Query(value = """
            SELECT m.ranker_version, SUM(m.servidas), SUM(m.vistas), SUM(m.clics)
              FROM catalogo.metrica_descubrimiento m
             WHERE m.dia >= :desde AND m.dia <= :hasta
             GROUP BY m.ranker_version
             ORDER BY 2 DESC
            """, nativeQuery = true)
    List<Object[]> porVersionDeRanker(@Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);
}
