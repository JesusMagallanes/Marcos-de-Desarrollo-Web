package com.backend.catalogo.descubrimiento;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImpresionRepository extends JpaRepository<Impresion, Long> {

    /**
     * Qué se le ha enseñado a alguien, dónde, cuántas veces y si lo tocó.
     *
     * <h4>Una consulta para toda la pantalla</h4>
     *
     * <p>Sustituye a la que devolvía «los ítems que ya cansan». Aquella
     * resolvía una pregunta de sí o no y ya venía decidida de la base, de modo
     * que graduar el castigo era imposible sin volver a preguntar. Esta
     * devuelve el dato en crudo —conteos por módulo— y deja la decisión en
     * {@link CooldownService}, que es donde se puede leer, probar y calibrar.
     *
     * <p>Es UNA consulta por petición y no una por módulo ni, mucho menos, una
     * por candidato: trae de golpe todas las filas del sujeto dentro de la
     * ventana, y con ellas se resuelven los seis carruseles del Home. Agrupada
     * en la base, así que lo que viaja son unas pocas decenas de filas y no el
     * historial de impresiones.
     *
     * <h4>El clic perdona el ítem entero, no solo su módulo</h4>
     *
     * <p>La cuarta columna suma los clics del ítem en TODOS sus módulos, con
     * una ventana sobre la partición. Es a propósito y refleja cómo se marca un
     * clic hoy: {@code marcarClic} cierra la impresión más reciente sin clic del
     * ítem, sin mirar en qué carrusel salió —el evento de entrada trae un
     * {@code origen} de texto libre, no el módulo—. Inventarse aquí una
     * atribución por módulo que la ingesta no garantiza habría sido construir
     * sobre un dato que no existe.
     *
     * <p>Y además es lo que se quiere: si alguien tocó ese producto, mostrárselo
     * otra vez no es insistir, es acertar.
     *
     * @param ahora corta el futuro. Las fechas las pone el servidor, pero una
     *     fila con fecha adelantada —una importación, un reloj torcido— no
     *     puede fabricar un enfriamiento que nadie se ha ganado
     * @return filas {@code [itemId, modulo, veces, clicsDelItem]}
     */
    @Query(value = """
            SELECT i.item_id,
                   i.modulo,
                   COUNT(*) AS veces,
                   SUM(COUNT(*) FILTER (WHERE i.con_clic))
                       OVER (PARTITION BY i.item_id) AS tocado
              FROM catalogo.impresion i
             WHERE i.sujeto_id = :sujeto
               AND i.item_tipo = :itemTipo
               AND i.mostrado_en >= :desde
               AND i.mostrado_en < :ahora
             GROUP BY i.item_id, i.modulo
            """, nativeQuery = true)
    List<Object[]> exposicionPorModulo(@Param("sujeto") UUID sujeto,
            @Param("itemTipo") String itemTipo, @Param("desde") Instant desde,
            @Param("ahora") Instant ahora);

    /**
     * Marca como clicada la impresión más reciente de ese ítem.
     *
     * <p>Sin esto el CTR por módulo no se puede calcular: habría impresiones y
     * clics en tablas distintas sin forma de casarlos.
     */
    @Modifying
    @Query(value = """
            UPDATE catalogo.impresion SET con_clic = TRUE
             WHERE id = (SELECT i.id FROM catalogo.impresion i
                          WHERE i.sujeto_id = :sujeto AND i.item_id = :itemId
                            AND i.con_clic = FALSE
                          ORDER BY i.mostrado_en DESC LIMIT 1)
            """, nativeQuery = true)
    int marcarClic(@Param("sujeto") UUID sujeto, @Param("itemId") Long itemId);

    @Modifying
    @Query("DELETE FROM Impresion i WHERE i.mostradoEn < :limite")
    int purgarAnterioresA(@Param("limite") Instant limite);

    /**
     * Cuántas veces se ha enseñado cada uno de estos ítems, a quien sea.
     *
     * <p>Es la medida de popularidad EXPUESTA, que no es la misma que la de
     * popularidad real y es justamente la que hay que castigar: un producto se
     * ve mucho porque el sistema lo enseña mucho, y si eso lo hace subir en el
     * ranking, el sistema se está retroalimentando a sí mismo. Ver
     * {@code PesosDescubrimiento.factorPopularidad}.
     *
     * <p>Sobre la lista de candidatos ya recortada, no sobre el catálogo: son
     * unas decenas de identificadores y un recorrido de índice.
     *
     * <h4>El piso de sujetos distintos</h4>
     *
     * <p>Un producto solo cuenta como expuesto si lo han visto varias personas
     * DISTINTAS. Sin ese corte, la cuenta era un {@code COUNT(*)} y quien
     * quisiera podía inflarla: como el freno se aplica al ranking de todo el
     * mundo, bastaba con declarar impresiones de un producto rival para
     * hundirlo para todos los visitantes. Es el mismo piso que ya protege
     * tendencias, y por la misma razón: un agregado sostenido por una sola
     * persona no describe un patrón, describe a esa persona.
     *
     * <p>Cambia el ranking en catálogos con poco tráfico —un producto por
     * debajo del piso deja de tener freno— y es un precio aceptado a cambio de
     * que nadie pueda mover el ranking de otros desde su navegador.
     *
     * @return filas {@code [itemId, veces]}; solo los que superan el piso
     */
    @Query(value = """
            SELECT i.item_id, COUNT(*)
              FROM catalogo.impresion i
             WHERE i.item_tipo = 'PRODUCTO'
               AND i.item_id IN (:items)
               AND i.mostrado_en >= :desde
             GROUP BY i.item_id
            HAVING COUNT(DISTINCT i.sujeto_id) >= :minimoSujetos
            """, nativeQuery = true)
    List<Object[]> contarPorItem(@Param("items") List<Long> items, @Param("desde") Instant desde,
            @Param("minimoSujetos") int minimoSujetos);
}
