package com.backend.catalogo.shared.ia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * El contador del tope de IA.
 *
 * <p>La operación es nativa y no JPQL porque tiene que ser UNA sola sentencia
 * atómica: leer-decidir-escribir desde Java dejaría que dos réplicas contaran la
 * misma ficha a la vez, que es exactamente lo que este contador existe para
 * impedir. El {@code ON CONFLICT} deja que Postgres serialice la fila y el
 * {@code RETURNING} devuelve el valor ya aplicado, sin un segundo viaje.
 *
 * <p>El reinicio del minuto va dentro del mismo UPSERT: si la fila es de un
 * minuto anterior, el contador vuelve a 1 en vez de sumar. Así no hace falta
 * borrar filas viejas ni una tarea programada que lo haga.
 *
 * <p>La transacción es {@code REQUIRES_NEW} y vive AQUÍ, no en quien llama: esto
 * tiene que quedar escrito aunque lo de alrededor se deshaga —las llamadas al
 * proveedor ya se pagaron— y, sobre todo, así un fallo de base marca como
 * «solo rollback» esta transacción y no la de quien llama, que es lo que le
 * permite seguir adelante con el contador en memoria.
 *
 * <p>El esquema va escrito en la consulta porque {@code hibernate.default_schema}
 * solo vale para el mapeo de entidades: una consulta nativa se manda tal cual.
 */
@Repository
public interface PresupuestoIaRepository extends JpaRepository<PresupuestoIaEntidad, String> {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = """
            INSERT INTO catalogo.ia_presupuesto (clave, minuto, usadas)
            VALUES (:clave, :minuto, 1)
            ON CONFLICT (clave) DO UPDATE SET
                usadas = CASE WHEN ia_presupuesto.minuto = :minuto THEN ia_presupuesto.usadas + 1 ELSE 1 END,
                minuto = :minuto
            RETURNING usadas
            """, nativeQuery = true)
    int anotarLlamada(@Param("clave") String clave, @Param("minuto") long minuto);
}
