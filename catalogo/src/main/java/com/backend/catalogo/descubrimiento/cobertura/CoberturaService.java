package com.backend.catalogo.descubrimiento.cobertura;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.descubrimiento.cobertura.InformeCobertura.Banda;
import com.backend.catalogo.descubrimiento.cobertura.InformeCobertura.Cobertura;
import com.backend.catalogo.descubrimiento.cobertura.InformeCobertura.Concentracion;
import com.backend.catalogo.descubrimiento.cobertura.InformeCobertura.Dimension;
import com.backend.catalogo.descubrimiento.cobertura.InformeCobertura.Faceta;
import com.backend.catalogo.descubrimiento.cobertura.InformeCobertura.Fuente;

import lombok.RequiredArgsConstructor;

/**
 * Responde qué parte del catálogo existe para el recomendador.
 *
 * <h4>La pregunta que faltaba</h4>
 *
 * <p>Hasta aquí se podía saber si el recomendador acierta —CTR, NDCG, compras— y
 * si sus listas son variadas. No se podía saber si hay doscientas categorías a
 * las que no manda a nadie nunca. Son preguntas independientes: un sistema puede
 * acertar mucho con el diez por ciento del catálogo y dejar el resto a oscuras,
 * y desde dentro se ve perfecto.
 *
 * <h4>Tres consultas, y por qué tres</h4>
 *
 * <ol>
 *   <li><b>Por categoría.</b> Da la cobertura de categorías, las que tienen
 *       cero, la concentración y —porque baja a nivel de producto por dentro— la
 *       concentración interna. Y da además la cobertura de PRODUCTOS, porque
 *       {@code categoria_id} es obligatoria y cada producto cuenta una vez.</li>
 *   <li><b>Por marca.</b> Aparte porque {@code marca_id} admite nulo: no todos
 *       los productos elegibles tienen marca, así que esta consulta no puede
 *       servir de base para el total.</li>
 *   <li><b>Por banda de posición.</b> Aparte porque es otra agrupación, no otro
 *       recorte: meter la banda en la primera multiplicaría sus filas por ocho
 *       para responder una pregunta distinta.</li>
 * </ol>
 *
 * <p>Cada una devuelve las dos fuentes de una vez. Ninguna se ejecuta por
 * categoría, por marca ni por producto: eso sería el N+1 que estas fases tienen
 * prohibido, y además haría el coste proporcional al tamaño del catálogo justo
 * en la medición que existe para vigilarlo.
 *
 * <h4>Qué no sale de aquí</h4>
 *
 * <p>Ningún identificador de persona. Las consultas no leen {@code sujeto_id} ni
 * lo devuelven, y nada de esto se persiste: es un cálculo sobre el detalle vivo,
 * no telemetría. Lo más fino que se mira es el producto, y solo como un máximo
 * dentro de su categoría —«el más expuesto se lleva el 90 %»— sin que salga
 * cuál era.
 */
@Service
@RequiredArgsConstructor
public class CoberturaService {

    private final CoberturaRepository repositorio;

    /**
     * Mide un periodo cerrado.
     *
     * @param desde inicio, inclusive
     * @param hasta fin, EXCLUSIVE. Cerrado por arriba a propósito: dos periodos
     *     consecutivos no pueden contar dos veces la misma exposición, y una
     *     exposición con fecha futura no entra en el periodo de hoy
     * @param modulo un carrusel concreto, o {@code null} para todos
     */
    @Transactional(readOnly = true)
    public InformeCobertura medir(Instant desde, Instant hasta, String modulo) {
        if (desde == null || hasta == null || !desde.isBefore(hasta)) {
            throw new IllegalArgumentException("El periodo tiene que empezar antes de acabar");
        }
        Map<Fuente, List<Faceta>> categorias = facetas(
                repositorio.porCategoria(desde, hasta, modulo));
        Map<Fuente, List<Faceta>> marcas = facetas(repositorio.porMarca(desde, hasta, modulo));
        Map<Fuente, List<Banda>> bandas = bandas(repositorio.porBanda(desde, hasta, modulo));

        return new InformeCobertura(desde, hasta, modulo,
                componer(Fuente.SERVIDO, categorias, marcas, bandas),
                componer(Fuente.VISTO, categorias, marcas, bandas));
    }

    private Cobertura componer(Fuente fuente, Map<Fuente, List<Faceta>> categorias,
            Map<Fuente, List<Faceta>> marcas, Map<Fuente, List<Banda>> bandas) {

        List<Faceta> deCategoria = categorias.getOrDefault(fuente, List.of());
        List<Faceta> deMarca = marcas.getOrDefault(fuente, List.of());

        return new Cobertura(fuente,
                /*
                 * Los productos se cuentan desde las categorias y NO desde las
                 * marcas. `categoria_id` es obligatoria, asi que cada producto
                 * elegible aparece en exactamente una fila; `marca_id` admite
                 * nulo y los productos sin marca no estarian en ninguna.
                 */
                dimensionDeProductos(deCategoria),
                dimensionDeFacetas(deCategoria),
                dimensionDeFacetas(deMarca),
                deCategoria, deMarca,
                concentracion(deCategoria),
                bandas.getOrDefault(fuente, List.of()));
    }

    /** El universo de productos, sumando el catálogo de cada categoría. */
    private Dimension dimensionDeProductos(List<Faceta> facetas) {
        long elegibles = facetas.stream().mapToLong(Faceta::productosElegibles).sum();
        long expuestos = facetas.stream().mapToLong(Faceta::productosExpuestos).sum();
        return new Dimension(elegibles, expuestos);
    }

    /** El universo de categorías o marcas: las que tienen catálogo vivo. */
    private Dimension dimensionDeFacetas(List<Faceta> facetas) {
        long conExposicion = facetas.stream().filter(f -> !f.sinExposicion()).count();
        return new Dimension(facetas.size(), conExposicion);
    }

    /**
     * Cuánto se llevan la primera y las cinco primeras.
     *
     * <p>Las filas llegan ya ordenadas por exposición descendente desde el SQL,
     * que es donde tiene que hacerse ese trabajo.
     */
    private Concentracion concentracion(List<Faceta> facetas) {
        long total = facetas.stream().mapToLong(Faceta::exposiciones).sum();
        if (total == 0) {
            return Concentracion.vacia();
        }
        long primera = facetas.get(0).exposiciones();
        long cinco = facetas.stream().limit(5).mapToLong(Faceta::exposiciones).sum();

        return new Concentracion(total, (double) primera / total, (double) cinco / total);
    }

    private Map<Fuente, List<Faceta>> facetas(List<Object[]> filas) {
        Map<Fuente, List<Faceta>> porFuente = new EnumMap<>(Fuente.class);
        for (Object[] f : filas) {
            porFuente.computeIfAbsent(Fuente.valueOf(String.valueOf(f[0])),
                    k -> new ArrayList<>())
                    .add(new Faceta(
                            ((Number) f[1]).longValue(),
                            String.valueOf(f[2]),
                            ((Number) f[3]).longValue(),
                            ((Number) f[4]).longValue(),
                            ((Number) f[5]).longValue(),
                            ((Number) f[6]).longValue()));
        }
        return porFuente;
    }

    private Map<Fuente, List<Banda>> bandas(List<Object[]> filas) {
        Map<Fuente, List<Banda>> porFuente = new EnumMap<>(Fuente.class);
        for (Object[] f : filas) {
            long exposiciones = ((Number) f[2]).longValue();
            long topCategoria = ((Number) f[5]).longValue();

            porFuente.computeIfAbsent(Fuente.valueOf(String.valueOf(f[0])),
                    k -> new ArrayList<>())
                    .add(new Banda(
                            ((Number) f[1]).shortValue(),
                            exposiciones,
                            ((Number) f[3]).longValue(),
                            ((Number) f[4]).longValue(),
                            exposiciones == 0 ? 0.0 : (double) topCategoria / exposiciones));
        }
        return porFuente;
    }
}
