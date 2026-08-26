package com.backend.catalogo.sincronizacion;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.backend.catalogo.sincronizacion.dto.SincronizacionDtos.PeticionSyncValoracion;
import com.backend.catalogo.sincronizacion.dto.SincronizacionDtos.RespuestaSyncValoracion;
import com.backend.catalogo.sincronizacion.dto.SincronizacionDtos.TipoOperacionValoracion;
import com.backend.catalogo.valoracion.ValoracionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Aplica las operaciones que el cliente escribió primero en local.
 *
 * <h2>Por qué la deduplicación vive aquí y no en el cliente</h2>
 *
 * <p>El cliente reenvía su cola hasta recibir confirmación. Puede perder la
 * respuesta de éxito con la red caída, reiniciarse, o duplicar el envío al
 * reconectar dos veces: desde fuera del servidor, "no llegó respuesta" y "no
 * llegó" son indistinguibles. El único punto donde se puede distinguir de
 * verdad es la base de datos, y por eso la constraint UNIQUE de
 * {@code operaciones_aplicadas} es el mecanismo real: la inserción del registro
 * va DENTRO de la misma transacción que el efecto, así que o los dos quedan o
 * ninguno.
 *
 * <h2>La carrera entre dos reenvíos del mismo id</h2>
 *
 * <p>Se consulta {@code existsById} antes de aplicar para responder rápido a
 * los reenvíos comunes (el caso masivo). Pero dos peticiones idénticas pueden
 * cruzarse y pasar AMBAS esa consulta: entonces la segunda choca contra la PK
 * al insertar, y su transacción entera —efecto incluido— se deshace. El cliente
 * recibe un 5xx puntual, reintenta, y esta vez la consulta inicial lo detecta:
 * exactamente-una vez aplicado, sin ventanas abiertas.
 *
 * <h2>Conflictos</h2>
 *
 * <p>No hay versiones ni merges: una valoración tiene UN escritor posible (su
 * dueño) y el cliente serializa su propia cola en orden FIFO. Si escribió dos
 * veces sin conexión, llegan las dos en orden y la última gana; si mientras
 * tanto el servidor vio otra edición, también la tapa la última de la cola.
 * Tras sincronizar, el cliente revalida sus cachés de lectura: el estado del
 * servidor manda siempre como fuente de verdad.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SincronizacionService {

    /** Después de este tiempo un reenvío ya no llega: se purga el registro. */
    private static final int DIAS_RETENCION = 7;

    private final OperacionAplicadaRepository operaciones;
    private final ValoracionService valoraciones;

    /**
     * Aplica la operación, o responde "ya estaba" si es un reenvío.
     *
     * <p>{@code token} viaja a través de {@link ValoracionService#guardar},
     * que lo usa para preguntarle a compras si quien envía compró el producto:
     * offline no se puede comprobar eso, así que la regla se aplica igual que
     * en línea, al momento de la sincronización.
     */
    @Transactional
    public RespuestaSyncValoracion aplicarValoracion(Long usuarioId,
            PeticionSyncValoracion peticion, String token) {

        if (operaciones.existsById(peticion.operacionId())) {
            log.debug("Operación {} ya aplicada: reenvío reconocido", peticion.operacionId());
            return RespuestaSyncValoracion.yaAplicada();
        }

        Long valoracionId = switch (peticion.tipo()) {
            case GUARDAR -> {
                if (peticion.valoracion() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "GUARDAR requiere los datos de la valoración");
                }
                yield valoraciones.guardar(peticion.productoId(), usuarioId,
                        peticion.valoracion(), token).id();
            }
            case ELIMINAR -> {
                valoraciones.eliminarSiExiste(peticion.productoId(), usuarioId);
                yield null;
            }
        };

        // Misma transacción que el efecto de arriba: la constraint UNIQUE de
        // esta fila es lo que cierra la carrera descrita en la cabecera.
        operaciones.save(new OperacionAplicada(
                peticion.operacionId(), usuarioId, peticion.tipo().name(), Instant.now()));

        return RespuestaSyncValoracion.nueva(valoracionId);
    }

    /** Libera los registros cuyo plazo de utilidad venció. Cada 6 horas. */
    @Scheduled(fixedDelay = 6 * 60 * 60 * 1000)
    @Transactional
    public void purgar() {
        int eliminadas = operaciones.eliminarAnterioresA(
                Instant.now().minus(DIAS_RETENCION, ChronoUnit.DAYS));
        if (eliminadas > 0) {
            log.info("Purga de operaciones sincronizadas: {} registros", eliminadas);
        }
    }
}
