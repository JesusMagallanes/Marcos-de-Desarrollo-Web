package com.backend.catalogo.descubrimiento;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.backend.catalogo.producto.Producto;

/**
 * La pregunta de elegibilidad, y solo esa.
 *
 * <p>Repositorio propio en vez de un método más en {@code ProductoRepository}
 * porque la condición no es «qué es un producto» sino «qué puede recomendarse»,
 * y son cosas que cambian por motivos distintos. Aquí vive junto al resto de
 * descubrimiento, que es quien la necesita y quien la va a modificar cuando
 * aparezca una regla comercial nueva.
 */
public interface ProductoElegibleRepository extends JpaRepository<Producto, Long> {

    /**
     * De los que se le pasan, cuáles pueden mostrarse ahora mismo.
     *
     * <p>Los dos criterios son exactamente los que ya aplican los siete
     * generadores en su SQL. Están escritos aquí otra vez a propósito: el día
     * que se añada una regla comercial —un producto retirado, una categoría
     * bloqueada— este es el sitio donde tiene que entrar, y tenerlo en un solo
     * método hace que no haya que acordarse de siete.
     *
     * <p>Una sola consulta para todo el lote. Ver el N+1 que evita en
     * {@link ElegibilidadService}.
     */
    @Query(value = """
            SELECT p.id
              FROM catalogo.producto p
             WHERE p.id IN (:ids)
               AND p.estado_moderacion = 'APROBADO'
               AND p.stock > 0
            """, nativeQuery = true)
    List<Long> elegiblesDe(@Param("ids") List<Long> ids);
}
