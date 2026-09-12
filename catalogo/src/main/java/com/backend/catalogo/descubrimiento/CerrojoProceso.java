package com.backend.catalogo.descubrimiento;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Que un proceso programado no se pise a sí mismo.
 *
 * <h4>El problema</h4>
 *
 * <p>Con una sola instancia no existe: {@code fixedDelay} espera a que termine
 * la ejecución anterior. Con dos instancias sí, y el colaborativo lo sufre de
 * verdad: dos pasadas que se solapan pueden dejar una relación reescrita con la
 * marca de tiempo más antigua y purgada por la más nueva. Se autocura en la
 * pasada siguiente, pero es un agujero.
 *
 * <h4>La solución, que ya estaba en PostgreSQL</h4>
 *
 * <p>Un cerrojo consultivo ligado a la transacción:
 * {@code pg_try_advisory_xact_lock}. Se intenta sin esperar; si otra
 * transacción lo tiene, se devuelve {@code false} y el proceso se salta esa
 * pasada. Se libera solo al confirmar o deshacer, así que no hay forma de
 * dejarlo colgado aunque el proceso reviente a medias. Ni tabla, ni Redis, ni
 * ShedLock: una función que la base ya tenía.
 *
 * <h4>Por proceso, no global</h4>
 *
 * <p>La clave es el nombre del proceso, así que dos colaborativos no corren a
 * la vez pero un colaborativo no estorba a las tendencias. Bloquear todo el
 * mantenimiento con una sola clave habría serializado trabajos que no comparten
 * ni una tabla.
 */
@Component
public class CerrojoProceso {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Intenta tomar el cerrojo de este proceso para la transacción en curso.
     *
     * <p>Exige una transacción activa, y lanza si no la hay. Un cerrojo de
     * transacción tomado fuera de una transacción se soltaría al instante y
     * el llamante creería estar protegido sin estarlo: es mejor que falle
     * ruidosamente a que mienta.
     *
     * @param proceso nombre estable del proceso; es la clave del cerrojo
     * @return {@code true} si esta transacción lo tiene y puede trabajar
     */
    public boolean intentar(String proceso) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "El cerrojo de '" + proceso + "' solo tiene sentido dentro de una transacción");
        }
        /*
         * `hashtext` convierte el nombre en la clave entera que pide la
         * funcion, de forma estable entre instancias. Va con prefijo para no
         * chocar con ningun otro uso de cerrojos consultivos en la misma base.
         */
        Object resultado = entityManager
                .createNativeQuery("SELECT pg_try_advisory_xact_lock(hashtext(:clave))")
                .setParameter("clave", "smartzone:descubrimiento:" + proceso)
                .getSingleResult();
        return Boolean.TRUE.equals(resultado);
    }
}
