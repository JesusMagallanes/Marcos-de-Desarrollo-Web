package com.backend.usuarios.colaborador;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.backend.usuarios.shared.seguridad.ContextoRls;
import com.backend.usuarios.shared.auditoria.AuditoriaService;
import com.backend.usuarios.shared.metricas.MetricasSeguridad;
import com.backend.usuarios.shared.auditoria.AuditoriaService.Evento;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Borra los documentos de identidad que ya no hacen falta.
 *
 * <p>Guardar fotos de DNI para siempre es acumular el peor tipo de dato posible
 * sin motivo: una vez resuelta la solicitud, la imagen ya cumplió su función y
 * lo único que aporta es superficie para una fuga.
 *
 * <p>Se borran los bytes pero <b>no la ficha</b>. Hay que poder demostrar que la
 * verificación se hizo, quién la hizo y cuándo, aunque la imagen ya no exista.
 *
 * <p>Dos plazos distintos porque son dos problemas distintos:
 *
 * <ul>
 *   <li><b>Huérfanos</b> (subidos y nunca enviados): a los pocos días. Son de
 *       alguien que empezó el formulario y lo dejó a medias; nadie los va a
 *       mirar y no tienen ninguna solicitud detrás que los justifique.
 *   <li><b>Resueltos</b>: bastante más tarde, porque durante un tiempo puede
 *       hacer falta revisar una decisión o atender una reclamación.
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PurgaDocumentos {

    private final DocumentoRepository documentos;
    private final AlmacenDocumentos almacen;
    private final AuditoriaService auditoria;
    private final MetricasSeguridad metricas;

    @Value("${smartzone.documentos.dias-huerfanos:7}")
    private int diasHuerfanos;

    @Value("${smartzone.documentos.dias-resueltas:90}")
    private int diasResueltas;

    /**
     * De madrugada, cuando no hay nadie usando la tienda. Borrar ficheros
     * mientras un administrador revisa la bandeja solo daría sustos.
     */
    @Scheduled(cron = "${smartzone.documentos.cron-purga:0 30 3 * * *}")
    public void purgar() {
        // Sin usuario detrás: con RLS activo y sin contexto, la política de
        // `documento_identidad` no dejaría ver ninguna fila y la purga no
        // borraría nada, diciendo cada noche que no había nada que borrar. Las
        // fotos de los DNI se quedarían ahí para siempre, que es justo lo que
        // esta clase existe para evitar.
        //
        // La marca va en este método y el trabajo en otro: si este fuera
        // @Transactional, Spring pediría la conexión —y fijaría el contexto—
        // antes de entrar en el cuerpo, y llegaría tarde.
        ContextoRls.comoSistema(this::borrarLoQueYaNoHaceFalta);
    }

    /**
     * A propósito sin {@code @Transactional} envolviéndolo todo.
     *
     * <p>Cada fila se marca en su propia transacción, la del {@code save}. Una
     * transacción única para las mil sería peor: el fichero se borra del disco
     * ANTES de marcar la fila, así que una vuelta atrás a mitad dejaría ficheros
     * ya borrados con su fila diciendo que siguen ahí. Eso se manifestaría
     * mucho después, como un 404 al abrir un documento que la ficha promete.
     *
     * <p>Yendo de uno en uno, lo que se ha hecho queda hecho y lo que falle se
     * reintenta la noche siguiente.
     */
    private void borrarLoQueYaNoHaceFalta() {
        Instant ahora = Instant.now();
        List<DocumentoIdentidad> purgables = documentos.buscarPurgables(
                ahora.minus(Duration.ofDays(diasHuerfanos)),
                ahora.minus(Duration.ofDays(diasResueltas)));

        if (purgables.isEmpty()) {
            return;
        }

        int borrados = 0;
        for (DocumentoIdentidad documento : purgables) {
            // Se marca aunque el fichero ya no estuviera: lo que interesa es que
            // deje de contarse como pendiente de purga. Si no, un fichero que
            // alguien borró a mano se reintentaría cada noche para siempre.
            almacen.borrar(documento.getRuta());
            documento.marcarPurgado();
            documentos.save(documento);
            metricas.documento("purgado");
            borrados++;
        }

        log.info("Purga de documentos de identidad: {} borrados", borrados);
        auditoria.registrar(Evento.DOCUMENTO_PURGADO, "sistema",
                "borrados=%d huerfanos=%dd resueltas=%dd"
                        .formatted(borrados, diasHuerfanos, diasResueltas));
    }
}
