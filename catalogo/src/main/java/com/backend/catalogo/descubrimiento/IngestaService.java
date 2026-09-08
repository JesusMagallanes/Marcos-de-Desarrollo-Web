package com.backend.catalogo.descubrimiento;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** Guarda lo que se mostró. El clic, si llega, lo marca después un evento. */
    @Transactional
    public int registrarImpresiones(UUID sujeto, List<ImpresionRequest> lote) {
        Instant ahora = Instant.now();
        List<Impresion> filas = lote.stream()
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
        return filas.size();
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
