package com.backend.catalogo.descubrimiento;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.EventoRequest;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.ImpresionRequest;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.LoteEventosRequest;

import lombok.RequiredArgsConstructor;

/**
 * Recibe lo que pasó y lo convierte en dato.
 *
 * <p>Hace tres cosas por cada evento: lo guarda crudo, actualiza el perfil y,
 * si toca, escribe el descarte. El orden importa: primero el log —que es la
 * verdad y de donde se podrá recalcular todo si la ponderación cambia— y
 * después los agregados.
 */
@Service
@RequiredArgsConstructor
public class IngestaService {

    private final EventoInteraccionRepository eventos;
    private final ImpresionRepository impresiones;
    private final ItemDescartadoRepository descartes;
    private final RecomendacionServidaRepository servidas;
    private final MetricasDescubrimiento metricas;
    private final PesosDescubrimiento pesos;
    private final PerfilService perfiles;

    /**
     * Registra un lote y actualiza el perfil.
     *
     * @return cuántos eventos se guardaron
     */
    @Transactional
    public int registrar(UUID sujeto, LoteEventosRequest lote) {
        int guardados = 0;
        for (EventoRequest e : lote.eventos()) {
            registrarUno(sujeto, lote.sesionId(), lote.ubigeo(), e);
            guardados++;
        }
        return guardados;
    }

    private void registrarUno(UUID sujeto, UUID sesion, String ubigeo, EventoRequest e) {
        eventos.save(EventoInteraccion.builder()
                .sujetoId(sujeto)
                .sesionId(sesion)
                .tipo(e.tipo())
                .itemTipo(e.itemTipo())
                .itemId(e.itemId())
                .categoriaId(e.categoriaId())
                .ubigeo(ubigeo)
                .dwellMs(e.dwellMs())
                .origen(e.origen())
                .posicion(e.posicion())
                .metadata(metadataDe(e))
                .ocurridoEn(Instant.now())
                .build());

        /*
         * Un descarte no solo resta en el perfil: retira el ítem de futuras
         * recomendaciones. Sin este filtro duro, «no me interesa» solo bajaría
         * un score y el producto volvería a aparecer en cuanto subiera algo
         * más, que es exactamente lo que hace que la gente deje de usarlo.
         */
        if (e.tipo().esDescarte() && e.itemId() != null && e.itemTipo() != null) {
            descartes.save(ItemDescartado.builder()
                    .id(new ItemDescartado.Id(sujeto, e.itemTipo(), e.itemId()))
                    .motivo(e.tipo().name())
                    .creadoEn(Instant.now())
                    .build());
        }

        // Un clic cierra el círculo de la impresión que lo provocó: sin esto no
        // hay CTR por módulo y no se puede saber qué carrusel funciona.
        if (e.itemId() != null && !e.tipo().esDescarte() && e.origen() != null) {
            impresiones.marcarClic(sujeto, e.itemId());
        }

        if (e.tipo() == TipoEvento.ATTRIBUTE_FILTER) {
            perfiles.aplicarFiltro(sujeto, e.atributoCodigo(), e.atributoValor());
            return;
        }

        long repeticion = e.itemId() == null ? 1
                : eventos.contarRepeticiones(sujeto, sesion, e.tipo(), e.itemId());

        perfiles.aplicar(sujeto, e.tipo(), e.itemId(), e.categoriaId(), e.dwellMs(),
                (int) Math.max(1, repeticion));
    }

    /** Cuántas impresiones se aceptaron y cuántas no pudieron sostenerse. */
    public record Resultado(int aceptadas, int descartadas) {
    }

    /**
     * Guarda lo que se mostró, si se le mostró de verdad.
     *
     * <h4>Por qué ya no se cree al cliente</h4>
     *
     * <p>Una impresión la declara el navegador, y hasta aquí se aceptaba tal
     * cual: decir «he visto el producto 7 en POPULARES» bastaba para que se
     * guardara. Con eso se podía fabricar exposición, y la exposición frena el
     * ranking de TODO SmartZone, no solo el de quien la declara. Un vendedor
     * podía hundir a un rival desde su propio navegador.
     *
     * <p>Ahora cada impresión tiene que corresponder a algo que el backend
     * decidió servirle a ESE sujeto en ESE módulo. La prueba ya existía en
     * {@code recomendacion_servida} y no hizo falta ninguna tabla nueva.
     *
     * <h4>La ventana</h4>
     *
     * <p>Se mira lo servido dentro de la retención del detalle, que es lo que
     * de verdad hay. Más estrecha dejaría fuera a quien tiene una pestaña
     * abierta desde ayer; no hay ninguna razón para castigar eso.
     *
     * <h4>Qué pasa con lo descartado</h4>
     *
     * <p>No se guarda y no se rompe la petición. El cliente legítimo nunca lo
     * ve —lo que pinta es lo que le sirvieron— y devolver un error convertiría
     * un desajuste inocente, como una pestaña con un Home purgado, en una
     * pantalla rota. El número sale en la respuesta y en un contador agregado.
     */
    @Transactional
    public Resultado registrarImpresiones(UUID sujeto, List<ImpresionRequest> lote) {
        Instant ahora = Instant.now();
        List<Long> items = lote.stream().map(ImpresionRequest::itemId).distinct().toList();

        Set<String> servidos = new HashSet<>(servidas.servidosDe(sujeto, items,
                ahora.minus(Duration.ofDays(pesos.getRetencionDias()))));

        List<Impresion> filas = lote.stream()
                .filter(i -> servidos.contains(i.modulo() + "|" + i.itemId()))
                .map(i -> Impresion.builder()
                        .sujetoId(sujeto)
                        .itemTipo(i.itemTipo())
                        .itemId(i.itemId())
                        .modulo(i.modulo())
                        .posicion(i.posicion())
                        .conClic(false)
                        .mostradoEn(ahora)
                        .build())
                .toList();

        impresiones.saveAll(filas);

        int descartadas = lote.size() - filas.size();
        if (descartadas > 0) {
            /*
             * Agregado y sin identificadores: cuantas, no de quien ni de que.
             * Un valor sostenido aqui es la senal de que alguien esta probando.
             */
            metricas.impresionesDescartadas(descartadas);
        }
        return new Resultado(filas.size(), descartadas);
    }

    /**
     * Lo específico del evento, como JSON.
     *
     * <p>Va en `jsonb` y no en columnas porque cada tipo de evento lleva cosas
     * distintas, y añadir una columna por cada una dejaría una tabla con veinte
     * campos nulos en casi todas las filas.
     */
    private String metadataDe(EventoRequest e) {
        if (e.termino() == null && e.atributoCodigo() == null) {
            return null;
        }
        StringBuilder json = new StringBuilder("{");
        if (e.termino() != null) {
            json.append("\"termino\":\"").append(escapar(e.termino())).append("\"");
        }
        if (e.atributoCodigo() != null) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append("\"atributo\":\"").append(escapar(e.atributoCodigo())).append("\"");
            if (e.atributoValor() != null) {
                json.append(",\"valor\":\"").append(escapar(e.atributoValor())).append("\"");
            }
        }
        return json.append('}').toString();
    }

    private String escapar(String valor) {
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
