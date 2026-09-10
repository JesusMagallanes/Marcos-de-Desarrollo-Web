package com.backend.catalogo.descubrimiento.adaptativo;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.CRC32;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Qué variante le toca a cada sujeto.
 *
 * <h4>Sin tabla</h4>
 *
 * <p>La asignación se CALCULA, no se guarda. Es la decisión de diseño más
 * importante de esta parte y merece la pena explicarla, porque lo natural sería
 * crear una tabla {@code asignacion_experimento} y no hace ninguna falta.
 *
 * <p>Una función determinista del identificador del sujeto y del nombre del
 * experimento devuelve siempre lo mismo para el mismo par. Eso da estabilidad
 * —lo que pide un experimento— sin una tabla que mantener, sin una consulta por
 * petición, sin un dato más que borrar cuando alguien ejerce su derecho al
 * olvido, y sin una fila que relacione a una persona con un tratamiento. Menos
 * código, menos latencia y menos superficie de privacidad, todo a la vez.
 *
 * <p>Y trae de regalo el comportamiento correcto en el ciclo de vida del
 * sujeto: al cerrar sesión el navegador suelta su identificador y estrena otro,
 * así que el siguiente visitante recibe la variante que le corresponda a él y no
 * la heredada. No hay que acordarse de limpiar nada, porque no hay nada que
 * limpiar.
 *
 * <h4>Por qué CRC32 y no un aleatorio</h4>
 *
 * <p>Un {@code random} por petición rompería el experimento: la misma persona
 * vería una mezcla de las dos variantes y ninguna medida significaría nada. Se
 * necesita un reparto estable y uniforme, no imprevisible — no hay nada que
 * proteger aquí, así que una función criptográfica seria complejidad sin
 * motivo.
 *
 * <p>El nombre del experimento entra en el hash para que el siguiente reparta
 * distinto. Sin eso, los mismos sujetos caerían siempre del mismo lado y un
 * sesgo cualquiera de ese grupo contaminaría todos los experimentos seguidos.
 */
@Component
@RequiredArgsConstructor
public class AsignacionExperimento {

    private final PesosAdaptativos pesos;

    /** Los dos brazos. Dos, y no veinte: con veinte no hay muestra para ninguno. */
    public enum Variante {
        /** La calibración que ya estaba sirviendo. */
        CONTROL,
        /** La que se está probando. */
        VARIANTE
    }

    /**
     * La variante de este sujeto, siempre la misma.
     *
     * @param sujeto puede ser {@code null} —una ficha servida a quien no trae
     *     identificador—; entonces se sirve el control, que es lo conservador:
     *     sin sujeto no hay a quién atribuir el resultado, así que meterlo en el
     *     experimento solo ensuciaría la medida
     */
    public Variante de(UUID sujeto) {
        if (sujeto == null || pesos.getPorcentajeVariante() <= 0) {
            return Variante.CONTROL;
        }
        if (pesos.getPorcentajeVariante() >= 100) {
            return Variante.VARIANTE;
        }
        return cubo(sujeto) < pesos.getPorcentajeVariante()
                ? Variante.VARIANTE
                : Variante.CONTROL;
    }

    /** El cubo del 0 al 99 en que cae este sujeto para este experimento. */
    int cubo(UUID sujeto) {
        CRC32 crc = new CRC32();
        crc.update((sujeto + "|" + pesos.getExperimento()).getBytes(StandardCharsets.UTF_8));
        return (int) (crc.getValue() % 100);
    }
}
